package com.johnz.diceroller.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.johnz.diceroller.DiceParser
import com.johnz.diceroller.DiceType
import com.johnz.diceroller.data.db.ActionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDonate: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val isSystemHapticsEnabled by viewModel.isSystemHapticsEnabled.collectAsState()
    val allCards by viewModel.allActionCards.collectAsState()
    
    val context = LocalContext.current
    
    var showAddCardDialog by remember { mutableStateOf(false) }
    var cardToEdit by remember { mutableStateOf<ActionCard?>(null) }
    var cardToDelete by remember { mutableStateOf<ActionCard?>(null) }
    var showCreditsDialog by remember { mutableStateOf(false) }

    if (showAddCardDialog) {
        ActionCardDialog(
            title = "New Action Card",
            confirmText = "Create",
            existingNames = allCards.map { it.name },
            onDismiss = { showAddCardDialog = false },
            onConfirm = { name, formula, visual, isMutable ->
                viewModel.addCustomActionCard(name, formula, visual, isMutable)
                showAddCardDialog = false
            }
        )
    }

    if (cardToEdit != null) {
        ActionCardDialog(
            title = "Edit Action Card",
            confirmText = "Update",
            existingNames = allCards.filter { it.id != cardToEdit!!.id }.map { it.name }, // Exclude self
            initialName = cardToEdit!!.name,
            initialFormula = cardToEdit!!.formula,
            initialVisual = cardToEdit!!.visualType,
            initialIsMutable = cardToEdit!!.isMutable,
            onDismiss = { cardToEdit = null },
            onConfirm = { name, formula, visual, isMutable ->
                val updatedCard = cardToEdit!!.copy(
                    name = name,
                    formula = formula,
                    visualType = visual,
                    isMutable = isMutable
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
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
                            Text(
                                text = "Haptic Feedback Disabled",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap to enable system haptics.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // --- Action Cards Management ---
            Text(
                text = "Action Cards",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // Sort: System cards first, then Custom cards
            val sortedCards = allCards.sortedWith(
                compareBy<ActionCard> { !it.isSystem } // false (System) < true (Custom)
                .thenBy { it.id } // Stable sort for creation order
            )
            
            if (sortedCards.isEmpty()) {
                Text(
                    text = "No cards available.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color.Gray
                )
            }
            
            // Use Column for items since we are inside a verticalScroll Column
            Column {
                sortedCards.forEach { card ->
                    ActionCardRow(
                        card = card, 
                        onEdit = { cardToEdit = card },
                        onDelete = { cardToDelete = card }
                    )
                }
            }
            
            Button(
                onClick = { showAddCardDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Create New Action Card")
            }
            
            Divider(modifier = Modifier.padding(vertical = 16.dp))
            
            // --- Donate Section ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToDonate() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Donate",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Donate",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Support the developer",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider(modifier = Modifier.padding(horizontal = 16.dp))

            // --- Credits Section ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCreditsDialog = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Credits",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Credits",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Attributions & Licenses",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ActionCardRow(card: ActionCard, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = card.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val desc = if (card.isMutable) "Adjustable (${card.visualType.label})" else card.formula
            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun ActionCardDialog(
    title: String,
    confirmText: String,
    existingNames: List<String>,
    initialName: String = "",
    initialFormula: String = "",
    initialVisual: DiceType = DiceType.D20,
    initialIsMutable: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String, String, DiceType, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var formula by remember { mutableStateOf(initialFormula) }
    var selectedVisual by remember { mutableStateOf(initialVisual) }
    var selectedBaseDie by remember { mutableStateOf(if (initialVisual.faces > 0 && initialVisual != DiceType.CUSTOM) initialVisual else DiceType.D20) }
    
    // Determine initial mode selection
    // 0 = Fixed, 1 = Adjustable
    // If opening for Edit, infer mode from isMutable
    var modeSelection by remember { mutableStateOf(if (initialIsMutable) 1 else 0) }
    
    // Update selectedBaseDie if we are in Adjustable mode and editing
    LaunchedEffect(Unit) {
        if (initialIsMutable && initialVisual.faces > 0 && initialVisual != DiceType.CUSTOM) {
            selectedBaseDie = initialVisual
        }
    }
    
    var validationError by remember { mutableStateOf<String?>(null) }
    var showErrorDialog by remember { mutableStateOf(false) }

    if (showErrorDialog && validationError != null) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Invalid Input") },
            text = { Text(validationError!!) },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) { Text("OK") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                
                Text(
                    text = "Action Mode / Roll Behavior:",
                    style = MaterialTheme.typography.titleSmall
                )
                Row(
                    Modifier.fillMaxWidth().selectable(selected = (modeSelection == 0), onClick = { modeSelection = 0 }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = (modeSelection == 0), onClick = { modeSelection = 0 })
                    Text("Fixed Roll", style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    Modifier.fillMaxWidth().selectable(selected = (modeSelection == 1), onClick = { modeSelection = 1 }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = (modeSelection == 1), onClick = { modeSelection = 1 })
                    Text("Adjustable Roll", style = MaterialTheme.typography.bodyMedium)
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                OutlinedTextField(
                    value = name, 
                    onValueChange = { name = it }, 
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (modeSelection == 0) {
                    OutlinedTextField(
                        value = formula, 
                        onValueChange = { formula = it }, 
                        label = { Text("Formula (e.g. 2d6 + 3)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text("Icon / Visual:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top=8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(DiceType.values().filter { it.faces > 0 && it != DiceType.CUSTOM }) { type ->
                            val isSelected = selectedVisual == type
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedVisual = type },
                                label = { Text(type.label) }
                            )
                        }
                    }
                } else {
                    Text("Select Base Die:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top=8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(DiceType.values().filter { it.faces > 0 && it != DiceType.CUSTOM }) { type ->
                            val isSelected = selectedBaseDie == type
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedBaseDie = type },
                                label = { Text(type.label) }
                            )
                        }
                    }
                    Text(
                        text = "Defaults to 1${selectedBaseDie.label.lowercase()}. You can adjust count/modifier when rolling.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    // Validation Logic
                    if (name.isBlank()) {
                        validationError = "Name cannot be empty."
                        showErrorDialog = true
                        return@Button
                    }
                    
                    if (existingNames.any { it.equals(name, ignoreCase = true) }) {
                        validationError = "A card with this name already exists."
                        showErrorDialog = true
                        return@Button
                    }

                    if (modeSelection == 0) {
                        if (formula.isBlank()) {
                            validationError = "Formula cannot be empty."
                            showErrorDialog = true
                            return@Button
                        }
                        if (!DiceParser.isValid(formula)) {
                            validationError = "Invalid formula format. Use standard notation like '2d6+3'."
                            showErrorDialog = true
                            return@Button
                        }
                        onConfirm(name, formula, selectedVisual, false)
                    } else {
                        val generatedFormula = "1d${selectedBaseDie.faces}"
                        onConfirm(name, generatedFormula, selectedBaseDie, true)
                    }
                }
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
