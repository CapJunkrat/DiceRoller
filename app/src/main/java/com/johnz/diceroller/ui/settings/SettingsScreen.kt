package com.johnz.diceroller.ui.settings

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.johnz.diceroller.DiceParser
import com.johnz.diceroller.DiceType
import com.johnz.diceroller.data.db.ActionCard
import com.johnz.diceroller.data.db.ActionCardType

// Custom ContentCopy Icon
val CopyIcon: ImageVector = ImageVector.Builder(
    name = "ContentCopy",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(16.0f, 1.0f)
        horizontalLineTo(4.0f)
        curveTo(2.9f, 1.0f, 2.0f, 1.9f, 2.0f, 3.0f)
        verticalLineTo(17.0f)
        horizontalLineTo(4.0f)
        verticalLineTo(3.0f)
        horizontalLineTo(16.0f)
        verticalLineTo(1.0f)
        close()
        moveTo(19.0f, 5.0f)
        horizontalLineTo(8.0f)
        curveTo(6.9f, 5.0f, 6.0f, 5.9f, 6.0f, 7.0f)
        verticalLineTo(21.0f)
        curveTo(6.0f, 22.1f, 6.9f, 23.0f, 8.0f, 23.0f)
        horizontalLineTo(19.0f)
        curveTo(20.1f, 23.0f, 21.0f, 22.1f, 21.0f, 21.0f)
        verticalLineTo(7.0f)
        curveTo(21.0f, 5.9f, 20.1f, 5.0f, 19.0f, 5.0f)
        close()
        moveTo(19.0f, 21.0f)
        horizontalLineTo(8.0f)
        verticalLineTo(7.0f)
        horizontalLineTo(19.0f)
        verticalLineTo(21.0f)
        close()
    }
}.build()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDonate: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val isSystemHapticsEnabled by viewModel.isSystemHapticsEnabled.collectAsState()
    val allCards by viewModel.allActionCards.collectAsState()
    
    val debugModeEnabled by viewModel.debugModeEnabled.collectAsState()
    val alwaysNat20 by viewModel.alwaysNat20.collectAsState()
    val alwaysNat1 by viewModel.alwaysNat1.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val critEffectsEnabled by viewModel.critEffectsEnabled.collectAsState()
    
    val context = LocalContext.current
    
    var showAddCardDialog by remember { mutableStateOf(false) }
    var cardToEdit by remember { mutableStateOf<ActionCard?>(null) }
    var cardToDelete by remember { mutableStateOf<ActionCard?>(null) }
    var showCreditsDialog by remember { mutableStateOf(false) }
    
    var titleTapCount by remember { mutableStateOf(0) }

    if (showAddCardDialog) {
        ActionCardDialog(
            title = "New Action Card",
            confirmText = "Create",
            existingNames = allCards.map { it.name },
            onDismiss = { showAddCardDialog = false },
            onConfirm = { name, formula, visual, type, steps ->
                viewModel.addCustomActionCard(name, formula, visual, type, steps)
                showAddCardDialog = false
            }
        )
    }

    if (cardToEdit != null) {
        ActionCardDialog(
            title = "Edit Action Card",
            confirmText = "Update",
            existingNames = allCards.filter { it.id != cardToEdit!!.id }.map { it.name },
            initialName = cardToEdit!!.name,
            initialFormula = cardToEdit!!.formula,
            initialVisual = cardToEdit!!.visualType,
            initialType = cardToEdit!!.type,
            initialSteps = cardToEdit!!.steps,
            onDismiss = { cardToEdit = null },
            onConfirm = { name, formula, visual, type, steps ->
                val updatedCard = cardToEdit!!.copy(
                    name = name,
                    formula = formula,
                    visualType = visual,
                    type = type,
                    steps = steps
                )
                viewModel.updateActionCard(updatedCard)
                cardToEdit = null
            }
        )
    }

    if (cardToDelete != null) {
        AlertDialog(
            onDismissRequest = { cardToDelete = null },
            title = { Text("Delete Action Card?") },
            text = { Text("Are you sure you want to delete '${cardToDelete?.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        cardToDelete?.let { viewModel.deleteActionCard(it) }
                        cardToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { cardToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreditsDialog) {
        AlertDialog(
            onDismissRequest = { showCreditsDialog = false },
            title = { Text("Credits") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Sound Effects", fontWeight = FontWeight.Bold)
                    Text(text = "“Various sound effects from Rubik's Race”")
                    Text(text = "by Nick Bowler, licensed under CC BY 3.0")
                    Text(text = "https://creativecommons.org/licenses/by/3.0/")
                }
            },
            confirmButton = {
                TextButton(onClick = { showCreditsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Settings",
                        modifier = Modifier.clickable {
                            if (!debugModeEnabled) {
                                titleTapCount++
                                if (titleTapCount >= 10) {
                                    viewModel.setDebugModeEnabled(true)
                                    Toast.makeText(context, "Debug Mode Enabled (If Debug Build)", Toast.LENGTH_SHORT).show()
                                    titleTapCount = 0
                                }
                            }
                        }
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            
            if (!isSystemHapticsEnabled) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clickable {
                            val intent = Intent(Settings.ACTION_SOUND_SETTINGS)
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(intent)
                            } else {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning, 
                            contentDescription = "Warning",
                            modifier = Modifier.padding(end = 16.dp)
                        )
                        Column {
                            Text(text = "Haptic Feedback Disabled", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = "Tap to enable system haptics.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Text(
                text = "Action Cards",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            val sortedCards = allCards.sortedWith(
                compareBy<ActionCard> { !it.isSystem }
                .thenBy { it.id }
            )
            
            if (sortedCards.isEmpty()) {
                Text(
                    text = "No cards available.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color.Gray
                )
            }
            
            Column {
                sortedCards.forEach { card ->
                    ActionCardRow(
                        card = card, 
                        onEdit = { cardToEdit = card },
                        onDuplicate = { viewModel.duplicateActionCard(card) },
                        onDelete = { cardToDelete = card }
                    )
                }
            }
            
            Button(
                onClick = { showAddCardDialog = true },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Create New Action Card")
            }
            
            Divider(modifier = Modifier.padding(vertical = 16.dp))
            
            Text(
                text = "General",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Sound Effects")
                Switch(checked = soundEnabled, onCheckedChange = { viewModel.onSoundEnabledChanged(it) })
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Critical Hit/Miss Effects")
                Switch(checked = critEffectsEnabled, onCheckedChange = { viewModel.onCritEffectsEnabledChanged(it) })
            }
            
            Divider(modifier = Modifier.padding(vertical = 16.dp))
            
            // --- Debug ---
            if (debugModeEnabled) {
                Text(
                    text = "Debug Mode",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Button(
                    onClick = { viewModel.setDebugModeEnabled(false) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Disable Debug Mode")
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Always Nat 20")
                    Switch(checked = alwaysNat20, onCheckedChange = { viewModel.setAlwaysNat20(it) })
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Always Nat 1")
                    Switch(checked = alwaysNat1, onCheckedChange = { viewModel.setAlwaysNat1(it) })
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ActionCardRow(card: ActionCard, onEdit: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = card.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val desc = when(card.type) {
                ActionCardType.SIMPLE -> "Simple Roll (${card.visualType.label})"
                ActionCardType.FORMULA -> "Formula: ${card.formula}"
                ActionCardType.COMBO -> "Action Combo"
            }
            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        IconButton(onClick = onDuplicate) { Icon(CopyIcon, contentDescription = "Duplicate", tint = MaterialTheme.colorScheme.primary) }
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary) }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
    }
}

data class UiComboStep(val id: Int, var name: String, var formula: String, var isAttack: Boolean, var threshold: String = "10")

@Composable
fun ActionCardDialog(
    title: String,
    confirmText: String,
    existingNames: List<String>,
    initialName: String = "",
    initialFormula: String = "",
    initialVisual: DiceType = DiceType.D20,
    initialType: ActionCardType = ActionCardType.SIMPLE,
    initialSteps: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, String, DiceType, ActionCardType, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var formula by remember { mutableStateOf(initialFormula) }
    var selectedVisual by remember { mutableStateOf(initialVisual) }
    var selectedType by remember { mutableStateOf(initialType) }
    
    // Combo Steps State
    val initialComboSteps = remember {
        if (initialSteps.isNotBlank()) {
            initialSteps.split("|").mapIndexedNotNull { index, s ->
                val parts = s.split(";")
                if (parts.size >= 3) {
                    val thresholdVal = if (parts.size >= 4) parts[3] else "10"
                    UiComboStep(index, parts[0], parts[1], parts[2].toBoolean(), thresholdVal)
                } else null
            }.toMutableStateList()
        } else {
            mutableStateListOf(UiComboStep(0, "Attack", "1d20+5", true, "10"), UiComboStep(1, "Damage", "1d8+3", false))
        }
    }
    
    // Ensure at least two steps if loading corrupted/empty data
    LaunchedEffect(initialComboSteps.size) {
        if (initialComboSteps.size < 2) {
            if (initialComboSteps.isEmpty()) {
                initialComboSteps.add(UiComboStep(0, "Attack", "1d20+5", true, "10"))
            }
            if (initialComboSteps.size < 2) {
                initialComboSteps.add(UiComboStep(1, "Damage", "1d8+3", false))
            }
        }
    }
    
    // Base die for SIMPLE mode
    var selectedBaseDie by remember { mutableStateOf(if (initialVisual.faces > 0 && initialVisual != DiceType.CUSTOM) initialVisual else DiceType.D20) }
    LaunchedEffect(Unit) {
        if (initialType == ActionCardType.SIMPLE && initialVisual.faces > 0) selectedBaseDie = initialVisual
    }
    
    var validationError by remember { mutableStateOf<String?>(null) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var stepIndexToDelete by remember { mutableStateOf(-1) }

    if (showErrorDialog && validationError != null) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Invalid Input") },
            text = { Text(validationError!!) },
            confirmButton = { TextButton(onClick = { showErrorDialog = false }) { Text("OK") } }
        )
    }

    if (stepIndexToDelete >= 0) {
        AlertDialog(
            onDismissRequest = { stepIndexToDelete = -1 },
            title = { Text("Delete Step?") },
            text = { Text("This step has content. Are you sure you want to remove it?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (stepIndexToDelete in initialComboSteps.indices) {
                            initialComboSteps.removeAt(stepIndexToDelete)
                        }
                        stepIndexToDelete = -1
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { stepIndexToDelete = -1 }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, 
                    label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                Text("Card Type:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top=8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { selectedType = ActionCardType.SIMPLE }) {
                        RadioButton(selected = selectedType == ActionCardType.SIMPLE, onClick = { selectedType = ActionCardType.SIMPLE })
                        Text("Simple Roll (Adjustable)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { selectedType = ActionCardType.FORMULA }) {
                        RadioButton(selected = selectedType == ActionCardType.FORMULA, onClick = { selectedType = ActionCardType.FORMULA })
                        Text("Formula Roll (Fixed)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { selectedType = ActionCardType.COMBO }) {
                        RadioButton(selected = selectedType == ActionCardType.COMBO, onClick = { selectedType = ActionCardType.COMBO })
                        Text("Action Combo (Multi-step)")
                    }
                }
                
                Divider(modifier = Modifier.padding(vertical = 4.dp))

                when (selectedType) {
                    ActionCardType.SIMPLE -> {
                        Text("Select Base Die:", style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DiceType.values().filter { it.faces > 0 && it != DiceType.CUSTOM }) { type ->
                                FilterChip(
                                    selected = selectedBaseDie == type,
                                    onClick = { selectedBaseDie = type },
                                    label = { Text(type.label) }
                                )
                            }
                        }
                    }
                    ActionCardType.FORMULA -> {
                        OutlinedTextField(
                            value = formula, onValueChange = { formula = it }, 
                            label = { Text("Formula (e.g. 2d6+3)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        Text("Icon:", style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DiceType.values().filter { it.faces > 0 && it != DiceType.CUSTOM }) { type ->
                                FilterChip(
                                    selected = selectedVisual == type,
                                    onClick = { selectedVisual = type },
                                    label = { Text(type.label) }
                                )
                            }
                        }
                    }
                    ActionCardType.COMBO -> {
                        Text("Steps:", style = MaterialTheme.typography.labelLarge)
                        initialComboSteps.forEachIndexed { index, step ->
                            val stepType = if (index == 0) "Attack" else "Damage"
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Step ${index + 1} ($stepType)", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        
                                        // Only allow deleting steps after the first two (Index 0 and 1 are fixed)
                                        if (index > 1) {
                                            IconButton(onClick = { 
                                                if (step.name.isNotBlank() || step.formula.isNotBlank()) {
                                                    stepIndexToDelete = index
                                                } else {
                                                    initialComboSteps.removeAt(index)
                                                }
                                            }) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove")
                                            }
                                        }
                                    }
                                    OutlinedTextField(
                                        value = step.name, onValueChange = { initialComboSteps[index] = step.copy(name = it) },
                                        label = { Text("Step Name") }, modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = step.formula, onValueChange = { initialComboSteps[index] = step.copy(formula = it) },
                                        label = { Text("Formula") }, modifier = Modifier.fillMaxWidth()
                                    )
                                    
                                    // Target AC only for the first step (Attack)
                                    if (index == 0) {
                                        OutlinedTextField(
                                            value = step.threshold, 
                                            onValueChange = { 
                                                if (it.all { char -> char.isDigit() }) initialComboSteps[index] = step.copy(threshold = it)
                                            },
                                            label = { Text("Target AC") },
                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                        )
                                    }
                                }
                            }
                        }
                        Button(onClick = { initialComboSteps.add(UiComboStep(initialComboSteps.size, "", "", false)) }) {
                            Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Add Damage Step")
                        }
                        Text("Icon:", style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DiceType.values().filter { it.faces > 0 && it != DiceType.CUSTOM }) { type ->
                                FilterChip(
                                    selected = selectedVisual == type,
                                    onClick = { selectedVisual = type },
                                    label = { Text(type.label) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) { validationError = "Name required"; showErrorDialog = true; return@Button }
                    
                    var finalFormula = formula
                    var finalSteps = ""
                    
                    if (selectedType == ActionCardType.SIMPLE) {
                        finalFormula = "1d${selectedBaseDie.faces}"
                        selectedVisual = selectedBaseDie
                    } else if (selectedType == ActionCardType.FORMULA) {
                        if (formula.isBlank() || !DiceParser.isValid(formula)) {
                            validationError = "Invalid formula"; showErrorDialog = true; return@Button
                        }
                    } else if (selectedType == ActionCardType.COMBO) {
                        if (initialComboSteps.isEmpty()) { validationError = "At least one step required"; showErrorDialog = true; return@Button }
                        val sb = StringBuilder()
                        initialComboSteps.forEachIndexed { i, s ->
                            if (s.name.isBlank() || s.formula.isBlank()) { validationError = "Step fields cannot be empty"; showErrorDialog = true; return@Button }
                            
                            // VALIDATION ADDED HERE
                            if (!DiceParser.isValid(s.formula)) {
                                validationError = "Invalid formula in Step ${i + 1}: ${s.formula}"; showErrorDialog = true; return@Button
                            }

                            if (i > 0) sb.append("|")
                            
                            // Force First Step = Attack, Others = Damage
                            val isAttack = (i == 0)
                            val thresh = if (isAttack) (if (s.threshold.isBlank()) "10" else s.threshold) else ""
                            
                            sb.append("${s.name};${s.formula};${isAttack};${thresh}")
                        }
                        finalSteps = sb.toString()
                        finalFormula = "COMBO" // placeholder
                    }
                    onConfirm(name, finalFormula, selectedVisual, selectedType, finalSteps)
                }
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
