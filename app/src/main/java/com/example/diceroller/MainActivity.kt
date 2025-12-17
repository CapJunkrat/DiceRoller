package com.example.diceroller

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
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
import com.example.diceroller.data.DiceStyle
import com.example.diceroller.ui.settings.SettingsScreen
import com.example.diceroller.ui.theme.DiceRollerTheme
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

        // rollSoundId = soundPool.load(context, R.raw.dice_roll, 1)
        rollSoundId = 0
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
    
    // Initialize SoundManager
    val context = LocalContext.current
    // Use remember to keep the instance across recompositions
    val soundManager = remember { SoundManager(context) }

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
                soundManager = soundManager
            )
        }
        composable("settings") {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiceScreen(
    onNavigateToSettings: () -> Unit,
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToSettings,
                containerColor = CartoonColors.Outline,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
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

                if (uiState.selectedDice == DiceType.CUSTOM) {
                    CustomFormulaInput(
                        value = uiState.customFormula,
                        onValueChange = { viewModel.updateCustomFormula(it) },
                        onDone = { viewModel.rollDice() }
                    )
                } else {
                    Spacer(modifier = Modifier.height(60.dp))
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun DiceDisplay(uiState: DiceUiState) {
    val baseColor = when(uiState.selectedDice) {
        DiceType.D4 -> CartoonColors.Red
        DiceType.D6 -> CartoonColors.Blue
        DiceType.D8 -> CartoonColors.Green
        DiceType.D10 -> CartoonColors.Purple
        DiceType.D12 -> CartoonColors.Orange
        DiceType.D20 -> CartoonColors.Yellow
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

        // D4 needs to be shifted Right and Down to sit on the main face
        // D6 needs to be shifted Right and Down (because Right Face is main)
        val textOffset = if (uiState.selectedDice == DiceType.D4) {
            Modifier.offset(x = 24.dp, y = 16.dp)
        } else if (uiState.selectedDice == DiceType.D6 || uiState.selectedDice == DiceType.CUSTOM) {
             Modifier.offset(x = (-12).dp, y = 12.dp)
        } else if (uiState.selectedDice == DiceType.D8) {
             Modifier.offset(y = (-4).dp) 
        } else if (uiState.selectedDice == DiceType.D10 || uiState.selectedDice == DiceType.D100) {
             Modifier.offset(y = (-12).dp) // Shift up slightly for D10
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

    if (!uiState.isRolling && uiState.breakdown.isNotEmpty() && uiState.selectedDice == DiceType.CUSTOM) {
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
        val radius = size.minDimension / 2 * if(type == DiceType.D4) 0.96f else 0.8f // D4 scale fix baked in

        val pTop: Offset
        val pLeft: Offset
        val pRight: Offset
        val pBottom: Offset
        
        // Define common D4 geometry for all styles
        if (type == DiceType.D4) {
            pTop = Offset(cx + radius * 0.05f, cy - radius * 0.9f)
            pLeft = Offset(cx - radius * 0.75f, cy + radius * 0.4f)
            pRight = Offset(cx + radius * 0.85f, cy + radius * 0.3f)
            pBottom = Offset(cx - radius * 0.3f, cy + radius * 0.85f)
        } else {
            // Placeholders
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

            fun drawHighlight(offset: Offset, size: Size, rotation: Float = 0f) {
                withTransform({
                    rotate(degrees = rotation, pivot = Offset(offset.x + size.width/2, offset.y + size.height/2))
                }) {
                    drawOval(color = Color.White.copy(alpha = 0.4f), topLeft = offset, size = size)
                }
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
                DiceType.D6, DiceType.CUSTOM -> {
                    val faceSize = radius * 1.3f
                    val depth = radius * 0.5f
                    
                    // Front Face Coordinates (slightly offset to balance visual center)
                    val fl = cx - faceSize / 2 - depth / 4
                    val ft = cy - faceSize / 2 + depth / 4
                    val fr = fl + faceSize
                    val fb = ft + faceSize
                    
                    // Back/Projected Coordinates
                    val bl = fl + depth
                    val bt = ft - depth
                    val br = fr + depth
                    
                    // 1. Outline Path (Silhouette)
                    path.moveTo(fl, ft)
                    path.lineTo(bl, bt)
                    path.lineTo(br, bt)
                    path.lineTo(br, fb - depth)
                    path.lineTo(fr, fb)
                    path.lineTo(fl, fb)
                    path.close()
                    
                    drawBase(path)

                    // 2. Face Shading
                    // Top Face
                    val topFace = Path().apply {
                        moveTo(fl, ft)
                        lineTo(bl, bt)
                        lineTo(br, bt)
                        lineTo(fr, ft)
                        close()
                    }
                    drawPath(topFace, color = Color.White.copy(alpha = 0.2f))
                    
                    // Right Face
                    val rightFace = Path().apply {
                        moveTo(fr, ft)
                        lineTo(br, bt)
                        lineTo(br, fb - depth)
                        lineTo(fr, fb)
                        close()
                    }
                    drawPath(rightFace, color = Color.Black.copy(alpha = 0.1f))

                    // 3. Inner Lines
                    val inner = Path().apply {
                        moveTo(fl, ft); lineTo(fr, ft); lineTo(fr, fb)
                        moveTo(fr, ft); lineTo(br, bt)
                    }
                    drawInnerLines(inner)

//                    // 4. Highlight
//                    drawHighlight(
//                        offset = Offset(fl + faceSize * 0.1f, ft + faceSize * 0.1f),
//                        size = Size(faceSize * 0.8f, faceSize * 0.2f),
//                        rotation = -5f
//                    )
                }

                DiceType.D8 -> {
                    // Geometry modified for top-down perspective (Top face larger, Bottom face smaller)
                    // Adjusted to make the front face "fatter"
                    val top = Offset(cx, cy - radius)
                    val bottom = Offset(cx, cy + radius * 0.8f) 
                    val left = Offset(cx - radius, cy + radius * 0.1f) 
                    val right = Offset(cx + radius, cy + radius * 0.1f) 
                    
                    // Inner vertices widened to make the top triangle base wider (fatter)
                    val innerLeft = Offset(cx - radius * 0.75f, cy + radius * 0.4f)
                    val innerRight = Offset(cx + radius * 0.75f, cy + radius * 0.4f)

                    // 1. Base Outline (Complex Polygon to include inner vertices)
                    path.moveTo(top.x, top.y)
                    path.lineTo(left.x, left.y)
                    path.lineTo(innerLeft.x, innerLeft.y) // Include inner vertex in outline
                    path.lineTo(bottom.x, bottom.y)
                    path.lineTo(innerRight.x, innerRight.y) // Include inner vertex in outline
                    path.lineTo(right.x, right.y)
                    path.close()
                    
                    drawBase(path)

                    // 2. Side Facets Shading
                    val leftSide = Path().apply {
                        moveTo(top.x, top.y); lineTo(left.x, left.y); lineTo(innerLeft.x, innerLeft.y); close()
                    }
                    drawPath(leftSide, color = Color.Black.copy(alpha = 0.1f))
                    
                    val rightSide = Path().apply {
                        moveTo(top.x, top.y); lineTo(right.x, right.y); lineTo(innerRight.x, innerRight.y); close()
                    }
                    drawPath(rightSide, color = Color.Black.copy(alpha = 0.2f))

                    // 3. Front Faces
                    // Front Upper Face (Main face for text - now larger and wider)
                    val frontUpper = Path().apply {
                        moveTo(top.x, top.y); lineTo(innerRight.x, innerRight.y); lineTo(innerLeft.x, innerLeft.y); close()
                    }
                    // Highlight the top face
                    drawPath(frontUpper, color = Color.White.copy(alpha = 0.15f))

                    // Front Lower Face
                    val frontLower = Path().apply {
                        moveTo(bottom.x, bottom.y); lineTo(innerRight.x, innerRight.y); lineTo(innerLeft.x, innerLeft.y); close()
                    }
                    // Shadow the bottom face
                    drawPath(frontLower, color = Color.Black.copy(alpha = 0.1f))

                    // 4. Inner Lines
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

//                    // 5. Highlight
//                    drawHighlight(
//                         offset = Offset(cx - radius * 0.3f, cy - radius * 0.6f),
//                         size = Size(radius * 0.6f, radius * 0.3f),
//                         rotation = 0f
//                    )
                }

                DiceType.D10, DiceType.D100 ->{

                        // D10 Geometry (Flattened Bottom Kite Version)
                        // 调整目标：使中间面(Face 9)下方的两条边更平缓(趋向水平)。

                        // 1. 顶点定义
                        val top = Offset(cx, cy - radius)
                        val bottom = Offset(cx, cy + radius)

                        // [关键调整 A] 内部顶点 (Inner Vertices)
                        // 大幅下移。之前在 -0.05f，现在改到 +0.18f (中心线下方)。
                        // 下移这个点可以让Face 9的上半部分更长，同时为"变平"下半部分做准备。
                        val innerY = cy + radius * 0.2f
                        val innerX = radius * 0.55f // 稍微加宽一点以增强水平感
                        val innerLeft = Offset(cx - innerX, innerY)
                        val innerRight = Offset(cx + innerX, innerY)

                        // [关键调整 B] 中心交汇点 (Center Junction)
                        // 之前在 +0.45f，现在上提至 +0.38f。
                        // innerY(0.18) 和 centerJunctionY(0.38) 的差值变小了，
                        // 这使得它们之间的连线角度变得非常平缓（接近水平）。
                        val centerJunction = Offset(cx, cy + radius * 0.38f)

                        // [外部轮廓配合调整]
                        // 侧面上端 (Shoulder): 保持在上方，维持"长上边"的视觉效果
                        val shoulderY = cy - radius * 0.25f
                        val shoulderXOffset = radius * 0.95f
                        val shoulderLeft = Offset(cx - shoulderXOffset, shoulderY)
                        val shoulderRight = Offset(cx + shoulderXOffset, shoulderY)

                        // 侧面下端 (Waist): 必须比 innerY 更低一点，以保证几何逻辑正确
                        // 之前在 +0.15，现在下移到 +0.28 (对应 Inner 的下移)
                        // 保持 xOffset 比 shoulder 小，维持"内收/垂直"效果
                        val waistY = cy + radius * 0.28f
                        val waistXOffset = radius * 0.88f
                        val outerLeft = Offset(cx - waistXOffset, waistY)
                        val outerRight = Offset(cx + waistXOffset, waistY)

                        // 2. Base Outline (外轮廓)
                        // 形状：长顶 -> 垂直腰 -> 短底
                        path.moveTo(top.x, top.y)
                        path.lineTo(shoulderLeft.x, shoulderLeft.y)
                        path.lineTo(outerLeft.x, outerLeft.y)
                        path.lineTo(bottom.x, bottom.y)
                        path.lineTo(outerRight.x, outerRight.y)
                        path.lineTo(shoulderRight.x, shoulderRight.y)
                        path.close()
                        drawBase(path)

                        // 3. 绘制切面

                        // --- (A) 中间主面 (Face 9) ---
                        // 现在的特征：上半部分很长，下半部分的V字收口非常平缓(钝角)
                        val centerFace = Path().apply {
                            moveTo(top.x, top.y)
                            lineTo(innerRight.x, innerRight.y)
                            lineTo(centerJunction.x, centerJunction.y)
                            lineTo(innerLeft.x, innerLeft.y)
                            close()
                        }
                        drawPath(centerFace, color = Color.White.copy(alpha = 0.2f))

                        // --- (B) 左上侧面 (Face 5) ---
                        val leftUpperFace = Path().apply {
                            moveTo(top.x, top.y)
                            lineTo(shoulderLeft.x, shoulderLeft.y)
                            lineTo(outerLeft.x, outerLeft.y)
                            lineTo(innerLeft.x, innerLeft.y)
                            close()
                        }
                        drawPath(leftUpperFace, color = Color.Black.copy(alpha = 0.15f))

                        // --- (C) 右上侧面 (Face 1) ---
                        val rightUpperFace = Path().apply {
                            moveTo(top.x, top.y)
                            lineTo(shoulderRight.x, shoulderRight.y)
                            lineTo(outerRight.x, outerRight.y)
                            lineTo(innerRight.x, innerRight.y)
                            close()
                        }
                        drawPath(rightUpperFace, color = Color.Black.copy(alpha = 0.25f))

                        // --- (D) 左下底面 (Face 2) ---
                        val leftBottomFace = Path().apply {
                            moveTo(centerJunction.x, centerJunction.y)
                            lineTo(innerLeft.x, innerLeft.y)
                            lineTo(outerLeft.x, outerLeft.y)
                            lineTo(bottom.x, bottom.y)
                            close()
                        }
                        drawPath(leftBottomFace, color = Color.Black.copy(alpha = 0.05f))

                        // --- (E) 右下底面 (Face 6) ---
                        val rightBottomFace = Path().apply {
                            moveTo(centerJunction.x, centerJunction.y)
                            lineTo(innerRight.x, innerRight.y)
                            lineTo(outerRight.x, outerRight.y)
                            lineTo(bottom.x, bottom.y)
                            close()
                        }
                        drawPath(rightBottomFace, color = Color.Black.copy(alpha = 0.3f))

                        // 4. 棱线
                        val innerLines = Path().apply {
                            // Face 9 轮廓
                            moveTo(top.x, top.y); lineTo(innerLeft.x, innerLeft.y)
                            moveTo(top.x, top.y); lineTo(innerRight.x, innerRight.y)

                            // 这里的连线现在非常平缓
                            moveTo(innerLeft.x, innerLeft.y); lineTo(centerJunction.x, centerJunction.y)
                            moveTo(innerRight.x, innerRight.y); lineTo(centerJunction.x, centerJunction.y)

                            // 侧面横线 (Inner -> Waist)
                            moveTo(innerLeft.x, innerLeft.y); lineTo(outerLeft.x, outerLeft.y)
                            moveTo(innerRight.x, innerRight.y); lineTo(outerRight.x, outerRight.y)

                            // 脊柱
                            moveTo(centerJunction.x, centerJunction.y); lineTo(bottom.x, bottom.y)
                        }
                        drawInnerLines(innerLines)

//                        // 5. Highlight (高光)
//                        drawHighlight(
//                            offset = Offset(cx - radius * 0.2f, cy - radius * 0.3f), // 位置随面下移略微调整
//                            size = Size(radius * 0.35f, radius * 0.2f),
//                            rotation = -35f
//                        )
                    }

                DiceType.D12 -> {
                    for (i in 0 until 5) {
                        val theta = -Math.PI / 2 + i * 2 * Math.PI / 5
                        val x = cx + radius * cos(theta).toFloat()
                        val y = cy + radius * sin(theta).toFloat()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawBase(path)
                    val inner = Path().apply {
                        for (i in 0 until 5) {
                            val theta = -Math.PI / 2 + i * 2 * Math.PI / 5
                            val x = cx + radius * cos(theta).toFloat()
                            val y = cy + radius * sin(theta).toFloat()
                            innerPath.moveTo(cx, cy); innerPath.lineTo(x, y)
                        }
                    }
                    drawInnerLines(inner)
                }
                DiceType.D20 -> {
                    for (i in 0 until 6) {
                        val theta = -Math.PI / 2 + i * 2 * Math.PI / 6
                        val x = cx + radius * cos(theta).toFloat()
                        val y = cy + radius * sin(theta).toFloat()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawBase(path)
                    val v = (0 until 6).map { i -> val theta = -Math.PI / 2 + i * 2 * Math.PI / 6; Offset(cx + radius * cos(theta).toFloat(), cy + radius * sin(theta).toFloat()) }
                    val inner = Path().apply { moveTo(v[0].x, v[0].y); lineTo(v[2].x, v[2].y); lineTo(v[4].x, v[4].y); close() }
                    drawInnerLines(inner)
                }
            }
            
//            if (type != DiceType.D6 && type != DiceType.CUSTOM && type != DiceType.D4) {
//                 drawHighlight(
//                    offset = Offset(cx - radius * 0.5f, cy - radius * 0.7f),
//                    size = Size(radius * 0.6f, radius * 0.3f),
//                    rotation = -30f
//                )
//            }
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

// ... Particle and ExplosionEffect ...
data class Particle(val id: Int, val angle: Double, val speed: Float, val color: Color, val size: Float)

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
                val alpha = (1f - animProgress).coerceIn(0f, 1f)
                val currentSize = p.size * (1f - animProgress * 0.5f)
                drawCircle(color = p.color.copy(alpha = alpha), radius = currentSize, center = Offset(x, y))
            }
        }
    }
}

// Updated Dice Selector to be more modern/minimalist
@Composable
fun DiceSelector(uiState: DiceUiState, onSelect: (DiceType) -> Unit) {
    if (uiState.visibleDiceTypes.isEmpty()) return

    // Modern Capsule-style selector
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center, // Center the items
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(uiState.visibleDiceTypes) { type ->
            val isSelected = uiState.selectedDice == type
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) CartoonColors.Outline else Color.Transparent,
                label = "bgColor"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else CartoonColors.Outline.copy(alpha = 0.5f),
                label = "contentColor"
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .clip(CircleShape) // Capsule shape
                    .background(backgroundColor)
                    .clickable { onSelect(type) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = type.label,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    fontSize = 16.sp
                )
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
