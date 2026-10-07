package com.fitnesslink.fit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.api.ShareableContentKind
import com.fitnesslink.fit.model.api.UserSummary
import com.fitnesslink.fit.network.ApiClient
import com.fitnesslink.fit.network.dto.ContentShareRequest
import com.fitnesslink.fit.ui.theme.BackgroundColor
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.HighlightState
import com.fitnesslink.fit.ui.theme.TextPrimaryColor
import com.fitnesslink.fit.ui.theme.TextSecondaryColor
import com.fitnesslink.fit.ui.theme.White
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.fitnesslink.fit.network.dto.ShareableContentKind as WireKind

/**
 * Bottom sheet for sharing a piece of content with another user (iOS
 * `ShareSheet`). Live debounced search against `GET /users/search`; tapping a
 * result sends `POST /content/share`. On success a toast confirms the
 * recipient still has to accept, then the sheet dismisses itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(contentId: String, kind: ShareableContentKind, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UserSummary>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var sendingTo by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var sentToName by remember { mutableStateOf<String?>(null) }
    // Bumped by "Try again" so the same query re-runs.
    var searchAttempt by remember { mutableIntStateOf(0) }

    val term = query.trim()

    // Debounce: 300 ms, cancelled by any keystroke (LaunchedEffect restarts on key change).
    LaunchedEffect(term, searchAttempt) {
        errorMessage = null
        if (term.length < 2) {
            results = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        delay(300)
        isSearching = true
        try {
            results = ApiClient.userApi.searchUsers(q = term, limit = 20)
        } catch (e: Exception) {
            errorMessage = "Couldn't search: ${e.message ?: "unknown error"}"
        } finally {
            isSearching = false
        }
    }

    fun send(user: UserSummary) {
        val userId = user.id.toString()
        if (sendingTo != null) return
        sendingTo = userId
        scope.launch {
            try {
                ApiClient.contentApi.share(
                    ContentShareRequest(
                        contentId = contentId,
                        kind = WireKind.valueOf(kind.name),
                        sharedWithUserId = userId
                    )
                )
                sentToName = user.displayName
                // Hold the toast briefly so the user sees confirmation, then close.
                delay(900)
                onDismiss()
            } catch (e: Exception) {
                errorMessage = "Couldn't share: ${e.message ?: "unknown error"}"
            } finally {
                sendingTo = null
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = White
    ) {
        Box(modifier = Modifier.fillMaxHeight(0.85f)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Title bar: "Share <kind>" + Done.
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        text = "Share ${kind.displayName}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryColor,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) {
                        Text("Done", color = FLPrimary, fontWeight = FontWeight.Medium)
                    }
                }

                ShareSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    onClear = { query = ""; results = emptyList() },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                when {
                    errorMessage != null -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(errorMessage ?: "", fontSize = 13.sp, color = Color.Red)
                            TextButton(onClick = { searchAttempt++ }) { Text("Try again", color = FLPrimary) }
                        }
                    }
                    term.length < 2 -> ShareEmptyState("Type at least 2 characters to search")
                    isSearching && results.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = FLPrimary)
                        }
                    }
                    results.isEmpty() -> ShareEmptyState("No users matched \"$query\"")
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(results, key = { it.id.toString() }) { user ->
                                val userId = user.id.toString()
                                ShareUserRow(
                                    user = user,
                                    isSending = sendingTo == userId,
                                    enabled = sendingTo == null,
                                    onClick = { send(user) }
                                )
                                HorizontalDivider(color = BackgroundColor)
                            }
                        }
                    }
                }
            }

            // Success toast — the recipient still has to accept before it enters their library.
            sentToName?.let { name ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.85f))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Sent to $name — they'll be asked to accept it",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = White
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BackgroundColor)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(18.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(fontSize = 15.sp, color = TextPrimaryColor),
            cursorBrush = SolidColor(FLPrimary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("Search by name or username", fontSize = 15.sp, color = TextSecondaryColor, maxLines = 1)
                    }
                    inner()
                }
            }
        )
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear search",
                tint = TextSecondaryColor,
                modifier = Modifier.size(18.dp).clickable(onClick = onClear)
            )
        }
    }
}

@Composable
private fun ShareEmptyState(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, color = TextSecondaryColor)
    }
}

@Composable
private fun ShareUserRow(user: UserSummary, isSending: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ShareAvatar(user)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(user.displayName, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimaryColor, maxLines = 1)
            if (user.username.isNotEmpty()) {
                Text("@${user.username}", fontSize = 12.sp, color = TextSecondaryColor, maxLines = 1)
            }
        }
        if (isSending) {
            CircularProgressIndicator(color = FLPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Share", tint = FLPrimary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ShareAvatar(user: UserSummary) {
    val imageId = user.profileImageId
    if (imageId != null) {
        FLImageView(
            ref = MediaRef.UserPhoto(imageId.toString()),
            modifier = Modifier.size(36.dp).clip(CircleShape)
        )
    } else {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(HighlightState),
            contentAlignment = Alignment.Center
        ) {
            Text(initials(user), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = FLPrimary)
        }
    }
}

private fun initials(user: UserSummary): String {
    val first = user.firstName.firstOrNull()?.toString() ?: ""
    val last = user.lastName.firstOrNull()?.toString() ?: ""
    val combined = (first + last).uppercase()
    return combined.ifEmpty { user.username.firstOrNull()?.uppercase() ?: "?" }
}
