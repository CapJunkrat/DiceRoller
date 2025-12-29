package com.johnz.diceroller.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.johnz.diceroller.DiceType
import com.johnz.diceroller.data.DiceStyle
import com.johnz.diceroller.data.db.ActionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDonate: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val isSystemHapticsEnabled by viewModel.isSystemHapticsEnabled.collectAsState()
    val currentStyle by viewModel.diceStyle.collectAsState()
    val allCards by viewModel.allActionCards.collectAsState()
    
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var showAddCardDialog by remember { mutableStateOf(false) }
    var cardToDelete by remember { mutableStateOf<ActionCard?>(null) }

    if (showAddCardDialog) {
        CreateActionCardDialog(
            onDismiss = { showAddCardDialog = false },
            onConfirm = { name, formula, visual, isMutable ->
                viewModel.addCustomActionCard(name, formula, visual, isMutable)
                showAddCardDialog = false
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkSystemHapticsStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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

            // --- Visual Style Section ---
            Text(
                text = "Visual Style",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StyleChip(
                    label = "2D",
                    isSelected = currentStyle == DiceStyle.FLAT_2D,
                    isEnabled = false,
                    onClick = { viewModel.onDiceStyleChanged(DiceStyle.FLAT_2D) },
                    modifier = Modifier.weight(1f)
                )
                StyleChip(
                    label = "2.5D",
                    isSelected = currentStyle == DiceStyle.CARTOON_25D,
                    onClick = { viewModel.onDiceStyleChanged(DiceStyle.CARTOON_25D) },
                    modifier = Modifier.weight(1f)
                )
                StyleChip(
                    label = "3D",
                    isSelected = currentStyle == DiceStyle.REALISTIC_3D,
                    isEnabled = false,
                    onClick = { viewModel.onDiceStyleChanged(DiceStyle.REALISTIC_3D) },
                    modifier = Modifier.weight(1f)
                )
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

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
                    ActionCardRow(card, onDelete = { cardToDelete = card })
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
        }
    }
}

@Composable
fun ActionCardRow(card: ActionCard, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = card.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = card.formula, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun CreateActionCardDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, DiceType, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var formula by remember { mutableStateOf("") }
    var selectedVisual by remember { mutableStateOf(DiceType.D20) }
    var isMutable by remember { mutableStateOf(false) }
    
    val canSubmit = name.isNotBlank() && formula.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Action Card") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, 
                    onValueChange = { name = it }, 
                    label = { Text("Name (e.g. Greatsword)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = formula, 
                    onValueChange = { formula = it }, 
                    label = { Text("Formula (e.g. 2d6 + 3)") },
                    singleLine = true
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { isMutable = !isMutable }
                ) {
                    Checkbox(checked = isMutable, onCheckedChange = { isMutable = it })
                    Text("Interactive Controls (Count/Modifier)")
                }
                
                Text("Icon / Visual:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top=8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DiceType.values().filter { it.faces > 0 }) { type ->
                        val isSelected = selectedVisual == type
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedVisual = type },
                            label = { Text(type.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (canSubmit) onConfirm(name, formula, selectedVisual, isMutable) },
                enabled = canSubmit
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun StyleChip(
    label: String,
    isSelected: Boolean,
    isEnabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        !isEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .then(if (isEnabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor,
            textAlign = TextAlign.Center
        )
    }
}
