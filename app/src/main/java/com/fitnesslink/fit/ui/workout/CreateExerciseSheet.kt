package com.fitnesslink.fit.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fitnesslink.fit.model.EquipmentType
import com.fitnesslink.fit.model.MovementLibraryItem
import com.fitnesslink.fit.model.MuscleGroup
import com.fitnesslink.fit.persistence.DatabaseManager
import com.fitnesslink.fit.ui.theme.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateExerciseSheet(
    onCreated: (MovementLibraryItem) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedMuscle by remember { mutableStateOf(MuscleGroup.Chest) }
    var selectedEquipment by remember { mutableStateOf(EquipmentType.Bodyweight) }
    var muscleExpanded by remember { mutableStateOf(false) }
    var equipmentExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(White)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("New Exercise", fontSize = 20.sp, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Exercise Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3
            )

            // Muscle Group dropdown
            ExposedDropdownMenuBox(expanded = muscleExpanded, onExpandedChange = { muscleExpanded = it }) {
                OutlinedTextField(
                    value = selectedMuscle.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Muscle Group") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = muscleExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = muscleExpanded, onDismissRequest = { muscleExpanded = false }) {
                    MuscleGroup.entries.forEach { mg ->
                        DropdownMenuItem(
                            text = { Text(mg.label) },
                            onClick = { selectedMuscle = mg; muscleExpanded = false }
                        )
                    }
                }
            }

            // Equipment dropdown
            ExposedDropdownMenuBox(expanded = equipmentExpanded, onExpandedChange = { equipmentExpanded = it }) {
                OutlinedTextField(
                    value = selectedEquipment.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Equipment") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = equipmentExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = equipmentExpanded, onDismissRequest = { equipmentExpanded = false }) {
                    EquipmentType.entries.forEach { eq ->
                        DropdownMenuItem(
                            text = { Text(eq.label) },
                            onClick = { selectedEquipment = eq; equipmentExpanded = false }
                        )
                    }
                }
            }

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val id = UUID.randomUUID().toString()
                        DatabaseManager.insertMovementFull(
                            id, name, description,
                            selectedMuscle.label, selectedEquipment.label
                        )
                        onCreated(MovementLibraryItem(
                            id = id, name = name, description = description,
                            muscleGroup = selectedMuscle.label,
                            equipment = selectedEquipment.label,
                            isFavorite = false
                        ))
                    },
                    enabled = name.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = FLPrimary)
                ) {
                    Text("Create")
                }
            }
        }
    }
}
