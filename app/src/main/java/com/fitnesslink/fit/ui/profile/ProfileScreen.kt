package com.fitnesslink.fit.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.auth.AuthManager
import com.fitnesslink.fit.media.MediaRef
import com.fitnesslink.fit.model.ProfileMenu
import com.fitnesslink.fit.ui.components.FLImageView
import com.fitnesslink.fit.ui.theme.FLPrimary
import com.fitnesslink.fit.ui.theme.FLTheme
import com.fitnesslink.fit.viewmodel.ProfileViewModel

/**
 * Sign-out hook injected by `MainTabNavigation` (iOS `\.logout` environment),
 * so the Profile menu — a sheet off the shared header — can reset the root's
 * gating flags after `AuthManager.logout` runs.
 */
val LocalLogoutAction = compositionLocalOf<() -> Unit> { {} }

/**
 * The Profile menu as a full screen (route `profile`), kept for deep links
 * and any caller that still pushes it. The header avatar shows the same
 * content in a sheet ([ProfileMenuContent]).
 */
@Composable
fun ProfileScreen(
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    onNavigateToNotifications: () -> Unit = { onNavigate("notifications") },
    onClose: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxSize().background(FLTheme.colors.background)) {
        ProfileMenuContent(onNavigate = onNavigate, onClose = onClose, onLogout = onLogout)
    }
}

/**
 * Profile menu (iOS `ProfileView`, Navigation PRD §6): avatar, name, email,
 * account items, and Logout as a separate action at the bottom (§6.4).
 */
@Composable
fun ProfileMenuContent(
    onNavigate: (String) -> Unit,
    onClose: (() -> Unit)?,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: ProfileViewModel = viewModel()
    val currentUser by AuthManager.currentUser.collectAsState()

    // Re-read whenever the signed-in user changes (login, profile refresh).
    LaunchedEffect(currentUser) { viewModel.loadData() }

    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        if (onClose != null) {
            Box(
                modifier = Modifier
                    .padding(start = 12.dp, top = 4.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose)
                    .semantics { contentDescription = "Close" },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = FLTheme.colors.textPrimary, modifier = Modifier.size(20.dp))
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProfileAvatar(photoRef = viewModel.photoRef, initials = viewModel.initials, size = 96.dp)
            Text(
                viewModel.userName.ifBlank { "FitnessLink Member" },
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = FLTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            if (viewModel.userEmail.isNotBlank()) {
                Text(
                    viewModel.userEmail,
                    fontSize = 14.sp,
                    color = FLTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(FLTheme.colors.surface)
        ) {
            viewModel.menuItems.forEachIndexed { index, item ->
                val route = viewModel.routeForMenuItem(item)
                if (route != null) {
                    ProfileItemView(item = item, onClick = { onNavigate(route) })
                }
                if (index != viewModel.menuItems.lastIndex) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(FLTheme.colors.divider)
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Runs the existing sign-out with no confirmation — the app never asked for one.
        Text(
            "Logout",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = FLTheme.colors.danger,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(FLTheme.colors.surface)
                .clickable {
                    // AuthManager.logout runs the ordered teardown (push
                    // unregister → Firebase signOut → clear DB → sync reset);
                    // AppNavigation reacts via auth state. The callback only
                    // lets the root reset its own gating flags.
                    viewModel.logout()
                    onLogout()
                }
                .padding(vertical = 14.dp)
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ProfileItemView(item: ProfileMenu, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        profileMenuIcon(item.icon)?.let { icon ->
            Icon(icon, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
        }
        Text(text = item.text, fontSize = 16.sp, color = FLTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = FLTheme.colors.textSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun profileMenuIcon(key: String): ImageVector? = when (key) {
    "person" -> Icons.Filled.Person
    "pencil" -> Icons.Filled.Edit
    "gear" -> Icons.Filled.Settings
    "card" -> Icons.Filled.CreditCard
    "watch" -> Icons.Filled.Watch
    "tv" -> Icons.Filled.Tv
    "bell" -> Icons.Filled.Notifications
    "calendar" -> Icons.Filled.CalendarMonth
    "video" -> Icons.Filled.VideoLibrary
    else -> null
}

/**
 * The user's photo in a circle, falling back to initials, then a generic
 * person glyph when there's no name either (iOS `ProfileAvatar`).
 */
@Composable
fun ProfileAvatar(photoRef: MediaRef?, initials: String, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(FLPrimary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        if (initials.isEmpty()) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(size * 0.5f))
        } else {
            Text(initials, fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.Bold, color = FLPrimary)
        }
        if (photoRef != null) {
            FLImageView(ref = photoRef, modifier = Modifier.size(size).clip(CircleShape))
        }
    }
}
