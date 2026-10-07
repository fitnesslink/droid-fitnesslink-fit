package com.fitnesslink.fit.ui.nutrition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.model.MealType
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.dto.IdentifiedFood
import com.fitnesslink.fit.network.dto.IdentifyMealPhotoRequest
import com.fitnesslink.fit.network.dto.IdentifyMealPhotoResponse
import com.fitnesslink.fit.nutrition.AIEstimateErrorCopy
import com.fitnesslink.fit.nutrition.SnapMealPrefill
import com.fitnesslink.fit.nutrition.SnapMealPrefillHandoff
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.components.SeparatorView
import com.fitnesslink.fit.ui.navigation.FoodLoggingRoutes
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.DisabledButton
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.MediumGray
import com.fitnesslink.fit.ui.theme.OrangeTheme
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** One identified food the user can rename, re-portion or delete. */
data class ConfirmLine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val portion: String,
    val confidence: String
) {
    val isValid: Boolean get() = name.isNotBlank()

    /** "Grilled chicken breast (about 6 oz)" — what the estimate reads. */
    val describe: String
        get() {
            val n = name.trim()
            val p = portion.trim()
            return if (p.isEmpty()) n else "$n ($p)"
        }

    companion object {
        fun from(food: IdentifiedFood) = ConfirmLine(name = food.name, portion = food.portionDescription, confidence = food.confidence)
        fun added(name: String, portion: String) = ConfirmLine(name = name, portion = portion, confidence = "added")
    }
}

/** FR-22: what the camera cannot see, one tap each. */
private val hiddenIngredients = listOf(
    "Cooking oil" to "1 tbsp", "Butter" to "1 tbsp", "Salad dressing" to "2 tbsp",
    "Sauce" to "2 tbsp", "Cheese" to "1 oz", "Drink" to "1 glass"
)

/**
 * FTNSSLNK-492/493/494 "Snap a Meal" (behind `NutritionFeatureFlags.snapMealEnabled`):
 * take or pick a photo → `POST /foods/identify-meal-photo` names what is on
 * the plate with portion guesses → the user confirms the list → the confirmed
 * lines become the description for Describe a Meal, which logs with
 * `source = ai_photo`. The photo never leaves this screen's memory: it is
 * downscaled, sent once, shown as a thumbnail, and released with the screen.
 * Nothing is written to the photo library (the camera returns a preview bitmap).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SnapMealScreen(
    mealType: MealType,
    date: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedMeal by remember { mutableStateOf(mealType) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var jpegBytes by remember { mutableStateOf<ByteArray?>(null) }
    var hint by remember { mutableStateOf("") }
    var isPreparing by remember { mutableStateOf(false) }
    var isIdentifying by remember { mutableStateOf(false) }
    var identifyError by remember { mutableStateOf<String?>(null) }
    var lines by remember { mutableStateOf<List<ConfirmLine>>(emptyList()) }
    var generationId by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf<String?>(null) }

    val hasResult = lines.isNotEmpty()
    val canIdentify = jpegBytes != null && !isIdentifying && !isPreparing
    val validLines = lines.filter { it.isValid }
    val cameraAvailable = remember { context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY) }

    /** Downscale + re-encode off the main thread, then keep only the rendered JPEG (and a bitmap of it for display). */
    fun accept(picked: Bitmap) {
        identifyError = null
        isPreparing = true
        scope.launch {
            val data = withContext(Dispatchers.Default) { MealPhotoPrep.jpegBytes(picked) }
            isPreparing = false
            if (data == null) { identifyError = "Couldn't prepare that photo. Try another one."; return@launch }
            jpegBytes = data
            bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { captured ->
        if (captured != null) accept(captured)
    }
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isPreparing = true
        scope.launch {
            val decoded = withContext(Dispatchers.IO) { MealPhotoPrep.decode(context, uri) }
            isPreparing = false
            if (decoded == null) identifyError = "Couldn't read that photo. Try another one." else accept(decoded)
        }
    }

    fun identify() {
        val data = jpegBytes ?: return
        if (!canIdentify) return
        isIdentifying = true
        identifyError = null
        scope.launch {
            try {
                val response = ApiClient.nutritionAiApi.identifyMealPhoto(
                    IdentifyMealPhotoRequest(
                        imageBase64 = Base64.encodeToString(data, Base64.NO_WRAP),
                        mealType = selectedMeal.wireValue,
                        hint = hint.trim().takeIf { it.isNotEmpty() }
                    )
                )
                val items = response.items
                if (response.success && !items.isNullOrEmpty()) {
                    lines = items.map { ConfirmLine.from(it) }
                    generationId = response.generationId
                    notes = response.notes
                } else {
                    identifyError = AIEstimateErrorCopy.message(response.errorReasonCode, response.errorDetail)
                }
            } catch (e: ApiError.ServerError) {
                val envelope = e.decodeBody(ApiClient.gson, IdentifyMealPhotoResponse::class.java)
                identifyError = AIEstimateErrorCopy.message(envelope?.errorReasonCode ?: e.errorReasonCode, envelope?.errorDetail ?: e.errorDetail)
            } catch (_: ApiError.Offline) {
                identifyError = "You're offline — Snap a Meal needs a connection."
            } catch (_: Exception) {
                identifyError = "Couldn't reach the estimate service. Try again."
            } finally {
                isIdentifying = false
            }
        }
    }

    /** FR-43: a new photo is a new identify call; nothing links back. */
    fun retake() {
        lines = emptyList(); generationId = null; notes = null; bitmap = null; jpegBytes = null; identifyError = null
    }

    fun estimate() {
        val gen = generationId ?: return
        if (validLines.isEmpty()) return
        val token = SnapMealPrefillHandoff.put(SnapMealPrefill(description = validLines.joinToString("; ") { it.describe }, sourceGenerationId = gen))
        onNavigate(FoodLoggingRoutes.describeMeal(selectedMeal, date, prefillToken = token))
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
        HeaderBackView(onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Snap a Meal", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimaryColor)
                Text(
                    if (hasResult) "Check what we found. Fix names and portions, and add anything the camera couldn't see."
                    else "Take a photo of your plate — AI names what's on it, you confirm, then it estimates the macros.",
                    fontSize = 13.sp, color = TextSecondaryColor
                )
            }

            if (!hasResult) {
                // Capture card
                FoodLogCard {
                    val shown = bitmap
                    if (shown != null) {
                        Image(
                            bitmap = shown.asImageBitmap(), contentDescription = "Your meal photo", contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp).background(BackgroundColor, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.PhotoCamera, null, tint = MediumGray, modifier = Modifier.size(40.dp))
                                Text(if (cameraAvailable) "Take a photo or pick one from your library" else "Pick a photo from your library", fontSize = 13.sp, color = TextSecondaryColor)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (cameraAvailable) {
                            Row(
                                modifier = Modifier.weight(1f).background(FLPrimary, RoundedCornerShape(8.dp)).clickable { cameraLauncher.launch(null) }.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PhotoCamera, null, tint = White, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                                Text(if (bitmap == null) "Camera" else "Retake", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = White)
                            }
                        }
                        Row(
                            modifier = Modifier.weight(1f).background(FLPrimary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .clickable { pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null, tint = FLPrimary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                            Text("Library", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth().background(BackgroundColor, RoundedCornerShape(10.dp)).padding(12.dp)) {
                        BasicTextField(
                            value = hint, onValueChange = { hint = it.take(200) }, maxLines = 2,
                            textStyle = TextStyle(fontSize = 15.sp, color = TextPrimaryColor), modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner -> if (hint.isEmpty()) Text("Anything the photo can't show? e.g. cooked in butter", fontSize = 15.sp, color = MediumGray); inner() }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().background(if (canIdentify) FLPrimary else DisabledButton, RoundedCornerShape(10.dp))
                            .then(if (canIdentify) Modifier.clickable { identify() } else Modifier).padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isIdentifying || isPreparing) CircularProgressIndicator(color = White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.AutoAwesome, null, tint = White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isPreparing) "Preparing photo…" else if (isIdentifying) "Looking at your plate…" else "Identify the foods",
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = White
                        )
                    }
                    identifyError?.let { error ->
                        Spacer(Modifier.height(8.dp))
                        FoodLogErrorText(error)
                        // FR-14/16: every refusal offers the typed flow.
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Describe the meal instead", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary,
                            modifier = Modifier.clickable { onNavigate(FoodLoggingRoutes.describeMeal(selectedMeal, date)) }
                        )
                    }
                }
            } else {
                // Confirm card
                FoodLogCard(padding = 0) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        bitmap?.let {
                            Image(bitmap = it.asImageBitmap(), contentDescription = "Your meal photo", contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.AutoAwesome, null, tint = OrangeTheme, modifier = Modifier.size(14.dp))
                                Text("What we found — check before estimating", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OrangeTheme)
                            }
                            notes?.let { Text(it, fontSize = 12.sp, color = TextSecondaryColor) }
                        }
                    }
                    lines.forEach { line ->
                        SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                        LineEditor(line, onChange = { updated -> lines = lines.map { if (it.id == updated.id) updated else it } }, onRemove = { lines = lines.filter { it.id != line.id } })
                    }
                    SeparatorView(modifier = Modifier.padding(horizontal = 16.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clickable { lines = lines + ConfirmLine.added("", "") }) {
                            Icon(Icons.Default.AddCircle, null, tint = FLPrimary, modifier = Modifier.size(16.dp))
                            Text("Add item", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                        }
                        Spacer(Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clickable { retake() }) {
                            Icon(Icons.Default.PhotoCamera, null, tint = FLPrimary, modifier = Modifier.size(16.dp))
                            Text("Retake", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
                        }
                    }
                }

                FoodLogCard {
                    Text("Anything the camera couldn't see?", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                    Text("Cooking fat, dressing and sauces add up. Tap to add, then adjust the portion.", fontSize = 12.sp, color = TextSecondaryColor)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        hiddenIngredients.forEach { (name, portion) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.background(FLPrimary.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                                    .clickable { lines = lines + ConfirmLine.added(name, portion) }.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Add, null, tint = FLPrimary, modifier = Modifier.size(10.dp))
                                Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FLPrimary)
                            }
                        }
                    }
                }

                FoodLogCard {
                    Text("Meal", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MealType.entries.forEach { meal -> AIChipLabel(meal.displayName, isSelected = selectedMeal == meal, modifier = Modifier.clickable { selectedMeal = meal }) }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FoodLogPrimaryButton(text = "Estimate macros", enabled = generationId != null && validLines.isNotEmpty(), onClick = ::estimate)
                    Text(
                        "${validLines.size} item${if (validLines.size == 1) "" else "s"} → macros you can still edit before logging",
                        fontSize = 12.sp, color = TextSecondaryColor, modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LineEditor(line: ConfirmLine, onChange: (ConfirmLine) -> Unit, onRemove: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicTextField(
                value = line.name, onValueChange = { onChange(line.copy(name = it)) }, singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor),
                decorationBox = { inner -> if (line.name.isEmpty()) Text("Food", fontSize = 15.sp, color = MediumGray); inner() }
            )
            BasicTextField(
                value = line.portion, onValueChange = { onChange(line.copy(portion = it)) }, singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = TextSecondaryColor),
                decorationBox = { inner -> if (line.portion.isEmpty()) Text("Portion, e.g. 1 cup", fontSize = 13.sp, color = MediumGray); inner() }
            )
        }
        // FR-21: low confidence is called out, not just labelled.
        val isLow = line.confidence == "low"
        val color = if (isLow) OrangeTheme else TextSecondaryColor
        Text(
            when { line.confidence == "added" -> "added"; isLow -> "check this"; else -> line.confidence },
            fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color,
            modifier = Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
        )
        Icon(Icons.Default.Cancel, "Remove ${line.name}", tint = TextSecondaryColor, modifier = Modifier.size(18.dp).clickable(onClick = onRemove))
    }
}

/** FR-4: long edge ≤ 1536 px, JPEG, target under 1 MB. Re-encoding drops every EXIF block. */
object MealPhotoPrep {
    const val MAX_LONG_EDGE = 1536
    const val TARGET_BYTES = 1_000_000

    fun jpegBytes(image: Bitmap): ByteArray? {
        if (image.width <= 0 || image.height <= 0) return null
        val scale = min(1f, MAX_LONG_EDGE.toFloat() / max(image.width, image.height))
        val target = if (scale < 1f) Bitmap.createScaledBitmap(image, (image.width * scale).toInt(), (image.height * scale).toInt(), true) else image
        var quality = 80
        var data = encode(target, quality)
        while (data.size > TARGET_BYTES && quality > 45) {
            quality -= 10
            data = encode(target, quality)
        }
        return data
    }

    private fun encode(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    /** Decode a picked image, downsampled near the long-edge cap so a 12 MP shot never fully inflates. */
    fun decode(context: Context, uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val longEdge = max(info.size.width, info.size.height)
                if (longEdge > MAX_LONG_EDGE) {
                    val scale = MAX_LONG_EDGE.toFloat() / longEdge
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
    } catch (_: Exception) {
        null
    }
}
