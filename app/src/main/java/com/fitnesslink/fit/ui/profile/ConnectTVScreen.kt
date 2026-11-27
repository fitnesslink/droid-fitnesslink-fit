package com.fitnesslink.fit.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.network.api.ConfirmPairingRequest
import com.fitnesslink.fit.ui.components.ActionButtonView
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.FLTheme
import kotlinx.coroutines.launch

/**
 * The pairing-code format the TV generates: 6 characters from an alphabet with the look-alikes
 * (0/O, 1/I) removed. Port of iOS `TVPairingCode`.
 */
object TvPairingCode {
    const val LENGTH = 6
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    data class Sanitized(val code: String, val rejected: Boolean)

    /** Uppercases, drops spaces and dashes, and discards anything outside the code alphabet. */
    fun sanitize(raw: String): Sanitized {
        val code = StringBuilder()
        var rejected = false
        for (ch in raw.uppercase()) {
            if (ch == '-' || ch.isWhitespace()) continue
            if (ch in ALPHABET) {
                if (code.length < LENGTH) code.append(ch)
            } else {
                rejected = true
            }
        }
        return Sanitized(code.toString(), rejected)
    }

    fun isValid(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }

    /** The message for a failed confirm. Port of iOS `TVPairingError`. */
    fun errorMessage(error: Throwable): String = when {
        error is ApiError.NotFound -> "We couldn't find that code. Check the code on your TV and try again."
        error is ApiError.ServerError && error.statusCode == 410 -> "That code has expired. Get a new code on your TV and enter it here."
        error is ApiError.ServerError && error.statusCode == 400 -> "Enter the 6-character code shown on your TV."
        error is ApiError.Unauthorized -> "Your session has expired. Sign in again, then retry."
        error is ApiError.Offline -> "No internet connection. Connect and try again."
        else -> "Something went wrong connecting your TV. Please try again."
    }
}

/**
 * Pairs a TV with this account. The TV shows a 6-character code, or a QR code that deep-links here
 * with it filled in, and the user confirms. Connecting always takes an explicit tap, so a stray
 * code can never link someone else's TV silently.
 */
@Composable
fun ConnectTVScreen(prefilledCode: String?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf(prefilledCode?.let { TvPairingCode.sanitize(it).code }.orEmpty()) }
    var showAlphabetHint by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }

    val connect: () -> Unit = {
        if (!TvPairingCode.isValid(code)) {
            error = "Enter the 6-character code shown on your TV."
        } else if (!isConnecting) {
            isConnecting = true
            error = null
            scope.launch {
                try {
                    val response = ApiClient.tvPairingApi.confirm(ConfirmPairingRequest(code))
                    if (response.success == false) {
                        error = "Something went wrong connecting your TV. Please try again."
                    } else {
                        isConnected = true
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = TvPairingCode.errorMessage(e)
                } finally {
                    isConnecting = false
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(FLTheme.colors.background)) {
        HeaderBackView(onBack = onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (isConnected) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(56.dp))
                Text("Your TV is connected", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
                Text(
                    "It's signed in to your FitnessLink account. You can pick up your workouts on the big screen.",
                    fontSize = 14.sp,
                    color = FLTheme.colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                ActionButtonView(text = "Done", isEnabled = true, isLoading = false, onClick = onBack)
                Text(
                    "Connect Another TV",
                    fontSize = 14.sp,
                    color = FLPrimary,
                    modifier = Modifier
                        .clickable {
                            code = ""
                            isConnected = false
                        }
                        .padding(8.dp)
                )
            } else {
                Icon(Icons.Filled.Tv, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(44.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the code on your TV", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FLTheme.colors.textPrimary)
                    Text(
                        "Open FitnessLink on your TV. Enter the 6-character code it shows, or scan its QR code with your camera.",
                        fontSize = 14.sp,
                        color = FLTheme.colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
                CodeEntry(
                    code = code,
                    hasError = error != null,
                    enabled = !isConnecting,
                    takeFocus = code.isEmpty(),
                    onChange = {
                        val result = TvPairingCode.sanitize(it)
                        code = result.code
                        showAlphabetHint = result.rejected
                        error = null
                    },
                    onDone = connect
                )
                when {
                    error != null -> Text(error.orEmpty(), fontSize = 14.sp, color = FLTheme.colors.danger, textAlign = TextAlign.Center)
                    showAlphabetHint -> Text(
                        "Codes use letters and the numbers 2–9 only. There's no O, 0, I or 1.",
                        fontSize = 13.sp,
                        color = FLTheme.colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
                ActionButtonView(
                    text = "Connect",
                    isEnabled = TvPairingCode.isValid(code),
                    isLoading = isConnecting,
                    onClick = connect
                )
                Text(
                    "Only connect a TV you can see in front of you. It will be signed in to your FitnessLink account.",
                    fontSize = 12.sp,
                    color = FLTheme.colors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CodeEntry(
    code: String,
    hasError: Boolean,
    enabled: Boolean,
    takeFocus: Boolean,
    onChange: (String) -> Unit,
    onDone: () -> Unit
) {
    val focus = remember { FocusRequester() }
    // Filled in from a QR scan: leave the keyboard down so Connect is the obvious next step.
    LaunchedEffect(Unit) { if (takeFocus) runCatching { focus.requestFocus() } }
    BasicTextField(
        value = code,
        onValueChange = onChange,
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Go
        ),
        keyboardActions = KeyboardActions(onGo = { onDone() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        decorationBox = { inner ->
            Box {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(TvPairingCode.LENGTH) { index ->
                        val current = index == minOf(code.length, TvPairingCode.LENGTH - 1)
                        Box(
                            Modifier
                                .weight(1f)
                                .height(62.dp)
                                .background(FLTheme.colors.surface, RoundedCornerShape(10.dp))
                                .border(
                                    if (current || hasError) 2.dp else 1.dp,
                                    when {
                                        hasError -> FLTheme.colors.danger
                                        current -> FLPrimary
                                        else -> FLTheme.colors.textSecondary.copy(alpha = 0.25f)
                                    },
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                code.getOrNull(index)?.toString().orEmpty(),
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = FLTheme.colors.textPrimary
                            )
                        }
                    }
                }
                Box(Modifier.matchParentSize()) { inner() }
            }
        }
    )
}
