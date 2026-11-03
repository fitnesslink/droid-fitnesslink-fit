package com.fitnesslink.fit.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitnesslink.fit.R
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.push.NotificationBadge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.ui.navigation.AppRouter
import com.fitnesslink.fit.ui.profile.LocalLogoutAction
import com.fitnesslink.fit.ui.profile.ProfileAvatar
import com.fitnesslink.fit.ui.profile.ProfileMenuContent
import com.fitnesslink.fit.ui.theme.FLTheme
import com.fitnesslink.fit.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

/**
 * Tab-root header (iOS `HeaderView`): centred wordmark, a notification bell
 * with a red unread badge (capped at 99), and — Navigation PRD §4.2 — the
 * user's avatar, which opens the Profile menu in a sheet. The unread count is
 * the larger of the server-driven [NotificationBadge.unread] and the locally
 * cached unread rows, so a push that landed while offline and an inbox read
 * in-app both reflect.
 *
 * Menu items close the sheet and push onto the current tab's stack through
 * [AppRouter.push] (iOS pushes inside the sheet's own stack; Android has one
 * NavController per tab host, so Back from an item returns to the tab).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeaderView(modifier: Modifier = Modifier, onNotificationsTap: () -> Unit = {}) {
    val serverUnread by NotificationBadge.unread.collectAsState()
    val localUnread by produceState(initialValue = 0, key1 = serverUnread) {
        value = withContext(Dispatchers.IO) {
            runCatching { DatabaseManager.unreadNotificationCount() }.getOrDefault(0)
        }
    }
    val unreadCount = maxOf(serverUnread, localUnread)

    var showProfileMenu by remember { mutableStateOf(false) }
    // Bumped when the menu closes so a photo changed in Personal Information
    // shows in the header straight away.
    var userVersion by remember { mutableIntStateOf(0) }
    val currentUser by AuthManager.currentUser.collectAsState()
    val avatar by produceState(initialValue = AvatarInfo(null, ""), userVersion, currentUser) {
        value = withContext(Dispatchers.IO) {
            val user = runCatching { DatabaseManager.user() }.getOrNull()
            val name = user?.name?.takeIf { it.isNotBlank() } ?: currentUser?.fullName.orEmpty()
            AvatarInfo(
                photoRef = user?.profileImageId?.takeIf { it.isNotBlank() }?.let { MediaRef.UserPhoto(it) },
                initials = ProfileViewModel.initialsOf(name)
            )
        }
    }
    val logout = LocalLogoutAction.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.fitnesslink),
            contentDescription = "FitnessLink",
            // Single-colour (dark) wordmark PNG: template-tint it like iOS (`.renderingMode(.template)`).
            colorFilter = ColorFilter.tint(FLTheme.colors.textPrimary),
            modifier = Modifier
                .align(Alignment.Center)
                .width(134.dp)
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClick = onNotificationsTap)
                    .semantics {
                        contentDescription = if (unreadCount > 0) "Notifications, $unreadCount unread" else "Notifications"
                    },
                contentAlignment = Alignment.Center
            ) {
                Box {
                    Image(
                        painter = painterResource(R.drawable.notificationbell),
                        contentDescription = null,
                        // Grey PNG: keep its own grey in light, lift it in dark so it doesn't vanish.
                        colorFilter = if (FLTheme.colors.isDark) ColorFilter.tint(FLTheme.colors.textSecondary) else null,
                        modifier = Modifier.size(20.dp)
                    )
                    if (unreadCount > 0) {
                        UnreadBadge(
                            count = unreadCount,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 8.dp, y = (-6).dp)
                        )
                    }
                }
            }
            ProfileAvatar(
                photoRef = avatar.photoRef,
                initials = avatar.initials,
                size = 32.dp,
                modifier = Modifier
                    .clickable { showProfileMenu = true }
                    .semantics { contentDescription = "Profile" }
            )
        }
    }

    if (showProfileMenu) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        fun close(then: () -> Unit = {}) {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                showProfileMenu = false
                userVersion++
                then()
            }
        }
        ModalBottomSheet(
            onDismissRequest = { showProfileMenu = false; userVersion++ },
            sheetState = sheetState,
            containerColor = FLTheme.colors.background
        ) {
            ProfileMenuContent(
                onNavigate = { route -> close { AppRouter.push(route) } },
                onClose = { close() },
                onLogout = { showProfileMenu = false; logout() }
            )
        }
    }
}

private data class AvatarInfo(val photoRef: MediaRef?, val initials: String)

@Composable
private fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            // Fixed height + min width so a single digit is a true circle
            // (the text's line height otherwise stretches it into an oval).
            .height(16.dp)
            .widthIn(min = 16.dp)
            .background(Color.Red, CircleShape)
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = minOf(count, 99).toString(),
            style = TextStyle(
                fontSize = 9.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Bold,
                color = FLTheme.colors.onBrand,
                textAlign = TextAlign.Center,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            maxLines = 1
        )
    }
}

@Composable
fun HeaderBackView(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.back),
            contentDescription = "Back",
            colorFilter = ColorFilter.tint(FLTheme.colors.textPrimary),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(20.dp)
                .clickable(onClick = onBack)
        )
        Image(
            painter = painterResource(R.drawable.fitnesslink),
            contentDescription = "FitnessLink",
            // Single-colour (dark) wordmark PNG: template-tint it like iOS (`.renderingMode(.template)`).
            colorFilter = ColorFilter.tint(FLTheme.colors.textPrimary),
            modifier = Modifier
                .align(Alignment.Center)
                .width(134.dp)
        )
    }
}
