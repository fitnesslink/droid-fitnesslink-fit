package com.fitnesslink.fit.network

import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.network.api.*
import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private val gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .create()

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val token = runBlocking {
            try { AuthManager.getIdToken() } catch (_: Exception) { null }
        }
        val request = if (token != null) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .build()
        } else {
            original.newBuilder()
                .header("Accept", "application/json")
                .build()
        }
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun retrofit(service: ApiService): Retrofit = Retrofit.Builder()
        .baseUrl(ApiConfiguration.baseUrl(service))
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    // Core API services
    val userApi: UserApi by lazy { retrofit(ApiService.CORE).create(UserApi::class.java) }
    val workoutApi: WorkoutApi by lazy { retrofit(ApiService.CORE).create(WorkoutApi::class.java) }
    val programApi: ProgramApi by lazy { retrofit(ApiService.CORE).create(ProgramApi::class.java) }
    val movementApi: MovementApi by lazy { retrofit(ApiService.CORE).create(MovementApi::class.java) }
    val sessionApi: SessionApi by lazy { retrofit(ApiService.CORE).create(SessionApi::class.java) }
    val goalApi: GoalApi by lazy { retrofit(ApiService.CORE).create(GoalApi::class.java) }
    val bodyTrackingApi: BodyTrackingApi by lazy { retrofit(ApiService.CORE).create(BodyTrackingApi::class.java) }
    val calendarApi: CalendarApi by lazy { retrofit(ApiService.CORE).create(CalendarApi::class.java) }
    val personalizationApi: PersonalizationApi by lazy { retrofit(ApiService.CORE).create(PersonalizationApi::class.java) }
    val classificationApi: ClassificationApi by lazy { retrofit(ApiService.CORE).create(ClassificationApi::class.java) }
    val reportApi: ReportApi by lazy { retrofit(ApiService.CORE).create(ReportApi::class.java) }
    val contentApi: ContentApi by lazy { retrofit(ApiService.CORE).create(ContentApi::class.java) }
    val syncApi: SyncApi by lazy { retrofit(ApiService.CORE).create(SyncApi::class.java) }
    val mediaApi: MediaApi by lazy { retrofit(ApiService.CORE).create(MediaApi::class.java) }

    // Nutrition API services
    val nutritionApi: NutritionApi by lazy { retrofit(ApiService.NUTRITION).create(NutritionApi::class.java) }

    // Notification API services
    val notificationApi: NotificationServiceApi by lazy { retrofit(ApiService.NOTIFICATION).create(NotificationServiceApi::class.java) }
}
