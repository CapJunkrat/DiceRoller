package com.example.diceroller.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.diceroller.data.DiceStyle

private val ALL_CONFIGURABLE_DICE = listOf(4, 6, 8, 10, 12, 20, 100)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDonate: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val visibleDice by viewModel.visibleDice.collectAsState()
    val isCustomVisible by viewModel.isCustomDiceVisible.collectAsState()
    val isSystemHapticsEnabled by viewModel.isSystemHapticsEnabled.collectAsState()
    val currentStyle by viewModel.diceStyle.collectAsState()
    
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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
    ) {
        paddingValues ->
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

            // --- Visual Style Section (Horizontal) ---
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

            // --- Visible Dice Section ---
            Text(
                text = "Visible Dice",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            ALL_CONFIGURABLE_DICE.forEach { face ->
                DiceVisibilityRow(
                    label = "D$face",
                    isVisible = visibleDice.contains(face),
                    onCheckedChange = { isChecked ->
                        viewModel.onDiceVisibilityChanged(face, isChecked)
                    }
                )
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            DiceVisibilityRow(
                label = "Custom Formula",
                isVisible = isCustomVisible,
                onCheckedChange = { isChecked ->
                    viewModel.onCustomDiceVisibilityChanged(isChecked)
                }
            )

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

@Composable
private fun DiceVisibilityRow(
    label: String,
    isVisible: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isVisible) } // Make whole row clickable
            .padding(horizontal = 16.dp, vertical = 4.dp), // Reduced vertical padding
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.weight(1f))
        Switch(
            checked = isVisible,
            onCheckedChange = onCheckedChange
        )
    }
}
