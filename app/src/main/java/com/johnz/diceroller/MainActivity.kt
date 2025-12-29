package com.johnz.diceroller

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.SoundPool
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
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
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
import com.johnz.diceroller.data.db.GameSession
import com.johnz.diceroller.ui.settings.SettingsScreen
import com.johnz.diceroller.ui.theme.DiceRollerTheme
import com.google.android.gms.ads.MobileAds
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// --- Cartoon Color Palette ---
object CartoonColors {
    val Red = Color(0xFFFF6B6B)
    val Blue = Color(0xFF4ECDC4)
    val Yellow = Color(0xFFFFD93D)
    val Purple = Color(0xFF6C5CE7)
    val Green = Color(0xFF95E1D3)
    val Orange = Color(0xFFFF8E71)
    val Pink = Color(0xFFA29BFE)
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

// Custom Folder Icon
val FolderIcon: ImageVector = ImageVector.Builder(
    name = "Folder",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(10.0f, 4.0f)
        horizontalLineTo(4.0f)
        curveTo(2.9f, 4.0f, 2.01f, 4.9f, 2.01f, 6.0f)
        lineTo(2.0f, 18.0f)
        curveTo(2.0f, 19.1f, 2.9f, 20.0f, 4.0f, 20.0f)
        horizontalLineTo(20.0f)
        curveTo(21.1f, 20.0f, 22.0f, 19.1f, 22.0f, 18.0f)
        verticalLineTo(8.0f)
        curveTo(22.0f, 6.9f, 21.1f, 6.0f, 20.0f, 6.0f)
        horizontalLineTo(12.0f)
        lineTo(10.0f, 4.0f)
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
    }

    fun playRollSound() {
        if (rollSoundId != 0) {
            // Randomize pitch slightly for variety (0.9f to 1.1f)
            val pitch = Random.nextFloat() * 0.2f + 0.9f
            soundPool.play(rollSoundId, 1f, 1f, 0, 0, pitch)
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
    
    // Initialize ViewModel here to share across screens
    val viewModel: DiceViewModel = viewModel()
    
    // Initialize SoundManager
    val soundManager = remember { SoundManager(context) }
    
    // Initialize AdMobHelper
    // val adMobHelper = remember { AdMobHelper(context).apply { loadRewardedAd() } } // Commented out unused AdMobHelper

    // Release SoundPool when the app is destroyed
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
            val activity = LocalContext.current as? Activity
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFFF7F9FC), // Light minimalist background
        topBar = {
            TopAppBar(
                title = { },
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
            
            // Effect Layer: Particles
            ExplosionEffect(trigger = uiState.rollTrigger)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!uiState.isRolling) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            viewModel.rollDice()
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))

                DiceDisplay(uiState = uiState)

                Spacer(modifier = Modifier.weight(1f))

                DiceSelector(uiState = uiState, onSelect = { viewModel.selectDice(it) })

                // --- UI CHANGE: Logic for displaying controls ---
                if (uiState.selectedDice == DiceType.CUSTOM) {
                    // Custom always shows Text Input
                    CustomFormulaInput(
                        value = uiState.customFormula,
                        onValueChange = { viewModel.updateCustomFormula(it) },
                        onDone = { viewModel.rollDice() }
                    )
                } else {
                    // Standard Dice always show Interactive Controls
                    InteractiveDiceControls(
                        diceCount = uiState.customDiceCount,
                        modifier = uiState.customModifier,
                        onCountChange = { viewModel.changeCustomDiceCount(it) },
                        onModifierChange = { viewModel.changeCustomModifier(it) }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ... DiceDisplay, DiceShapeRenderer, ExplosionEffect, DiceSelector, CustomFormulaInput, InteractiveDiceControls, ControlGroup ...
// Assuming these are unchanged from previous steps, I will keep them but focus on HistoryScreen and adding SessionsScreen

@Composable
fun DiceDisplay(uiState: DiceUiState) {
    val baseColor = when(uiState.selectedDice) {
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

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(280.dp)
            .scale(scale)
    ) {
        // Render dice based on selected style
        DiceShapeRenderer(
            style = uiState.diceStyle,
            type = uiState.selectedDice,
            color = baseColor
        )

        val textOffset = if (uiState.selectedDice == DiceType.D4) {
            Modifier.offset(x = 24.dp, y = 16.dp)
        } else if (uiState.selectedDice == DiceType.D6) {
             Modifier.offset(x = (-12).dp, y = 12.dp)
        } else if (uiState.selectedDice == DiceType.D8) {
             Modifier.offset(y = (-4).dp) 
        } else if (uiState.selectedDice == DiceType.D10 ) {
             Modifier.offset(y = (-12).dp) 
        } else if (uiState.selectedDice == DiceType.D100) {
            Modifier.offset(y = (0).dp)
        } else if (uiState.selectedDice == DiceType.CUSTOM) {
            Modifier.offset(y = (0).dp)
        } else {
            Modifier
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = textOffset
        ) {
            // Shadow text only for Cartoon/3D styles
            if (uiState.diceStyle != DiceStyle.FLAT_2D) {
                Text(
                    text = uiState.displayedResult,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if(uiState.diceStyle == DiceStyle.REALISTIC_3D) Color.Black.copy(alpha = 0.3f) else CartoonColors.Outline.copy(alpha = 0.2f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.displayLarge.copy(
                        drawStyle = Stroke(width = 12f, join = StrokeJoin.Round)
                    ),
                    modifier = Modifier.offset(x = 4.dp, y = 4.dp)
                )
            }
            
            Text(
                text = uiState.displayedResult,
                fontSize = 64.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }

    if (!uiState.isRolling && uiState.breakdown.isNotEmpty()) {
        Text(
            text = "Details: ${uiState.breakdown}",
            style = MaterialTheme.typography.titleMedium,
            color = CartoonColors.Outline,
            modifier = Modifier.padding(top = 16.dp)
        )
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
            val innerPath = Path()

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
fun DiceSelector(uiState: DiceUiState, onSelect: (DiceType) -> Unit) {
    if (uiState.visibleDiceTypes.isEmpty()) return
    val listState = rememberLazyListState()
    LaunchedEffect(uiState.selectedDice) {
        val index = uiState.visibleDiceTypes.indexOf(uiState.selectedDice)
        if (index >= 0) listState.animateScrollToItem(index)
    }
    LazyRow(
        state = listState, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center, contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(uiState.visibleDiceTypes) { type ->
            val isSelected = uiState.selectedDice == type
            val backgroundColor by animateColorAsState(if (isSelected) CartoonColors.Outline else Color.Transparent, label = "bgColor")
            val contentColor by animateColorAsState(if (isSelected) Color.White else CartoonColors.Outline.copy(alpha = 0.5f), label = "contentColor")
            Box(
                modifier = Modifier.padding(horizontal = 4.dp).clip(CircleShape).background(backgroundColor).clickable { onSelect(type) }.padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = type.label, fontWeight = FontWeight.Bold, color = contentColor, fontSize = 16.sp)
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
                    TextButton(onClick = onNavigateToSessions) {
                        // Use our custom defined FolderIcon here
                        Icon(imageVector = FolderIcon, contentDescription = null, tint = CartoonColors.Outline)
                        Spacer(Modifier.width(4.dp))
                        Text("Saved Games", color = CartoonColors.Outline)
                    }
                }
            )
        },
        floatingActionButton = {
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
                        TextButton(onClick = onNavigateToSessions) {
                            Text("Save / Load")
                        }
                    }
                }
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
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "New Game")
                    }
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
                        viewModel.resumeSession(session)
                        onNavigateBack()
                    },
                    onDelete = {
                        viewModel.deleteSession(session)
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
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
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
