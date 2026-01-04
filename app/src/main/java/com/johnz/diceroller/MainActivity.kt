package com.johnz.diceroller

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.johnz.diceroller.data.DiceStyle
import com.johnz.diceroller.data.db.ActionCard
import com.johnz.diceroller.data.db.GameSession
import com.johnz.diceroller.data.db.RollMode
import com.johnz.diceroller.ui.settings.SettingsScreen
import com.johnz.diceroller.ui.theme.DiceRollerTheme
import com.google.android.gms.ads.MobileAds
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// --- Cartoon Color Palette ---
object CartoonColors {
    val Red = Color(0xFFFF6B6B)
    val Blue = Color(0xFF4ECDC4) // Used for D6, now ADV
    val Yellow = Color(0xFFFFD93D)
    val Purple = Color(0xFF6C5CE7)
    val Green = Color(0xFF95E1D3)
    val Orange = Color(0xFFFF8E71)
    val Pink = Color(0xFFA29BFE)
    val TrueBlue = Color(0xFF54A0FF) // New Blue for STD
    val Outline = Color(0xFF2D3436) // Dark Charcoal for borders
    val Shadow = Color(0xFF000000).copy(alpha = 0.2f)
}

// Custom History Icon (since it's not in core icons)
val HistoryIcon: ImageVector = ImageVector.Builder(
    name = "History",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(13.0f, 3.0f)
        curveTo(8.03f, 3.0f, 4.0f, 7.03f, 4.0f, 12.0f)
        horizontalLineTo(1.0f)
        lineToRelative(3.89f, 3.89f)
        lineToRelative(0.07f, 0.14f)
        lineTo(9.0f, 12.0f)
        horizontalLineTo(6.0f)
        curveToRelative(0.0f, -3.87f, 3.13f, -7.0f, 7.0f, -7.0f)
        reflectiveCurveToRelative(7.0f, 3.13f, 7.0f, 7.0f)
        reflectiveCurveToRelative(-3.13f, 7.0f, -7.0f, 7.0f)
        curveToRelative(-1.93f, 0.0f, -3.68f, -0.79f, -4.94f, -2.06f)
        lineToRelative(-1.42f, 1.42f)
        curveTo(8.27f, 19.99f, 10.51f, 21.0f, 13.0f, 21.0f)
        curveToRelative(4.97f, 0.0f, 9.0f, -4.03f, 9.0f, -9.0f)
        reflectiveCurveTo(17.97f, 3.0f, 13.0f, 3.0f)
        close()
        moveTo(12.0f, 8.0f)
        verticalLineToRelative(5.0f)
        lineToRelative(4.28f, 2.54f)
        lineToRelative(0.72f, -1.21f)
        lineToRelative(-3.5f, -2.08f)
        verticalLineTo(8.0f)
        horizontalLineTo(12.0f)
        close()
    }
}.build()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize AdMob
        MobileAds.initialize(this) {}
        
        setContent {
            DiceRollerTheme {
                DiceAppWithNavigation()
            }
        }
    }
}

// Sound Manager Class
class SoundManager(context: Context) {
    private val soundPool: SoundPool
    private val rollSoundId: Int
    private val winSoundId: Int
    private val loseSoundId: Int

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        rollSoundId = soundPool.load(context, R.raw.dice_roll, 1)
        winSoundId = soundPool.load(context, R.raw.win, 1) 
        loseSoundId = soundPool.load(context, R.raw.lose, 1)
    }

    fun playRollSound() {
        if (rollSoundId != 0) {
            val pitch = Random.nextFloat() * 0.2f + 0.9f
            soundPool.play(rollSoundId, 1f, 1f, 0, 0, pitch)
        }
    }

    fun playWinSound() {
        if (winSoundId != 0) {
            soundPool.play(winSoundId, 1f, 1f, 0, 0, 1f)
        }
    }

    fun playLoseSound() {
        if (loseSoundId != 0) {
            soundPool.play(loseSoundId, 1f, 1f, 0, 0, 1f)
        }
    }

    fun release() {
        soundPool.release()
    }
}

@Composable
fun DiceAppWithNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val viewModel: DiceViewModel = viewModel()
    val soundManager = remember { SoundManager(context) }
    
    DisposableEffect(Unit) {
        onDispose {
            soundManager.release()
        }
    }

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            DiceScreen(
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToHistory = { navController.navigate("history") },
                soundManager = soundManager,
                viewModel = viewModel
            )
        }
        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDonate = { navController.navigate("donate") }
            )
        }
        composable("donate") {
            DonateScreen(
                onNavigateBack = { navController.popBackStack() },
                onWatchAd = {
                    Toast.makeText(context, "To be added", Toast.LENGTH_SHORT).show()
                }
            )
        }
        composable("history") {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSessions = { navController.navigate("sessions") },
                viewModel = viewModel
            )
        }
        composable("sessions") {
            SessionsScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = viewModel
            )
        }
    }
}

fun saveImageToGallery(context: Context, resourceId: Int, fileName: String) {
    try {
        val bitmap = BitmapFactory.decodeResource(context.resources, resourceId)
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.WIDTH, bitmap.width)
            put(MediaStore.Images.Media.HEIGHT, bitmap.height)
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            context.contentResolver.openOutputStream(it).use { stream ->
                if (stream != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    Toast.makeText(context, "Saved to Gallery", Toast.LENGTH_SHORT).show()
                }
            }
        } ?: run {
            Toast.makeText(context, "Failed to save", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error saving image", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonateScreen(onNavigateBack: () -> Unit, onWatchAd: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Donate") },
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Support the Developer",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
            
            Text(
                text = "If you enjoy using this app, please consider supporting. Thank you!",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            // Watch Ad Button
            OutlinedButton(
                onClick = onWatchAd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Watch Ad")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Watch Ad to Support (Free)")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiceScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    soundManager: SoundManager,
    viewModel: DiceViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val view = LocalView.current

    // Trigger Sound Effect when rollTrigger changes
    LaunchedEffect(uiState.rollTrigger) {
        if (uiState.rollTrigger > 0) {
            soundManager.playRollSound()
        }
    }

    // Listen for Game Events (Roll Finished)
    LaunchedEffect(Unit) {
        viewModel.gameEvents.collect { event ->
            if (event is GameEvent.RollFinished) {
                if (event.isNat20) {
                    soundManager.playWinSound()
                } else if (event.isNat1) {
                    soundManager.playLoseSound()
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFFF7F9FC),
        topBar = {
            TopAppBar(
                title = {
                    uiState.activeSession?.let { session ->
                        Text(
                            text = "Playing: ${session.name}",
                            style = MaterialTheme.typography.titleMedium,
                            color = CartoonColors.Outline
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Settings",
                            tint = CartoonColors.Outline
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            imageVector = HistoryIcon,
                            contentDescription = "History",
                            tint = CartoonColors.Outline
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            
            ExplosionEffect(trigger = uiState.rollTrigger)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Clickable Area Container
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!uiState.isRolling) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                } else {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                }
                                viewModel.rollDice()
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    // Display selected action card or fallback
                    val currentCard = uiState.selectedActionCard
                    if (currentCard != null) {
                        DiceDisplay(uiState = uiState, card = currentCard)
                    }

                    Spacer(modifier = Modifier.weight(1f))
                }

                // Non-clickable Control Area
                DiceSelector(
                    uiState = uiState, 
                    onSelect = { viewModel.selectActionCard(it) }
                )

                // Controls Area
                val currentCard = uiState.selectedActionCard
                if (currentCard != null) {
                    if (currentCard.visualType == DiceType.CUSTOM) {
                        CustomFormulaInput(
                            value = uiState.customFormula,
                            onValueChange = { viewModel.updateCustomFormula(it) },
                            onDone = { viewModel.rollDice() }
                        )
                    } else if (currentCard.isMutable) {
                        InteractiveDiceControls(
                            diceCount = uiState.customDiceCount,
                            modifier = uiState.customModifier,
                            onCountChange = { viewModel.changeCustomDiceCount(it) },
                            onModifierChange = { viewModel.changeCustomModifier(it) }
                        )
                    } else {
                        // Immutable Action Cards (Custom) show Adv/Dis
                        ActionCardControls(
                            currentMode = uiState.selectedRollMode,
                            onModeSelect = { viewModel.selectRollMode(it) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun ActionCardControls(
    currentMode: RollMode,
    onModeSelect: (RollMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Disadvantage
        Button(
            onClick = { onModeSelect(RollMode.DISADVANTAGE) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (currentMode == RollMode.DISADVANTAGE) CartoonColors.Red else Color.Transparent,
                contentColor = if (currentMode == RollMode.DISADVANTAGE) Color.White else CartoonColors.Red
            ),
            border = if (currentMode != RollMode.DISADVANTAGE) androidx.compose.foundation.BorderStroke(1.dp, CartoonColors.Red) else null
        ) {
            Text("DIS")
        }
        
        // Standard
        Button(
            onClick = { onModeSelect(RollMode.NORMAL) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (currentMode == RollMode.NORMAL) CartoonColors.TrueBlue else Color.Transparent,
                contentColor = if (currentMode == RollMode.NORMAL) Color.White else CartoonColors.TrueBlue
            ),
            border = if (currentMode != RollMode.NORMAL) androidx.compose.foundation.BorderStroke(1.dp, CartoonColors.TrueBlue) else null
        ) {
            Text("STD")
        }

        // Advantage
        Button(
            onClick = { onModeSelect(RollMode.ADVANTAGE) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (currentMode == RollMode.ADVANTAGE) CartoonColors.Blue else Color.Transparent,
                contentColor = if (currentMode == RollMode.ADVANTAGE) Color.White else CartoonColors.Blue
            ),
            border = if (currentMode != RollMode.ADVANTAGE) androidx.compose.foundation.BorderStroke(1.dp, CartoonColors.Blue) else null
        ) {
            Text("ADV")
        }
    }
}

@Composable
fun DiceDisplay(uiState: DiceUiState, card: ActionCard) {
    val baseColor = when(card.visualType) {
        DiceType.D4 -> CartoonColors.Red
        DiceType.D6 -> CartoonColors.Blue
        DiceType.D8 -> CartoonColors.Green
        DiceType.D10 -> CartoonColors.Purple
        DiceType.D12 -> CartoonColors.Orange
        DiceType.D20 -> CartoonColors.Pink
        DiceType.D100 -> CartoonColors.Pink
        DiceType.CUSTOM -> Color.LightGray
    }

    val scale by animateFloatAsState(
        targetValue = if (uiState.isRolling) 0.9f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    // Animation Transitions for Adv/Dis
    val transition = updateTransition(targetState = uiState.selectedRollMode, label = "RollModeTransition")
    
    val separationOffset by transition.animateDp(
        transitionSpec = { tween(durationMillis = 400, easing = FastOutSlowInEasing) },
        label = "Separation"
    ) { mode ->
        if (mode == RollMode.NORMAL) 0.dp else 80.dp // Moves left and right
    }

    val modeScale by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 400, easing = FastOutSlowInEasing) },
        label = "Scale"
    ) { mode ->
        if (mode == RollMode.NORMAL) 1.0f else 0.65f // Shrink to fit two dice
    }

    val secondDieAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 400, easing = LinearEasing) },
        label = "Alpha"
    ) { mode ->
        if (mode == RollMode.NORMAL) 0f else 1f
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(280.dp) // Main container size
    ) {
        // --- Dice 1 (Primary: Moves Left in Adv/Dis) ---
        Box(
            modifier = Modifier
                .offset(x = -separationOffset)
                .scale(scale * modeScale) 
                .size(280.dp), // Size of individual die render space
            contentAlignment = Alignment.Center
        ) {
            DiceRenderUnit(
                uiState = uiState,
                card = card,
                color = baseColor,
                displayText = uiState.displayedResult,
                // Only show shadow for primary die if in Normal mode, or always? 
                // Let's keep shadows consistent.
            )
        }

        // --- Dice 2 (Secondary: Moves Right in Adv/Dis) ---
        if (secondDieAlpha > 0f) {
            Box(
                modifier = Modifier
                    .offset(x = separationOffset)
                    .scale(scale * modeScale) 
                    .alpha(secondDieAlpha)
                    .size(280.dp),
                contentAlignment = Alignment.Center
            ) {
                DiceRenderUnit(
                    uiState = uiState,
                    card = card,
                    color = baseColor,
                    displayText = uiState.displayedResult2
                )
            }
        }
    }

    if (!uiState.isRolling) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 16.dp)) {
            // Show Card Name if it's a custom card (or system card name)
            Text(
                text = card.name,
                style = MaterialTheme.typography.headlineSmall,
                color = CartoonColors.Outline,
                fontWeight = FontWeight.Bold
            )
            if (uiState.breakdown.isNotEmpty()) {
                Text(
                    text = uiState.breakdown,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun DiceRenderUnit(
    uiState: DiceUiState,
    card: ActionCard,
    color: Color,
    displayText: String
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        DiceShapeRenderer(
            style = uiState.diceStyle,
            type = card.visualType,
            color = color
        )

        // Adjust text offset logic based on dice shape
        val textOffset = when (card.visualType) {
            DiceType.D4 -> Modifier.offset(x = 24.dp, y = 16.dp)
            DiceType.D6 -> Modifier.offset(x = (-12).dp, y = 12.dp)
            DiceType.D8 -> Modifier.offset(y = (-4).dp)
            DiceType.D10 -> Modifier.offset(y = (-12).dp)
            else -> Modifier
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = textOffset
        ) {
            // Shadow text
            if (uiState.diceStyle != DiceStyle.FLAT_2D) {
                Text(
                    text = displayText,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (uiState.diceStyle == DiceStyle.REALISTIC_3D) Color.Black.copy(alpha = 0.3f) else CartoonColors.Outline.copy(alpha = 0.2f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.displayLarge.copy(
                        drawStyle = Stroke(width = 12f, join = StrokeJoin.Round)
                    ),
                    modifier = Modifier.offset(x = 4.dp, y = 4.dp)
                )
            }

            Text(
                text = displayText,
                fontSize = 64.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun DiceShapeRenderer(style: DiceStyle, type: DiceType, color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2
        val radius = size.minDimension / 2 * if(type == DiceType.D4) 0.96f else 0.8f 

        val pTop: Offset
        val pLeft: Offset
        val pRight: Offset
        val pBottom: Offset
        
        if (type == DiceType.D4) {
            pTop = Offset(cx + radius * 0.05f, cy - radius * 0.9f)
            pLeft = Offset(cx - radius * 0.75f, cy + radius * 0.4f)
            pRight = Offset(cx + radius * 0.85f, cy + radius * 0.3f)
            pBottom = Offset(cx - radius * 0.3f, cy + radius * 0.85f)
        } else {
            pTop = Offset.Zero
            pLeft = Offset.Zero
            pRight = Offset.Zero
            pBottom = Offset.Zero
        }

        // --- 1. FLAT 2D STYLE ---
        if (style == DiceStyle.FLAT_2D) {
            val path = Path()
            when (type) {
                DiceType.D4 -> {
                    path.moveTo(pTop.x, pTop.y)
                    path.lineTo(pRight.x, pRight.y)
                    path.lineTo(pBottom.x, pBottom.y)
                    path.lineTo(pLeft.x, pLeft.y)
                    path.close()
                    drawPath(path, color = color)
                }
                DiceType.D6, DiceType.CUSTOM -> {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(cx - radius, cy - radius),
                        size = Size(radius * 2, radius * 2),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f, 20f)
                    )
                }
                DiceType.D100, DiceType.D10 -> {
                    // Kite
                    path.moveTo(cx, cy - radius)
                    path.lineTo(cx + radius * 0.8f, cy)
                    path.lineTo(cx, cy + radius)
                    path.lineTo(cx - radius * 0.8f, cy)
                    path.close()
                    drawPath(path, color = color)
                }
                DiceType.D12, DiceType.D20, DiceType.D8 -> {
                    // Polygon approximation (Circle for simplicity in flat)
                    drawCircle(color, radius, center)
                }
            }
            return@Canvas
        }

        // --- 2. CARTOON 2.5D STYLE (Current) ---
        if (style == DiceStyle.CARTOON_25D) {
            val outlineStroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val innerLineStroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            val shadowOffset = Offset(8.dp.toPx(), 8.dp.toPx())
            val gradientBrush = Brush.linearGradient(
                colors = listOf(color.copy(alpha = 0.85f), color),
                start = Offset(cx - radius, cy - radius),
                end = Offset(cx + radius, cy + radius)
            )

            fun drawBase(path: Path) {
                translate(left = shadowOffset.x, top = shadowOffset.y) {
                    drawPath(path, CartoonColors.Shadow, style = Fill)
                }
                drawPath(path, CartoonColors.Outline, style = outlineStroke)
                drawPath(path, gradientBrush, style = Fill)
            }

            fun drawInnerLines(path: Path) {
                 drawPath(path = path, color = CartoonColors.Outline.copy(alpha = 0.3f), style = innerLineStroke)
            }

            val path = Path()
            // val innerPath = Path() // Removed unused variable

            when (type) {
                DiceType.D4 -> {
                    val d4Radius = radius * 1.2f
                    val pTop25 = Offset(cx + d4Radius * 0.05f, cy - d4Radius * 0.9f) 
                    val pLeft25 = Offset(cx - d4Radius * 0.75f, cy + d4Radius * 0.4f)
                    val pRight25 = Offset(cx + d4Radius * 0.85f, cy + d4Radius * 0.3f)
                    val pBottom25 = Offset(cx - d4Radius * 0.3f, cy + d4Radius * 0.85f) 

                    path.moveTo(pTop25.x, pTop25.y)
                    path.lineTo(pRight25.x, pRight25.y)
                    path.lineTo(pBottom25.x, pBottom25.y)
                    path.lineTo(pLeft25.x, pLeft25.y)
                    path.close()
                    
                    val d4GradientBrush = Brush.linearGradient(
                        colors = listOf(color.copy(alpha = 0.85f), color),
                        start = Offset(cx - d4Radius, cy - d4Radius),
                        end = Offset(cx + d4Radius, cy + d4Radius)
                    )
                    translate(left = shadowOffset.x, top = shadowOffset.y) {
                        drawPath(path = path, color = CartoonColors.Shadow, style = Fill)
                    }
                    drawPath(path = path, color = CartoonColors.Outline, style = outlineStroke)
                    drawPath(path = path, brush = d4GradientBrush, style = Fill)

                    val rightFace = Path().apply {
                        moveTo(pTop25.x, pTop25.y)
                        lineTo(pRight25.x, pRight25.y)
                        lineTo(pBottom25.x, pBottom25.y)
                        close()
                    }
                    drawPath(rightFace, color = color) 

                    val leftFace = Path().apply {
                        moveTo(pTop25.x, pTop25.y)
                        lineTo(pBottom25.x, pBottom25.y)
                        lineTo(pLeft25.x, pLeft25.y)
                        close()
                    }
                    drawPath(leftFace, color = color.copy(alpha = 0.7f))

                    val inner = Path().apply {
                        moveTo(pTop25.x, pTop25.y)
                        lineTo(pBottom25.x, pBottom25.y)
                    }
                    drawInnerLines(inner)
                    
                    val highlightPath = Path().apply {
                        moveTo(pTop25.x + 15f, pTop25.y + 40f)
                        lineTo(pRight25.x - 25f, pRight25.y - 10f)
                        lineTo(pRight25.x - 45f, pRight25.y + 15f)
                        lineTo(pTop25.x + 15f, pTop25.y + 80f)
                        close()
                    }
                    drawPath(highlightPath, color = Color.White.copy(alpha = 0.3f))
                }
                DiceType.D6 -> {
                    val faceSize = radius * 1.3f
                    val depth = radius * 0.5f
                    val fl = cx - faceSize / 2 - depth / 4
                    val ft = cy - faceSize / 2 + depth / 4
                    val fr = fl + faceSize
                    val fb = ft + faceSize
                    val bl = fl + depth
                    val bt = ft - depth
                    val br = fr + depth
                    
                    path.moveTo(fl, ft)
                    path.lineTo(bl, bt)
                    path.lineTo(br, bt)
                    path.lineTo(br, fb - depth)
                    path.lineTo(fr, fb)
                    path.lineTo(fl, fb)
                    path.close()
                    
                    drawBase(path)

                    val topFace = Path().apply { moveTo(fl, ft); lineTo(bl, bt); lineTo(br, bt); lineTo(fr, ft); close() }
                    drawPath(topFace, color = Color.White.copy(alpha = 0.2f))
                    val rightFace = Path().apply { moveTo(fr, ft); lineTo(br, bt); lineTo(br, fb - depth); lineTo(fr, fb); close() }
                    drawPath(rightFace, color = Color.Black.copy(alpha = 0.1f))

                    val inner = Path().apply { moveTo(fl, ft); lineTo(fr, ft); lineTo(fr, fb); moveTo(fr, ft); lineTo(br, bt) }
                    drawInnerLines(inner)
                }

                DiceType.D8 -> {
                    val top = Offset(cx, cy - radius)
                    val bottom = Offset(cx, cy + radius * 0.8f) 
                    val left = Offset(cx - radius, cy + radius * 0.1f) 
                    val right = Offset(cx + radius, cy + radius * 0.1f) 
                    val innerLeft = Offset(cx - radius * 0.75f, cy + radius * 0.4f)
                    val innerRight = Offset(cx + radius * 0.75f, cy + radius * 0.4f)

                    path.moveTo(top.x, top.y)
                    path.lineTo(left.x, left.y)
                    path.lineTo(innerLeft.x, innerLeft.y) 
                    path.lineTo(bottom.x, bottom.y)
                    path.lineTo(innerRight.x, innerRight.y) 
                    path.lineTo(right.x, right.y)
                    path.close()
                    
                    drawBase(path)

                    val leftSide = Path().apply { moveTo(top.x, top.y); lineTo(left.x, left.y); lineTo(innerLeft.x, innerLeft.y); close() }
                    drawPath(leftSide, color = Color.Black.copy(alpha = 0.1f))
                    val rightSide = Path().apply { moveTo(top.x, top.y); lineTo(right.x, right.y); lineTo(innerRight.x, innerRight.y); close() }
                    drawPath(rightSide, color = Color.Black.copy(alpha = 0.2f))
                    val frontUpper = Path().apply { moveTo(top.x, top.y); lineTo(innerRight.x, innerRight.y); lineTo(innerLeft.x, innerLeft.y); close() }
                    drawPath(frontUpper, color = Color.White.copy(alpha = 0.15f))
                    val frontLower = Path().apply { moveTo(bottom.x, bottom.y); lineTo(innerRight.x, innerRight.y); lineTo(innerLeft.x, innerLeft.y); close() }
                    drawPath(frontLower, color = Color.Black.copy(alpha = 0.1f))

                    val inner = Path().apply {
                        moveTo(top.x, top.y); lineTo(innerLeft.x, innerLeft.y)
                        moveTo(top.x, top.y); lineTo(innerRight.x, innerRight.y)
                        moveTo(bottom.x, bottom.y); lineTo(innerLeft.x, innerLeft.y)
                        moveTo(bottom.x, bottom.y); lineTo(innerRight.x, innerRight.y)
                        moveTo(innerLeft.x, innerLeft.y); lineTo(innerRight.x, innerRight.y)
                        moveTo(left.x, left.y); lineTo(innerLeft.x, innerLeft.y)
                        moveTo(right.x, right.y); lineTo(innerRight.x, innerRight.y)
                    }
                    drawInnerLines(inner)
                }
                DiceType.D10->{
                    val top = Offset(cx, cy - radius)
                    val bottom = Offset(cx, cy + radius)
                    val innerY = cy + radius * 0.2f
                    val innerX = radius * 0.55f 
                    val innerLeft = Offset(cx - innerX, innerY)
                    val innerRight = Offset(cx + innerX, innerY)
                    val centerJunction = Offset(cx, cy + radius * 0.38f)
                    val shoulderY = cy - radius * 0.25f
                    val shoulderXOffset = radius * 0.95f
                    val shoulderLeft = Offset(cx - shoulderXOffset, shoulderY)
                    val shoulderRight = Offset(cx + shoulderXOffset, shoulderY)
                    val waistY = cy + radius * 0.28f
                    val waistXOffset = radius * 0.88f
                    val outerLeft = Offset(cx - waistXOffset, waistY)
                    val outerRight = Offset(cx + waistXOffset, waistY)

                    path.moveTo(top.x, top.y)
                    path.lineTo(shoulderLeft.x, shoulderLeft.y)
                    path.lineTo(outerLeft.x, outerLeft.y)
                    path.lineTo(bottom.x, bottom.y)
                    path.lineTo(outerRight.x, outerRight.y)
                    path.lineTo(shoulderRight.x, shoulderRight.y)
                    path.close()
                    drawBase(path)

                    val centerFace = Path().apply { moveTo(top.x, top.y); lineTo(innerRight.x, innerRight.y); lineTo(centerJunction.x, centerJunction.y); lineTo(innerLeft.x, innerLeft.y); close() }
                    drawPath(centerFace, color = Color.White.copy(alpha = 0.2f))
                    val leftUpperFace = Path().apply { moveTo(top.x, top.y); lineTo(shoulderLeft.x, shoulderLeft.y); lineTo(outerLeft.x, outerLeft.y); lineTo(innerLeft.x, innerLeft.y); close() }
                    drawPath(leftUpperFace, color = Color.Black.copy(alpha = 0.15f))
                    val rightUpperFace = Path().apply { moveTo(top.x, top.y); lineTo(shoulderRight.x, shoulderRight.y); lineTo(outerRight.x, outerRight.y); lineTo(innerRight.x, innerRight.y); close() }
                    drawPath(rightUpperFace, color = Color.Black.copy(alpha = 0.25f))
                    val leftBottomFace = Path().apply { moveTo(centerJunction.x, centerJunction.y); lineTo(innerLeft.x, innerLeft.y); lineTo(outerLeft.x, outerLeft.y); lineTo(bottom.x, bottom.y); close() }
                    drawPath(leftBottomFace, color = Color.Black.copy(alpha = 0.05f))
                    val rightBottomFace = Path().apply { moveTo(centerJunction.x, centerJunction.y); lineTo(innerRight.x, innerRight.y); lineTo(outerRight.x, outerRight.y); lineTo(bottom.x, bottom.y); close() }
                    drawPath(rightBottomFace, color = Color.Black.copy(alpha = 0.3f))

                    val innerLines = Path().apply {
                        moveTo(top.x, top.y); lineTo(innerLeft.x, innerLeft.y)
                        moveTo(top.x, top.y); lineTo(innerRight.x, innerRight.y)
                        moveTo(innerLeft.x, innerLeft.y); lineTo(centerJunction.x, centerJunction.y)
                        moveTo(innerRight.x, innerRight.y); lineTo(centerJunction.x, centerJunction.y)
                        moveTo(innerLeft.x, innerLeft.y); lineTo(outerLeft.x, outerLeft.y)
                        moveTo(innerRight.x, innerRight.y); lineTo(outerRight.x, outerRight.y)
                        moveTo(centerJunction.x, centerJunction.y); lineTo(bottom.x, bottom.y)
                    }
                    drawInnerLines(innerLines)
                }
                DiceType.D12 -> {
                    val innerRadius = radius * 0.6f 
                    val outerRadius = radius         
                    val startAngle = -90f
                    val segmentAngle = 72f 
                    val innerPoints = (0 until 5).map { i ->
                        val theta = (startAngle + i * segmentAngle) * (Math.PI / 180f)
                        Offset(cx + innerRadius * cos(theta).toFloat(), cy + innerRadius * sin(theta).toFloat())
                    }
                    val outerSpokePoints = (0 until 5).map { i ->
                        val theta = (startAngle + i * segmentAngle) * (Math.PI / 180f)
                        Offset(cx + outerRadius * cos(theta).toFloat(), cy + outerRadius * sin(theta).toFloat())
                    }
                    val outerMidPoints = (0 until 5).map { i ->
                        val theta = (startAngle + segmentAngle/2 + i * segmentAngle) * (Math.PI / 180f)
                        Offset(cx + outerRadius * cos(theta).toFloat(), cy + outerRadius * sin(theta).toFloat())
                    }

                    path.moveTo(outerSpokePoints[0].x, outerSpokePoints[0].y)
                    for (i in 0 until 5) {
                        path.lineTo(outerMidPoints[i].x, outerMidPoints[i].y)
                        val nextIndex = (i + 1) % 5
                        path.lineTo(outerSpokePoints[nextIndex].x, outerSpokePoints[nextIndex].y)
                    }
                    path.close()
                    drawBase(path)

                    val centerPath = Path().apply { moveTo(innerPoints[0].x, innerPoints[0].y); for (i in 1 until 5) lineTo(innerPoints[i].x, innerPoints[i].y); close() }
                    drawPath(centerPath, color = Color.White.copy(alpha = 0.05f)) 
                    val face0 = Path().apply { moveTo(innerPoints[0].x, innerPoints[0].y); lineTo(outerSpokePoints[0].x, outerSpokePoints[0].y); lineTo(outerMidPoints[0].x, outerMidPoints[0].y); lineTo(outerSpokePoints[1].x, outerSpokePoints[1].y); lineTo(innerPoints[1].x, innerPoints[1].y); close() }
                    drawPath(face0, color = Color.Black.copy(alpha = 0.1f))
                    val face1 = Path().apply { moveTo(innerPoints[1].x, innerPoints[1].y); lineTo(outerSpokePoints[1].x, outerSpokePoints[1].y); lineTo(outerMidPoints[1].x, outerMidPoints[1].y); lineTo(outerSpokePoints[2].x, outerSpokePoints[2].y); lineTo(innerPoints[2].x, innerPoints[2].y); close() }
                    drawPath(face1, color = Color.Black.copy(alpha = 0.25f))
                    val face2 = Path().apply { moveTo(innerPoints[2].x, innerPoints[2].y); lineTo(outerSpokePoints[2].x, outerSpokePoints[2].y); lineTo(outerMidPoints[2].x, outerMidPoints[2].y); lineTo(outerSpokePoints[3].x, outerSpokePoints[3].y); lineTo(innerPoints[3].x, innerPoints[3].y); close() }
                    drawPath(face2, color = Color.Black.copy(alpha = 0.4f))
                    val face3 = Path().apply { moveTo(innerPoints[3].x, innerPoints[3].y); lineTo(outerSpokePoints[3].x, outerSpokePoints[3].y); lineTo(outerMidPoints[3].x, outerMidPoints[3].y); lineTo(outerSpokePoints[4].x, outerSpokePoints[4].y); lineTo(innerPoints[4].x, innerPoints[4].y); close() }
                    drawPath(face3, color = Color.Black.copy(alpha = 0.25f))
                    val face4 = Path().apply { moveTo(innerPoints[4].x, innerPoints[4].y); lineTo(outerSpokePoints[4].x, outerSpokePoints[4].y); lineTo(outerMidPoints[4].x, outerMidPoints[4].y); lineTo(outerSpokePoints[0].x, outerSpokePoints[0].y); lineTo(innerPoints[0].x, innerPoints[0].y); close() }
                    drawPath(face4, color = Color.White.copy(alpha = 0.15f))

                    val innerLines = Path().apply {
                        moveTo(innerPoints[0].x, innerPoints[0].y); for (i in 1 until 5) lineTo(innerPoints[i].x, innerPoints[i].y); close()
                        for (i in 0 until 5) { moveTo(innerPoints[i].x, innerPoints[i].y); lineTo(outerSpokePoints[i].x, outerSpokePoints[i].y) }
                    }
                    drawInnerLines(innerLines)
                }
                DiceType.D20 -> {
                    val innerRadius = radius * 0.68f
                    val outerRadius = radius
                    val outerPoints = (0 until 6).map { i ->
                        val theta = (-90 + i * 60) * (Math.PI / 180f)
                        Offset(cx + outerRadius * cos(theta).toFloat(), cy + outerRadius * sin(theta).toFloat())
                    }
                    val innerPoints = listOf(0, 2, 4).map { i ->
                        val theta = (-90 + i * 60) * (Math.PI / 180f)
                        Offset(cx + innerRadius * cos(theta).toFloat(), cy + innerRadius * sin(theta).toFloat())
                    }

                    path.moveTo(outerPoints[0].x, outerPoints[0].y)
                    for (i in 1 until 6) { path.lineTo(outerPoints[i].x, outerPoints[i].y) }
                    path.close()
                    drawBase(path)

                    val centerFace = Path().apply { moveTo(innerPoints[0].x, innerPoints[0].y); lineTo(innerPoints[1].x, innerPoints[1].y); lineTo(innerPoints[2].x, innerPoints[2].y); close() }
                    drawPath(centerFace, color = Color.White.copy(alpha = 0.15f))
                    val bottomFace = Path().apply { moveTo(innerPoints[1].x, innerPoints[1].y); lineTo(innerPoints[2].x, innerPoints[2].y); lineTo(outerPoints[3].x, outerPoints[3].y); close() }
                    drawPath(bottomFace, color = Color.White.copy(alpha = 0.15f))
                    val rightFace = Path().apply { moveTo(innerPoints[0].x, innerPoints[0].y); lineTo(innerPoints[1].x, innerPoints[1].y); lineTo(outerPoints[1].x, outerPoints[1].y); close() }
                    drawPath(rightFace, color = Color.White.copy(alpha = 0.15f))
                    val leftFace = Path().apply { moveTo(innerPoints[0].x, innerPoints[0].y); lineTo(innerPoints[2].x, innerPoints[2].y); lineTo(outerPoints[5].x, outerPoints[5].y); close() }
                    drawPath(leftFace, color = Color.White.copy(alpha = 0.15f))

                    val innerLines = Path().apply {
                        moveTo(innerPoints[0].x, innerPoints[0].y); lineTo(innerPoints[1].x, innerPoints[1].y); lineTo(innerPoints[2].x, innerPoints[2].y); lineTo(innerPoints[0].x, innerPoints[0].y)
                        moveTo(innerPoints[0].x, innerPoints[0].y); lineTo(outerPoints[0].x, outerPoints[0].y)
                        moveTo(innerPoints[0].x, innerPoints[0].y); lineTo(outerPoints[1].x, outerPoints[1].y)
                        moveTo(innerPoints[0].x, innerPoints[0].y); lineTo(outerPoints[5].x, outerPoints[5].y)
                        moveTo(innerPoints[1].x, innerPoints[1].y); lineTo(outerPoints[1].x, outerPoints[1].y)
                        moveTo(innerPoints[1].x, innerPoints[1].y); lineTo(outerPoints[2].x, outerPoints[2].y)
                        moveTo(innerPoints[1].x, innerPoints[1].y); lineTo(outerPoints[3].x, outerPoints[3].y)
                        moveTo(innerPoints[2].x, innerPoints[2].y); lineTo(outerPoints[3].x, outerPoints[3].y)
                        moveTo(innerPoints[2].x, innerPoints[2].y); lineTo(outerPoints[4].x, outerPoints[4].y)
                        moveTo(innerPoints[2].x, innerPoints[2].y); lineTo(outerPoints[5].x, outerPoints[5].y)
                    }
                    drawInnerLines(innerLines)
                }
                DiceType.D100 -> {
                    val segments = 24 
                    val innerRadius = radius * 0.78f 
                    val outerRadius = radius
                    val outerPoints = (0 until segments).map { i ->
                        val theta = (-Math.PI / 2 + i * 2 * Math.PI / segments)
                        Offset(cx + outerRadius * cos(theta).toFloat(), cy + outerRadius * sin(theta).toFloat())
                    }
                    val innerPoints = (0 until segments).map { i ->
                        val theta = (-Math.PI / 2 + i * 2 * Math.PI / segments)
                        Offset(cx + innerRadius * cos(theta).toFloat(), cy + innerRadius * sin(theta).toFloat())
                    }

                    path.moveTo(outerPoints[0].x, outerPoints[0].y)
                    for (i in 1 until segments) { path.lineTo(outerPoints[i].x, outerPoints[i].y) }
                    path.close()
                    drawBase(path)

                    val bottomShadow = Path().apply {
                        val startIdx = (segments * 0.35).toInt(); val endIdx = (segments * 0.65).toInt()
                        moveTo(outerPoints[startIdx].x, outerPoints[startIdx].y)
                        for (i in startIdx + 1..endIdx) lineTo(outerPoints[i].x, outerPoints[i].y)
                        lineTo(innerPoints[endIdx].x, innerPoints[endIdx].y)
                        for (i in endIdx - 1 downTo startIdx) lineTo(innerPoints[i].x, innerPoints[i].y)
                        close()
                    }
                    drawPath(bottomShadow, color = Color.Black.copy(alpha = 0.3f))

                    val sideShadowLeft = Path().apply {
                        val startIdx = (segments * 0.25).toInt(); val endIdx = (segments * 0.35).toInt()
                        moveTo(outerPoints[startIdx].x, outerPoints[startIdx].y)
                        for (i in startIdx + 1..endIdx) lineTo(outerPoints[i].x, outerPoints[i].y)
                        lineTo(innerPoints[endIdx].x, innerPoints[endIdx].y)
                        for (i in endIdx - 1 downTo startIdx) lineTo(innerPoints[i].x, innerPoints[i].y)
                        close()
                    }
                    val sideShadowRight = Path().apply {
                        val startIdx = (segments * 0.65).toInt(); val endIdx = (segments * 0.75).toInt()
                        moveTo(outerPoints[startIdx].x, outerPoints[startIdx].y)
                        for (i in startIdx + 1..endIdx) lineTo(outerPoints[i].x, outerPoints[i].y)
                        lineTo(innerPoints[endIdx].x, innerPoints[endIdx].y)
                        for (i in endIdx - 1 downTo startIdx) lineTo(innerPoints[i].x, innerPoints[i].y)
                        close()
                    }
                    drawPath(sideShadowLeft, color = Color.Black.copy(alpha = 0.15f))
                    drawPath(sideShadowRight, color = Color.Black.copy(alpha = 0.15f))

                    val innerLines = Path().apply {
                        moveTo(innerPoints[0].x, innerPoints[0].y); for (i in 1 until segments) lineTo(innerPoints[i].x, innerPoints[i].y); close()
                        for (i in 0 until segments) { moveTo(innerPoints[i].x, innerPoints[i].y); lineTo(outerPoints[i].x, outerPoints[i].y) }
                    }
                    drawInnerLines(innerLines)
                }
                DiceType.CUSTOM -> {
                    val points = 8 
                    val outerRadius = radius
                    val innerRadius = radius * 0.75f 
                    val vertices = (0 until points * 2).map { i ->
                        val isOuter = i % 2 == 0
                        val r = if (isOuter) outerRadius else innerRadius
                        val angleStep = 2 * Math.PI / (points * 2)
                        val theta = -Math.PI / 2 + i * angleStep
                        Offset(cx + r * cos(theta).toFloat(), cy + r * sin(theta).toFloat())
                    }

                    path.moveTo(vertices[0].x, vertices[0].y)
                    for (i in 1 until vertices.size) { path.lineTo(vertices[i].x, vertices[i].y) }
                    path.close()
                    drawBase(path) 

                    for (i in 0 until points) {
                        val vertexIndex = i * 2
                        val nextInnerIndex = (vertexIndex + 1) % vertices.size
                        val facetA = Path().apply { moveTo(cx, cy); lineTo(vertices[vertexIndex].x, vertices[vertexIndex].y); lineTo(vertices[nextInnerIndex].x, vertices[nextInnerIndex].y); close() }
                        val prevInnerIndex = (vertexIndex - 1 + vertices.size) % vertices.size
                        val facetB = Path().apply { moveTo(cx, cy); lineTo(vertices[prevInnerIndex].x, vertices[prevInnerIndex].y); lineTo(vertices[vertexIndex].x, vertices[vertexIndex].y); close() }

                        var alphaA = 0.05f; var alphaB = 0.2f
                        if (i in 3..5) { alphaA += 0.2f; alphaB += 0.2f } else if (i == 2 || i == 6) { alphaA += 0.1f; alphaB += 0.1f }
                        drawPath(facetA, color = Color.Black.copy(alpha = alphaA))
                        drawPath(facetB, color = Color.Black.copy(alpha = alphaB))
                    }

                    val innerLines = Path().apply {
                        for (i in 1 until vertices.size step 2) { moveTo(cx, cy); lineTo(vertices[i].x, vertices[i].y) }
                        for (i in 0 until vertices.size step 2) { moveTo(cx, cy); lineTo(vertices[i].x, vertices[i].y) }
                    }
                    drawInnerLines(innerLines)
                }
            }
            return@Canvas
        }

        if (style == DiceStyle.REALISTIC_3D) {
            drawCircle(color = Color.Black.copy(alpha = 0.2f), radius = radius * 1.1f, center = Offset(cx + 15f, cy + 25f))
            val radialGradient = Brush.radialGradient(colors = listOf(Color.White.copy(alpha = 0.8f), color, color.copy(alpha = 0.8f), Color.Black.copy(alpha = 0.6f)), center = Offset(cx - radius * 0.3f, cy - radius * 0.3f), radius = radius * 1.8f)
            when (type) {
                DiceType.D4 -> {
                    val path = Path().apply { moveTo(pTop.x, pTop.y); lineTo(pRight.x, pRight.y); lineTo(pBottom.x, pBottom.y); lineTo(pLeft.x, pLeft.y); close() }
                    drawPath(path, brush = radialGradient)
                    val edgePath = Path().apply { moveTo(pTop.x, pTop.y); lineTo(pBottom.x, pBottom.y) }
                    drawPath(edgePath, Color.White.copy(alpha=0.3f), style = Stroke(width = 2f))
                }
                else -> {
                    drawCircle(brush = radialGradient, radius = radius, center = center)
                }
            }
        }
    }
}

@Composable
fun ExplosionEffect(trigger: Long) {
    if (trigger == 0L) return
    val particles = remember(trigger) {
        (0..15).map { id ->
             Particle(id, Random.nextDouble(0.0, 2 * Math.PI), Random.nextFloat() * 10f + 5f, listOf(CartoonColors.Red, CartoonColors.Blue, CartoonColors.Yellow, CartoonColors.Green, CartoonColors.Purple).random(), Random.nextFloat() * 20f + 10f)
        }
    }
    val animatable = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { animatable.snapTo(0f); animatable.animateTo(1f, animationSpec = tween(600, easing = LinearOutSlowInEasing)) }
    val animProgress = animatable.value
    if (animProgress < 1f) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2; val cy = size.height / 2
            particles.forEach { p ->
                val distance = p.speed * animProgress * 500f
                val x = cx + distance * cos(p.angle).toFloat()
                val y = cy + distance * sin(p.angle).toFloat()
                drawCircle(color = p.color.copy(alpha = (1f - animProgress).coerceIn(0f, 1f)), radius = p.size * (1f - animProgress * 0.5f), center = Offset(x, y))
            }
        }
    }
}
data class Particle(val id: Int, val angle: Double, val speed: Float, val color: Color, val size: Float)

@Composable
fun DiceSelector(uiState: DiceUiState, onSelect: (ActionCard) -> Unit) {
    if (uiState.visibleActionCards.isEmpty()) return
    val listState = rememberLazyListState()
    LaunchedEffect(uiState.selectedActionCard) {
        val index = uiState.visibleActionCards.indexOfFirst { 
            it.id == uiState.selectedActionCard?.id && it.name == uiState.selectedActionCard?.name 
        }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    LazyRow(
        state = listState, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center, contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(uiState.visibleActionCards) { card ->
            val isSelected = uiState.selectedActionCard?.id == card.id && uiState.selectedActionCard?.name == card.name
            val backgroundColor by animateColorAsState(if (isSelected) CartoonColors.Outline else Color.Transparent, label = "bgColor")
            val contentColor by animateColorAsState(if (isSelected) Color.White else CartoonColors.Outline.copy(alpha = 0.5f), label = "contentColor")
            Box(
                modifier = Modifier.padding(horizontal = 4.dp).clip(CircleShape).background(backgroundColor).clickable { onSelect(card) }.padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = card.name, fontWeight = FontWeight.Bold, color = contentColor, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun CustomFormulaInput(value: String, onValueChange: (String) -> Unit, onDone: () -> Unit) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text("Formula (e.g. 2d20 + 5)") },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
        singleLine = true, shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CartoonColors.Outline, unfocusedBorderColor = CartoonColors.Outline.copy(alpha = 0.5f), focusedLabelColor = CartoonColors.Outline),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); onDone() })
    )
}

@Composable
fun InteractiveDiceControls(
    diceCount: Int,
    modifier: Int,
    onCountChange: (Int) -> Unit,
    onModifierChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dice Count Control
        ControlGroup(
            value = "${diceCount}d",
            onDecrement = { onCountChange(-1) },
            onIncrement = { onCountChange(1) }
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Modifier Control
        ControlGroup(
            value = if (modifier >= 0) "+$modifier" else "$modifier",
            onDecrement = { onModifierChange(-1) },
            onIncrement = { onModifierChange(1) }
        )
    }
}

@Composable
fun ControlGroup(
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(CartoonColors.Outline.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        // Decrement Button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .clickable(onClick = onDecrement),
            contentAlignment = Alignment.Center
        ) {
            // Replaced Icon with Text to avoid dependency issues with "Remove" icon
            Text(
                text = "-",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = CartoonColors.Outline,
                textAlign = TextAlign.Center
            )
        }

        // Value Display
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .widthIn(min = 60.dp)
                .padding(horizontal = 12.dp),
            textAlign = TextAlign.Center
        )

        // Increment Button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .clickable(onClick = onIncrement),
            contentAlignment = Alignment.Center
        ) {
            // Replaced Icon with Text
            Text(
                text = "+",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = CartoonColors.Outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSessions: () -> Unit,
    viewModel: DiceViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // State to control the clear history confirmation dialog
    var showClearDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CreateSessionDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                viewModel.startNewSession(name)
                showCreateDialog = false
                // No navigation needed, DiceScreen will handle active session UI
                // But we probably want to navigate back to Main if started from History?
                // Actually, starting a new session from history usually implies you want to play it.
                // The current flow is: onNavigateToSessions -> Create -> (Session Starts)
                // Here we are inside HistoryScreen.
                // If we start a new session, we should probably clear history view context?
                // Let's just stay here or maybe go back. The previous logic didn't navigate back.
                // The user asked to "Save" via this button, so essentially "Create Session".
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Session History?") },
            text = { Text("This will permanently delete all roll records for the active session '${uiState.activeSession?.name}'.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearCurrentHistory()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear", color = CartoonColors.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Roll History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Empty actions
                }
            )
        },
        floatingActionButton = {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Create Session Button (Green +)
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = CartoonColors.Green,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Save / New Session")
                }

                // 2. Load Button (Blue Refresh)
                FloatingActionButton(
                    onClick = { onNavigateToSessions() },
                    containerColor = CartoonColors.Blue,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Load Session")
                }

                // 3. Clear History Button (Red Delete)
                FloatingActionButton(
                    onClick = { 
                        if (uiState.activeSession != null) {
                            showClearDialog = true
                        } else {
                            viewModel.clearCurrentHistory() 
                        }
                    },
                    containerColor = CartoonColors.Red,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear History")
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Current Session Indicator
            if (uiState.activeSession != null) {
                Surface(
                    color = CartoonColors.Blue.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Playing: ${uiState.activeSession!!.name}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.endCurrentSession() }) {
                            Text("Stop")
                        }
                    }
                }
            } else {
                // Removed the "Quick Play (Not Saved)" Surface with Save/Load button
                // as requested.
                /*
                Surface(
                    color = Color.LightGray.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Playing: Quick Play (Not Saved)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        // Removed Button
                    }
                }
                */
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.history) { item ->
                    HistoryItemCard(item)
                }
                if (uiState.history.isEmpty()) {
                    item {
                        Text(
                            text = "No rolls yet.",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            textAlign = TextAlign.Center,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(item: RollHistoryItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.breakdown,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Text(
                    text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(item.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray
                )
            }
            Text(
                text = item.result,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = CartoonColors.Outline
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DiceViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var sessionToLoad by remember { mutableStateOf<GameSession?>(null) }
    var sessionToDelete by remember { mutableStateOf<GameSession?>(null) }

    if (showCreateDialog) {
        CreateSessionDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                viewModel.startNewSession(name)
                showCreateDialog = false
                onNavigateBack() // Go back to history or stay here? User might want to verify.
                // Actually, flow: Open Sessions -> Create -> (Session Starts) -> Maybe go back to DiceScreen?
                // Let's go back.
            }
        )
    }
    
    if (sessionToLoad != null) {
        AlertDialog(
            onDismissRequest = { sessionToLoad = null },
            title = { Text("Merge History?") },
            text = { Text("You have unsaved roll history in Quick Play. Do you want to merge it into '${sessionToLoad?.name}'?") },
            confirmButton = {
                TextButton(onClick = {
                    sessionToLoad?.let { viewModel.mergeAndResumeSession(it) }
                    sessionToLoad = null
                    onNavigateBack()
                }) {
                    Text("Merge")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    sessionToLoad?.let { viewModel.resumeSession(it) }
                    sessionToLoad = null
                    onNavigateBack()
                }) {
                    Text("Discard Current History")
                }
            }
        )
    }

    if (sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Delete Session?") },
            text = { Text("Are you sure you want to delete '${sessionToDelete?.name}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    sessionToDelete?.let { viewModel.deleteSession(it) }
                    sessionToDelete = null
                }) {
                    Text("Delete", color = CartoonColors.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Games") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Removed the + button here as requested
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = CartoonColors.Blue,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Game")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.savedSessions.isEmpty()) {
                item {
                    Text(
                        text = "No saved games found. Start a new one!",
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                }
            }
            
            items(uiState.savedSessions) { session ->
                SessionItemCard(
                    session = session,
                    isActive = session.id == uiState.activeSession?.id,
                    onClick = {
                        // Check if we need to ask about merging
                        if (uiState.activeSession == null && uiState.history.isNotEmpty()) {
                            sessionToLoad = session
                        } else {
                            viewModel.resumeSession(session)
                            onNavigateBack()
                        }
                    },
                    onDelete = {
                        sessionToDelete = session
                    }
                )
            }
        }
    }
}

@Composable
fun SessionItemCard(
    session: GameSession,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) CartoonColors.Blue.copy(alpha = 0.1f) else Color.White
        ),
        border = if (isActive) androidx.compose.foundation.BorderStroke(2.dp, CartoonColors.Blue) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Last played: ${java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(session.lastPlayedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            
            if (isActive) {
                Text(
                    text = "Active",
                    color = CartoonColors.Blue,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CartoonColors.Red)
            }
        }
    }
}

@Composable
fun CreateSessionDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Game Session") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Session Name") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
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
