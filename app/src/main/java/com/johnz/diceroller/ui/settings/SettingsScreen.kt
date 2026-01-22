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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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

// Unified Dialog State
sealed interface SettingsDialogState {
    data object None : SettingsDialogState
    data object Add : SettingsDialogState
    data class Edit(val card: ActionCard) : SettingsDialogState
    data class Delete(val card: ActionCard) : SettingsDialogState
    data object Credits : SettingsDialogState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDonate: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    // 1. Decoupled State Observation
    val actionCards by viewModel.actionCardsState.collectAsState()
    val settingsState by viewModel.generalSettingsState.collectAsState()
    
    val context = LocalContext.current
    
    // UI State for Dialogs managed via Sealed Class
    var dialogState by remember { mutableStateOf<SettingsDialogState>(SettingsDialogState.None) }
    
    var titleTapCount by remember { mutableStateOf(0) }

    // ... Dialogs ...
    when (val state = dialogState) {
        SettingsDialogState.Add -> {
            ActionCardDialog(
                title = "New Action Card",
                confirmText = "Create",
                existingNames = actionCards.map { it.name },
                onDismiss = { dialogState = SettingsDialogState.None },
                onConfirm = { name, formula, visual, type, steps ->
                    viewModel.addCustomActionCard(name, formula, visual, type, steps)
                    dialogState = SettingsDialogState.None
                }
            )
        }
        is SettingsDialogState.Edit -> {
            ActionCardDialog(
                title = "Edit Action Card",
                confirmText = "Update",
                existingNames = actionCards.filter { it.id != state.card.id }.map { it.name },
                initialName = state.card.name,
                initialFormula = state.card.formula,
                initialVisual = state.card.visualType,
                initialType = state.card.type,
                initialSteps = state.card.steps,
                onDismiss = { dialogState = SettingsDialogState.None },
                onConfirm = { name, formula, visual, type, steps ->
                    val updatedCard = state.card.copy(
                        name = name,
                        formula = formula,
                        visualType = visual,
                        type = type,
                        steps = steps
                    )
                    viewModel.updateActionCard(updatedCard)
                    dialogState = SettingsDialogState.None
                }
            )
        }
        is SettingsDialogState.Delete -> {
            AlertDialog(
                onDismissRequest = { dialogState = SettingsDialogState.None },
                title = { Text("Delete Action Card?") },
                text = { Text("Are you sure you want to delete '${state.card.name}'?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteActionCard(state.card)
                            dialogState = SettingsDialogState.None
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialogState = SettingsDialogState.None }) {
                        Text("Cancel")
                    }
                }
            )
        }
        SettingsDialogState.Credits -> {
            AlertDialog(
                onDismissRequest = { dialogState = SettingsDialogState.None },
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
                    TextButton(onClick = { dialogState = SettingsDialogState.None }) {
                        Text("Close")
                    }
                }
            )
        }
        SettingsDialogState.None -> {
            // No dialog
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Settings",
                        modifier = Modifier.clickable {
                            if (!settingsState.debugModeEnabled) {
                                titleTapCount++
                                if (titleTapCount >= 10) {
                                    viewModel.setDebugModeEnabled(true)
                                    Toast.makeText(context, "Debug Mode Enabled", Toast.LENGTH_SHORT).show()
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            
            // Haptic Warning: Conditional but keyed
            if (!settingsState.isSystemHapticsEnabled) {
                item(key = "haptic_warning") {
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
            }

            item(key = "action_cards_title") {
                Text(
                    text = "Action Cards",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            
            if (actionCards.isEmpty()) {
                item(key = "no_cards") {
                    Text(
                        text = "No cards available.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = Color.Gray
                    )
                }
            } else {
                items(actionCards, key = { "card_${it.id}" }) { card ->
                    ActionCardRow(
                        card = card, 
                        onEdit = { dialogState = SettingsDialogState.Edit(card) },
                        onDuplicate = { viewModel.duplicateActionCard(card) },
                        onDelete = { dialogState = SettingsDialogState.Delete(card) }
                    )
                }
            }
            
            item(key = "add_card_button") {
                Button(
                    onClick = { dialogState = SettingsDialogState.Add },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create New Action Card")
                }
            }
            
            item(key = "general_header") {
                Column {
                    Divider(modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        text = "General",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            item(key = "sound_setting") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Sound Effects")
                    Switch(checked = settingsState.soundEnabled, onCheckedChange = { viewModel.onSoundEnabledChanged(it) })
                }
            }

            item(key = "crit_setting") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Critical Hit/Miss Effects")
                    Switch(checked = settingsState.critEffectsEnabled, onCheckedChange = { viewModel.onCritEffectsEnabledChanged(it) })
                }
            }
            
            // --- Debug ---
            if (settingsState.debugModeEnabled) {
                item(key = "debug_header") {
                    Column {
                        Divider(modifier = Modifier.padding(vertical = 16.dp))
                        Text(
                            text = "Debug Mode",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                item(key = "debug_disable") {
                    Button(
                        onClick = { viewModel.setDebugModeEnabled(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Disable Debug Mode")
                    }
                }
                item(key = "debug_nat20") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Always Nat 20")
                        Switch(checked = settingsState.alwaysNat20, onCheckedChange = { viewModel.setAlwaysNat20(it) })
                    }
                }
                item(key = "debug_nat1") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Always Nat 1")
                        Switch(checked = settingsState.alwaysNat1, onCheckedChange = { viewModel.setAlwaysNat1(it) })
                    }
                }
            }
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

// Pure function to prepare steps list. Moves logic out of Composition.
private fun prepareComboSteps(initialSteps: String): List<UiComboStep> {
    val steps = mutableListOf<UiComboStep>()
    
    // Parse
    if (initialSteps.isNotBlank()) {
        val parsed = initialSteps.split("|").mapIndexedNotNull { index, s ->
            val parts = s.split(";")
            if (parts.size >= 3) {
                val thresholdVal = if (parts.size >= 4) parts[3] else "10"
                UiComboStep(index, parts[0], parts[1], parts[2].toBoolean(), thresholdVal)
            } else null
        }
        steps.addAll(parsed)
    }

    // Ensure Defaults / Integrity
    if (steps.isEmpty()) {
        steps.add(UiComboStep(0, "Attack", "1d20+5", true, "10"))
    }
    if (steps.size < 2) {
        val nextId = if (steps.isEmpty()) 0 else steps.maxOf { it.id } + 1
        steps.add(UiComboStep(nextId, "Damage", "1d8+3", false))
    }
    
    // Normalize IDs just in case
    return steps.mapIndexed { i, s -> s.copy(id = i) }
}

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
    var selectedType by remember { mutableStateOf(initialType) }
    
    // Helper to determine initial visual safely
    val defaultBase = if (initialVisual.faces > 0 && initialVisual != DiceType.CUSTOM) initialVisual else DiceType.D20
    var selectedBaseDie by remember { 
        mutableStateOf(if (initialType == ActionCardType.SIMPLE && initialVisual.faces > 0) initialVisual else defaultBase)
    }
    
    // Initialize Visual (Icon) for Formula/Combo types
    var selectedVisual by remember { mutableStateOf(initialVisual) }
    
    // OPTIMIZED STATE: Use standard List + State, no SnapshotStateList
    var comboSteps by remember(initialSteps) {
        mutableStateOf(prepareComboSteps(initialSteps))
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
                        // IMMUTABLE UPDATE: filter out the index
                        if (stepIndexToDelete in comboSteps.indices) {
                            comboSteps = comboSteps.filterIndexed { index, _ -> index != stepIndexToDelete }
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
                        // Use standard forEachIndexed on the List
                        comboSteps.forEachIndexed { index, step ->
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
                                                    // IMMUTABLE UPDATE: direct delete
                                                    comboSteps = comboSteps.filterIndexed { i, _ -> i != index }
                                                }
                                            }) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove")
                                            }
                                        }
                                    }
                                    OutlinedTextField(
                                        value = step.name, 
                                        onValueChange = { newName ->
                                            // IMMUTABLE UPDATE: map and copy
                                            comboSteps = comboSteps.mapIndexed { i, s -> if (i == index) s.copy(name = newName) else s }
                                        },
                                        label = { Text("Step Name") }, modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = step.formula, 
                                        onValueChange = { newFormula ->
                                            // IMMUTABLE UPDATE: map and copy
                                            comboSteps = comboSteps.mapIndexed { i, s -> if (i == index) s.copy(formula = newFormula) else s }
                                        },
                                        label = { Text("Formula") }, modifier = Modifier.fillMaxWidth()
                                    )
                                    
                                    // Target AC only for the first step (Attack)
                                    if (index == 0) {
                                        OutlinedTextField(
                                            value = step.threshold, 
                                            onValueChange = { newVal ->
                                                if (newVal.all { char -> char.isDigit() }) {
                                                    comboSteps = comboSteps.mapIndexed { i, s -> if (i == index) s.copy(threshold = newVal) else s }
                                                }
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
                        Button(onClick = { 
                            // IMMUTABLE UPDATE: Add new item
                            comboSteps = comboSteps + UiComboStep(comboSteps.size, "", "", false) 
                        }) {
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
                        if (comboSteps.isEmpty()) { validationError = "At least one step required"; showErrorDialog = true; return@Button }
                        val sb = StringBuilder()
                        comboSteps.forEachIndexed { i, s ->
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