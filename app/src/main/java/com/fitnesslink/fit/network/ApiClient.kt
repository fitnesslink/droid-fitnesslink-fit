package com.fitnesslink.fit.network

import com.fitnesslink.fit.network.api.*
import com.fitnesslink.fit.network.dto.PreferredWaterUnitRequest
import com.fitnesslink.fit.network.dto.SessionCompleteRequest
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import okio.Timeout
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Retrofit/OkHttp stack shared by every API interface. Mirrors iOS
 * `APIClient`:
 *
 * - Firebase bearer token on every request; a 401 force-refreshes the token
 *   and retries exactly once ([TokenAuthenticator]).
 * - 402 fires [onPaymentRequired] (the entitlement store re-resolves and
 *   routes to the paywall) before the call throws [ApiError.PaymentRequired].
 * - Every call throws a typed [ApiError] instead of `HttpException` /
 *   `IOException` ([ApiErrorCallAdapterFactory]).
 * - A second client with a 180 s read timeout backs the long-running AI
 *   endpoints ([longRunningRetrofit]).
 */
object ApiClient {
    /** Which client started a session / completed a workout (`Session.Platform`). */
    const val PLATFORM = "android"

    /**
     * Supplies the bearer token. `forceRefresh` bypasses Firebase's cached
     * token so a 401 caused by an expired-but-cached token can recover.
     * Replaceable for tests; defaults to the signed-in Firebase user.
     */
    fun interface TokenProvider {
        fun token(forceRefresh: Boolean): String?
    }

    @Volatile
    var tokenProvider: TokenProvider = TokenProvider { forceRefresh ->
        val user = FirebaseAuth.getInstance().currentUser ?: return@TokenProvider null
        try {
            Tasks.await(user.getIdToken(forceRefresh), 20, TimeUnit.SECONDS).token
        } catch (_: Exception) {
            null
        }
    }

    /**
     * FTNSSLNK-286: invoked (on an OkHttp thread) whenever the server answers
     * 402. The billing layer sets this to re-resolve the entitlement; the
     * request itself still fails with [ApiError.PaymentRequired].
     */
    @Volatile
    var onPaymentRequired: (() -> Unit)? = null

    // MARK: - Gson

    val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Date::class.java, LenientDateAdapter())
        .registerTypeAdapterFactory(LenientEnumAdapterFactory())
        // iOS forces these keys onto the wire even when null (see the DTOs);
        // Gson omits nulls by default, so serialize them by hand.
        .registerTypeAdapter(SessionCompleteRequest::class.java, JsonSerializer<SessionCompleteRequest> { src, _, _ ->
            JsonObject().apply {
                add("rpeId", src.rpeId?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
                src.rpeValue?.let { addProperty("rpeValue", it) }
                src.completionPercent?.let { addProperty("completionPercent", it) }
                src.platform?.let { addProperty("platform", it) }
            }
        })
        .registerTypeAdapter(PreferredWaterUnitRequest::class.java, JsonSerializer<PreferredWaterUnitRequest> { src, _, _ ->
            JsonObject().apply {
                add("unit", src.unit?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE)
            }
        })
        .create()

    // MARK: - OkHttp

    private const val RETRY_HEADER = "X-FL-Auth-Retry"

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder().header("Accept", "application/json")
        if (original.header("Authorization") == null) {
            tokenProvider.token(false)?.let { builder.header("Authorization", "Bearer $it") }
        }
        chain.proceed(builder.build())
    }

    /**
     * 401 → force a Firebase refresh and retry once. OkHttp only consults the
     * authenticator for 401s, and the marker header stops a second pass when
     * the retried request is also rejected.
     */
    private val tokenAuthenticator = object : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            if (response.request.header(RETRY_HEADER) != null) return null
            if (response.priorResponse?.code == 401) return null
            val fresh = tokenProvider.token(true) ?: return null
            return response.request.newBuilder()
                .header("Authorization", "Bearer $fresh")
                .header(RETRY_HEADER, "1")
                .build()
        }
    }

    private val paymentRequiredInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        if (response.code == 402) {
            try { onPaymentRequired?.invoke() } catch (_: Exception) { }
        }
        response
    }

    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = if (ApiConfiguration.isDebugBuild) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.BASIC
            redactHeader("Authorization")
        }
    }

    private fun baseClient(): OkHttpClient.Builder = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(paymentRequiredInterceptor)
        .addInterceptor(loggingInterceptor)
        .authenticator(tokenAuthenticator)
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)

    /** Default client — 30 s like iOS's `URLSessionConfiguration`. */
    val okHttpClient: OkHttpClient by lazy {
        baseClient().readTimeout(30, TimeUnit.SECONDS).build()
    }

    /**
     * Long-running client for the AI generators (whole-week meal plans,
     * workout drafts): a single structured response that can take well over
     * 30 s. Kept separate so nothing else can hang on it.
     */
    val longRunningOkHttpClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(240, TimeUnit.SECONDS)
            .build()
    }

    // MARK: - Retrofit

    private fun buildRetrofit(service: ApiService, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(ApiConfiguration.baseUrl(service))
        .client(client)
        .addCallAdapterFactory(ApiErrorCallAdapterFactory())
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    fun retrofit(service: ApiService): Retrofit = buildRetrofit(service, okHttpClient)

    fun longRunningRetrofit(service: ApiService): Retrofit = buildRetrofit(service, longRunningOkHttpClient)

    private inline fun <reified T> core(): T = retrofit(ApiService.CORE).create(T::class.java)
    private inline fun <reified T> nutrition(): T = retrofit(ApiService.NUTRITION).create(T::class.java)

    // Core API services
    val userApi: UserApi by lazy { core() }
    val workoutApi: WorkoutApi by lazy { core() }
    val workoutPhaseApi: WorkoutPhaseApi by lazy { core() }
    val aiWorkoutApi: AiWorkoutApi by lazy { longRunningRetrofit(ApiService.CORE).create(AiWorkoutApi::class.java) }
    val programApi: ProgramApi by lazy { core() }
    val movementApi: MovementApi by lazy { core() }
    val sessionApi: SessionApi by lazy { core() }
    val goalApi: GoalApi by lazy { core() }
    val bodyTrackingApi: BodyTrackingApi by lazy { core() }
    val calendarApi: CalendarApi by lazy { core() }
    val personalizationApi: PersonalizationApi by lazy { core() }
    val classificationApi: ClassificationApi by lazy { core() }
    val contributorApi: ContributorApi by lazy { core() }
    val reportApi: ReportApi by lazy { core() }
    val contentApi: ContentApi by lazy { core() }
    val syncApi: SyncApi by lazy { core() }
    val mediaApi: MediaApi by lazy { core() }

    // Nutrition API services
    val nutritionApi: NutritionApi by lazy { nutrition() }
    /** AI nutrition endpoints on the 180 s client (estimate / parse / identify / suggest / week). */
    val nutritionAiApi: NutritionAiApi by lazy { longRunningRetrofit(ApiService.NUTRITION).create(NutritionAiApi::class.java) }

    // Notification API services
    val notificationApi: NotificationServiceApi by lazy { retrofit(ApiService.NOTIFICATION).create(NotificationServiceApi::class.java) }

    // Billing API services
    val billingApi: BillingApi by lazy { retrofit(ApiService.BILLING).create(BillingApi::class.java) }
}

// MARK: - Call adapter: HttpException / IOException → ApiError

/**
 * Wraps every `Call` so a non-2xx response or a transport failure reaches
 * the suspend caller as an [ApiError] (iOS `validate`), not as Retrofit's
 * `HttpException`.
 */
private class ApiErrorCallAdapterFactory : CallAdapter.Factory() {
    override fun get(returnType: Type, annotations: Array<out Annotation>, retrofit: Retrofit): CallAdapter<*, *>? {
        if (getRawType(returnType) != Call::class.java) return null
        val responseType = getParameterUpperBound(0, returnType as ParameterizedType)
        return object : CallAdapter<Any?, Call<Any?>> {
            override fun responseType(): Type = responseType
            override fun adapt(call: Call<Any?>): Call<Any?> = ApiErrorCall(call)
        }
    }
}

private class ApiErrorCall<T>(private val delegate: Call<T>) : Call<T> {
    override fun enqueue(callback: Callback<T>) {
        delegate.enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>, response: retrofit2.Response<T>) {
                if (response.isSuccessful) {
                    callback.onResponse(this@ApiErrorCall, response)
                } else {
                    val body = runCatching { response.errorBody()?.string() }.getOrNull()
                    callback.onFailure(this@ApiErrorCall, ApiError.fromResponse(response.code(), body))
                }
            }

            override fun onFailure(call: Call<T>, t: Throwable) {
                callback.onFailure(this@ApiErrorCall, ApiError.from(t))
            }
        })
    }

    override fun execute(): retrofit2.Response<T> = try {
        delegate.execute()
    } catch (t: Throwable) {
        throw ApiError.from(t)
    }

    override fun clone(): Call<T> = ApiErrorCall(delegate.clone())
    override fun isExecuted(): Boolean = delegate.isExecuted
    override fun cancel() = delegate.cancel()
    override fun isCanceled(): Boolean = delegate.isCanceled
    override fun request(): Request = delegate.request()
    override fun timeout(): Timeout = delegate.timeout()
}

// MARK: - Gson adapters

/**
 * Parses every timestamp shape the platform emits (fractional seconds of
 * any precision, with or without an offset) and writes ISO-8601 UTC. The
 * previous single-pattern formatter failed on `.1234567Z`, which made whole
 * arrays decode to null.
 */
private class LenientDateAdapter : TypeAdapter<Date>() {
    private val utc = TimeZone.getTimeZone("UTC")

    private val writer = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = utc }

    private val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd"
    )

    private val parsers = ThreadLocal.withInitial {
        patterns.map { SimpleDateFormat(it, Locale.US).apply { timeZone = utc; isLenient = false } }
    }

    override fun write(out: JsonWriter, value: Date?) {
        if (value == null) out.nullValue() else out.value(synchronized(writer) { writer.format(value) })
    }

    override fun read(reader: JsonReader): Date? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        if (reader.peek() == JsonToken.NUMBER) {
            return Date(reader.nextLong())
        }
        val raw = reader.nextString()
        return parse(raw) ?: throw JsonParseException("Cannot parse date: $raw")
    }

    private fun parse(raw: String): Date? {
        // SimpleDateFormat treats "S" as milliseconds, so a 7-digit fraction
        // would be read as 1234567 ms. Trim the fraction to 3 digits first.
        val normalized = normalizeFraction(raw)
        for (f in parsers.get()!!) {
            val pos = ParsePosition(0)
            val d = f.parse(normalized, pos)
            if (d != null && pos.index == normalized.length) return d
        }
        return null
    }

    private fun normalizeFraction(s: String): String {
        val dot = s.indexOf('.')
        if (dot < 0) return s
        var end = dot + 1
        while (end < s.length && s[end].isDigit()) end++
        val fraction = s.substring(dot + 1, end)
        if (fraction.length <= 3) return s
        return s.substring(0, dot + 1) + fraction.substring(0, 3) + s.substring(end)
    }
}

/**
 * Enum (de)serialization that tolerates the platform's PascalCase names
 * against the app's SCREAMING_CASE constants, and the display-string enums
 * (`WaterUnit.OZ("fl oz")`, `MealType.BREAKFAST("Breakfast")`):
 *
 * read:  `@SerializedName` → constant name (case-insensitive) →
 *        `rawValue` / `displayName` / `label` property (case-insensitive) → null
 * write: `@SerializedName` → `rawValue` property → constant name
 *
 * Unknown values decode to null instead of failing the whole payload.
 */
private class LenientEnumAdapterFactory : TypeAdapterFactory {
    override fun <T : Any?> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        var raw: Class<*> = type.rawType
        if (!Enum::class.java.isAssignableFrom(raw) || raw == Enum::class.java) return null
        // A constant with a body is an anonymous subclass; adapt its enum.
        if (!raw.isEnum) raw = raw.superclass
        @Suppress("UNCHECKED_CAST")
        return EnumAdapter(raw as Class<out Enum<*>>) as TypeAdapter<T>
    }

    private class EnumAdapter<E : Enum<*>>(private val enumClass: Class<E>) : TypeAdapter<E>() {
        private val constants: Array<out E> = enumClass.enumConstants ?: emptyArray<Enum<*>>() as Array<out E>
        private val serializedNames: Map<E, String> = constants.mapNotNull { c ->
            enumClass.getField(c.name).getAnnotation(SerializedName::class.java)?.let { c to it.value }
        }.toMap()
        private val rawValues: Map<E, String> = readProperty("rawValue")
        private val aliases: Map<String, E> = buildMap {
            fun put(key: String, value: E) { putIfAbsent(key.lowercase(Locale.ROOT), value) }
            serializedNames.forEach { (c, n) -> put(n, c) }
            constants.forEach { put(it.name, it) }
            rawValues.forEach { (c, v) -> put(v, c) }
            readProperty("displayName").forEach { (c, v) -> put(v, c) }
            readProperty("label").forEach { (c, v) -> put(v, c) }
        }

        private fun readProperty(name: String): Map<E, String> {
            val field = try { enumClass.getDeclaredField(name) } catch (_: NoSuchFieldException) { return emptyMap() }
            if (field.type != String::class.java) return emptyMap()
            field.isAccessible = true
            return constants.mapNotNull { c -> (field.get(c) as? String)?.let { c to it } }.toMap()
        }

        override fun write(out: JsonWriter, value: E?) {
            if (value == null) { out.nullValue(); return }
            out.value(serializedNames[value] ?: rawValues[value] ?: value.name)
        }

        override fun read(reader: JsonReader): E? {
            if (reader.peek() == JsonToken.NULL) { reader.nextNull(); return null }
            if (reader.peek() == JsonToken.NUMBER) {
                val ordinal = reader.nextInt()
                return constants.getOrNull(ordinal)
            }
            return aliases[reader.nextString().trim().lowercase(Locale.ROOT)]
        }
    }
}
