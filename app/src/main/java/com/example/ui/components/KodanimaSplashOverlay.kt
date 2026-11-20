package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.engine.SplashAudioEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class SplashMotionDirection {
    CLOSE_BREATHE_IN,
    CLOSE_BREATHE_OUT,
    CLOSE_GLIDE_LEFT,
    CLOSE_GLIDE_RIGHT
}

/**
 * Tracks cold-start splash state in process memory and persists non-repeating deck history
 * in SharedPreferences so every fresh launch shows a brand-new splash artwork before repeating.
 */
object ColdStartSplashManager {
    private const val PREFS_NAME = "kodanima_app_prefs"
    private const val KEY_LAST_VARIANT = "splash_last_variant"
    private const val KEY_UNSEEN_QUEUE = "splash_unseen_queue"
    private const val KEY_LAST_DIRECTION = "splash_last_direction"

    @Volatile
    var hasShownColdStartSplash: Boolean = false

    @Volatile
    private var activeSessionVariant: Int? = null

    @Volatile
    private var activeSessionDirection: SplashMotionDirection? = null

    @Volatile
    private var preloadedVariant: Int = -1

    @Volatile
    private var preloadedBitmap: Bitmap? = null

    @Volatile
    private var preloadedEdgeColor: Int = 0xFF12101C.toInt()

    fun shouldShowOnColdStart(): Boolean {
        return !hasShownColdStartSplash
    }

    fun markShown() {
        hasShownColdStartSplash = true
    }

    fun pickNextVariant(context: Context): Int {
        activeSessionVariant?.let { return it }

        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastVariant = sp.getInt(KEY_LAST_VARIANT, 0)
        val rawQueue = sp.getString(KEY_UNSEEN_QUEUE, "") ?: ""

        val queue = rawQueue.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..5 }
            .distinct()
            .toMutableList()

        // Ensure even a partial persisted queue doesn't immediately show the last-shown splash
        if (queue.isNotEmpty() && queue.first() == lastVariant && queue.size > 1) {
            val first = queue.removeAt(0)
            queue.add(first)
        }

        if (queue.isEmpty()) {
            val fresh = (1..5).shuffled().toMutableList()
            if (fresh.first() == lastVariant) {
                val swapIdx = 1 + Random.nextInt(fresh.size - 1)
                val tmp = fresh[0]
                fresh[0] = fresh[swapIdx]
                fresh[swapIdx] = tmp
            }
            queue.addAll(fresh)
        }

        val chosen = queue.removeAt(0)
        sp.edit()
            .putInt(KEY_LAST_VARIANT, chosen)
            .putString(KEY_UNSEEN_QUEUE, queue.joinToString(","))
            .commit()

        activeSessionVariant = chosen
        return chosen
    }

    fun pickNextDirection(context: Context): SplashMotionDirection {
        activeSessionDirection?.let { return it }

        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastDirIdx = sp.getInt(KEY_LAST_DIRECTION, -1)
        val entries = SplashMotionDirection.entries
        val candidates = entries.indices.filter { it != lastDirIdx }
        val chosenIdx = candidates[Random.nextInt(candidates.size)]
        val chosen = entries[chosenIdx]

        sp.edit().putInt(KEY_LAST_DIRECTION, chosenIdx).commit()
        activeSessionDirection = chosen
        return chosen
    }

    fun preloadPoster(context: Context) {
        val variant = pickNextVariant(context)
        pickNextDirection(context)
        getOrDecodePosterSync(context, variant)
    }

    fun getOrDecodePosterSync(context: Context, variant: Int): Pair<Bitmap?, Int> {
        val existing = preloadedBitmap
        if (existing != null && preloadedVariant == variant && !existing.isRecycled) {
            return existing to preloadedEdgeColor
        }
        val assetName = if (variant == 1) {
            "splash/splash_poster_1.jpg"
        } else {
            "splash/splash_poster_$variant.png"
        }
        return runCatching {
            context.assets.open(assetName).use { stream ->
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = BitmapFactory.decodeStream(stream, null, opts)
                if (decoded != null) {
                    val edgePixel = decoded.getPixel(
                        4.coerceAtMost(decoded.width - 1),
                        4.coerceAtMost(decoded.height - 1)
                    )
                    preloadedVariant = variant
                    preloadedBitmap = decoded
                    preloadedEdgeColor = edgePixel
                    decoded to edgePixel
                } else {
                    null to 0xFF12101C.toInt()
                }
            }
        }.getOrElse { null to 0xFF12101C.toInt() }
    }

    internal fun resetForTesting() {
        hasShownColdStartSplash = false
        activeSessionVariant = null
        activeSessionDirection = null
        preloadedVariant = -1
        preloadedBitmap = null
        preloadedEdgeColor = 0xFF12101C.toInt()
    }
}

@Composable
fun KodanimaColdStartSplashOverlay(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val variant = remember { ColdStartSplashManager.pickNextVariant(context) }
    val direction = remember { ColdStartSplashManager.pickNextDirection(context) }
    val initialPosterPair = remember(variant) {
        ColdStartSplashManager.getOrDecodePosterSync(context, variant)
    }

    var posterBitmap by remember { mutableStateOf(initialPosterPair.first) }
    var backdropColor by remember { mutableStateOf(Color(initialPosterPair.second)) }
    var enterStarted by remember { mutableStateOf(false) }
    var isFadingOut by remember { mutableStateOf(false) }
    var canSkip by remember { mutableStateOf(false) }
    var loadingProgress by remember { mutableFloatStateOf(0.06f) }
    var statusIndex by remember { mutableIntStateOf(0) }

    val statusMessages = remember {
        listOf(
            "Carregando bibliotecas Skia & Hardware...",
            "Inicializando pincéis Krita & pressão...",
            "Preparando motor Multiplayer Coop LAN...",
            "Estúdio Kodanima 2D v${BuildConfig.VERSION_NAME} pronto!"
        )
    }

    val accentForVariant = remember(variant) {
        when (variant) {
            1 -> Color(0xFFE040FB)
            2 -> Color(0xFF00E676)
            3 -> Color(0xFFFF6E40)
            4 -> Color(0xFF64B5F6)
            else -> Color(0xFF7C4DFF)
        }
    }

    // Start music immediately & ensure poster is decoded cleanly
    LaunchedEffect(variant) {
        SplashAudioEngine.playStartupTrack(variant, context)

        if (posterBitmap == null) {
            val loaded = withContext(Dispatchers.IO) {
                ColdStartSplashManager.getOrDecodePosterSync(context, variant)
            }
            if (loaded.first != null) {
                posterBitmap = loaded.first
                backdropColor = Color(loaded.second)
            }
        }
        enterStarted = true

        // Smooth, unhurried studio intro pacing (~4.5s total)
        delay(650L)
        canSkip = true
        loadingProgress = 0.28f
        statusIndex = 1

        delay(1150L)
        loadingProgress = 0.62f
        statusIndex = 2

        delay(1300L)
        loadingProgress = 0.92f
        statusIndex = 2

        delay(850L)
        loadingProgress = 1.0f
        statusIndex = 3

        delay(500L)
        isFadingOut = true
        delay(420L)
        ColdStartSplashManager.markShown()
        SplashAudioEngine.stop()
        onFinished()
    }

    DisposableEffect(Unit) {
        onDispose {
            SplashAudioEngine.stop()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "splash_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "emblem_spin"
    )

    val overlayAlpha by animateFloatAsState(
        targetValue = if (isFadingOut) 0f else 1f,
        animationSpec = tween(durationMillis = 420),
        label = "splash_fade"
    )

    val cinemaEase = remember { CubicBezierEasing(0.22f, 0.61f, 0.36f, 1.0f) }
    val motionProgress by animateFloatAsState(
        targetValue = if (enterStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 4000, easing = cinemaEase),
        label = "poster_smooth_motion"
    )

    val animProgress by animateFloatAsState(
        targetValue = loadingProgress,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "lib_progress"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha }
            .background(backdropColor)
            .testTag("kodanima_cold_start_splash")
    ) {
        val isPortrait = maxHeight >= maxWidth
        val driftSmallPx = with(density) { 10.dp.toPx() }
        val liftSmallPx = with(density) { 8.dp.toPx() }

        // Intact full poster animated smoothly with close framing (zero white cutouts, zero floating torso, no excess zoom)
        posterBitmap?.let { bmp ->
            val t = motionProgress.coerceIn(0f, 1f)
            val scale = when (direction) {
                SplashMotionDirection.CLOSE_BREATHE_IN -> 1.045f + 0.045f * t
                SplashMotionDirection.CLOSE_BREATHE_OUT -> 1.09f - 0.045f * t
                SplashMotionDirection.CLOSE_GLIDE_LEFT -> 1.07f - 0.025f * t
                SplashMotionDirection.CLOSE_GLIDE_RIGHT -> 1.045f + 0.030f * t
            }
            val shiftX = when (direction) {
                SplashMotionDirection.CLOSE_BREATHE_IN -> -driftSmallPx * 0.45f + driftSmallPx * 0.9f * t
                SplashMotionDirection.CLOSE_BREATHE_OUT -> driftSmallPx * 0.45f - driftSmallPx * 0.9f * t
                SplashMotionDirection.CLOSE_GLIDE_LEFT -> driftSmallPx * 0.8f - (driftSmallPx * 1.6f * t)
                SplashMotionDirection.CLOSE_GLIDE_RIGHT -> -driftSmallPx * 0.8f + (driftSmallPx * 1.6f * t)
            }
            val shiftY = when (direction) {
                SplashMotionDirection.CLOSE_BREATHE_IN -> -liftSmallPx * 0.5f * t
                SplashMotionDirection.CLOSE_BREATHE_OUT -> -liftSmallPx * 0.5f * (1f - t)
                SplashMotionDirection.CLOSE_GLIDE_LEFT -> -liftSmallPx * 0.4f * t
                SplashMotionDirection.CLOSE_GLIDE_RIGHT -> -liftSmallPx * 0.4f * (1f - t)
            }

            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Kodanima 2D Splash",
                contentScale = if (isPortrait) ContentScale.FillWidth else ContentScale.Fit,
                alignment = if (isPortrait) Alignment.BottomCenter else Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = if (isPortrait) {
                            TransformOrigin(0.54f, 0.72f)
                        } else {
                            TransformOrigin(0.5f, 0.5f)
                        }
                        scaleX = scale
                        scaleY = scale
                        translationX = shiftX
                        translationY = shiftY
                    }
            )
        }

        // Top studio wash + bottom readability scrim so loader bar integrates smoothly with poster edge
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to backdropColor.copy(alpha = 0.25f),
                        0.16f to Color.Transparent,
                        0.76f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.55f)
                    )
                )
        )

        // Top-right optional quick skip once libraries start warming (48dp touch target)
        AnimatedVisibility(
            visible = canSkip && !isFadingOut,
            enter = fadeIn(tween(350)),
            exit = fadeOut(tween(250)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable {
                        isFadingOut = true
                        ColdStartSplashManager.markShown()
                        SplashAudioEngine.stop()
                        onFinished()
                    }
                    .testTag("splash_skip_button"),
                color = Color.Black.copy(alpha = 0.62f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(
                        text = "Pular",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Bottom Library Loader HUD with spinning Kodanima emblem
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .fillMaxWidth()
                .testTag("splash_loader_bar"),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF12101C).copy(alpha = 0.92f),
            border = BorderStroke(1.dp, accentForVariant.copy(alpha = 0.65f)),
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(accentForVariant.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_kodanima_k),
                        contentDescription = "Carregando",
                        tint = Color.Unspecified,
                        modifier = Modifier
                            .size(26.dp)
                            .rotate(spinAngle)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusMessages[statusIndex],
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${(animProgress * 100).toInt()}%",
                            color = accentForVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { animProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = accentForVariant,
                        trackColor = Color.White.copy(alpha = 0.14f)
                    )
                }
            }
        }
    }
}
