package com.fitnesslink.fit.ui.profile

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitnesslink.fit.ui.components.HeaderBackView
import com.fitnesslink.fit.ui.theme.*
import com.fitnesslink.fit.viewmodel.PersonalInfoViewModel

@Composable
fun PersonalInfoScreen(
    onBack: () -> Unit,
    onNavigateToBilling: () -> Unit,
    viewModel: PersonalInfoViewModel = viewModel()
) {
    val context = LocalContext.current

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes()
            if (bytes != null) {
                viewModel.saveProfileImage(bytes, context.filesDir)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadData()
        viewModel.loadProfileImage(context.filesDir)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        HeaderBackView(onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 10.dp, bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .clickable { photoLauncher.launch("image/*") }
            ) {
                val bitmap = viewModel.profileImage
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Profile",
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(FLPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(viewModel.initials, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = FLPrimary)
                    }
                }
                // Camera badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(FLPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = White, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Active badge
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (viewModel.isActive) FLPrimary else MediumGray)
                )
                Text(
                    if (viewModel.isActive) "Active" else "Inactive",
                    fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    color = if (viewModel.isActive) FLPrimary else MediumGray
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Fields
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(White)
            ) {
                FieldRow("First Name", viewModel.firstName, { viewModel.firstName = it })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                FieldRow("Last Name", viewModel.lastName, { viewModel.lastName = it })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                FieldRow("Username", viewModel.username, { viewModel.username = it })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                FieldRow("Email", viewModel.email, { viewModel.email = it }, KeyboardType.Email)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                FieldRow("Phone", viewModel.phone, { viewModel.phone = it }, KeyboardType.Phone)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                FieldRow("Country", viewModel.country, { viewModel.country = it })
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Member since
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = TextSecondaryColor, modifier = Modifier.size(14.dp))
                Text("Member since March 2026", fontSize = 14.sp, color = TextSecondaryColor)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Save button
            Button(
                onClick = { viewModel.saveChanges() },
                enabled = viewModel.hasChanges,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FLPrimary,
                    disabledContainerColor = DisabledButton
                )
            ) {
                Text("Save Changes", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Links
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToBilling)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CreditCard, contentDescription = null, tint = FLPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Billing & Subscription", fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MediumGray, modifier = Modifier.size(14.dp))
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.showDeleteConfirmation = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = androidx.compose.ui.graphics.Color.Red, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Delete Account", fontSize = 15.sp, color = androidx.compose.ui.graphics.Color.Red)
                }
            }
        }
    }

    if (viewModel.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.showDeleteConfirmation = false },
            title = { Text("Delete Account") },
            text = { Text("Are you sure you want to delete your account? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.showDeleteConfirmation = false }) {
                    Text("Delete", color = androidx.compose.ui.graphics.Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FieldRow(label: String, value: String, onValueChange: (String) -> Unit, keyboardType: KeyboardType = KeyboardType.Text) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, fontSize = 12.sp, color = TextSecondaryColor)
        Spacer(modifier = Modifier.height(4.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = White,
                unfocusedContainerColor = White,
                focusedIndicatorColor = FLPrimary,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
            ),
            singleLine = true
        )
    }
}
