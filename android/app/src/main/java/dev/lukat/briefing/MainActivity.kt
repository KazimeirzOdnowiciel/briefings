@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package dev.lukat.briefing

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
private fun briefingColors() = if (Palette.light) {
    lightColorScheme(primary = Accent, background = Bg, surface = Panel, onPrimary = AccentInk, onBackground = Ink, onSurface = Ink)
} else {
    darkColorScheme(primary = Accent, background = Bg, surface = Panel, onPrimary = AccentInk, onBackground = Ink, onSurface = Ink)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notify.ensureChannels(this)
        BriefingWorker.schedule(this)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1
            )
        }
        Palette.light = getSharedPreferences("briefing", MODE_PRIVATE).getBoolean("light_theme", false)
        setContent { MaterialTheme(colorScheme = briefingColors()) { BriefingScreen() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (dateFromIntent(intent) != null) recreate()
    }
}

/** briefing://edition/2026-11-05 opens that day's edition. */
private fun dateFromIntent(intent: Intent?): String? {
    val uri = intent?.data ?: return null
    if (uri.scheme != "briefing") return null
    val candidate = uri.pathSegments.lastOrNull() ?: return null
    return if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(candidate)) candidate else null
}

private data class Reading(val item: Item, val section: String)

private sealed interface UiState {
    data object Loading : UiState
    data class Ready(val feed: Feed) : UiState
    data class Failed(val message: String) : UiState
}

internal val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
internal val LocalAnimScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Navigation is three in-memory layers, each dismissed by back: Today (category cards) →
 * a category (story deck or scoreboard) → the reader. Search and Saved sit on top.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BriefingScreen() {
    val context = LocalContext.current
    val deepLinked = remember { dateFromIntent((context as? android.app.Activity)?.intent) }
    val wantsSearch = remember { (context as? android.app.Activity)?.intent?.getStringExtra("open") == "search" }
    var state by remember { mutableStateOf<UiState>(UiState.Loading) }
    var attempt by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<String?>(deepLinked) }
    var offline by remember { mutableStateOf(false) }
    var dates by remember { mutableStateOf<List<String>>(emptyList()) }
    var release by remember { mutableStateOf<Release?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { release = latestRelease() }

    LaunchedEffect(attempt, selected) {
        if (!refreshing) state = UiState.Loading
        dates = EditionStore.dates(context)
        val wanted = selected
        state = if (wanted != null) {
            val raw = EditionStore.load(context, wanted)
            if (raw == null) {
                UiState.Failed("edition $wanted is not on the phone")
            } else {
                offline = true
                markSeen(context, wanted)
                UiState.Ready(parseFeed(raw))
            }
        } else {
            try {
                val raw = loadFeedRaw()
                FeedStore.save(context, raw)
                EditionStore.save(context, raw)
                dates = EditionStore.dates(context)
                offline = false
                refreshing = false
                val feed = parseFeed(raw)
                markSeen(context, feed.date)
                runCatching { BriefingWidget().updateAll(context) }
                UiState.Ready(feed)
            } catch (e: Exception) {
                refreshing = false
                val cached = EditionStore.latest(context)
                if (cached != null) {
                    offline = true
                    UiState.Ready(parseFeed(cached))
                } else {
                    UiState.Failed(e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }

    val fold = rememberFoldInfo()
    var category by remember { mutableStateOf<String?>(null) }
    var reading by remember { mutableStateOf<Reading?>(null) }
    var searching by remember { mutableStateOf(wantsSearch) }
    var showingSaved by remember { mutableStateOf(false) }
    val feedNow = (state as? UiState.Ready)?.feed
    val savedCount = remember(reading, showingSaved) { SavedStore.all(context).size }
    val read = remember(reading, category, feedNow) { ReadStore.all(context) }

    LaunchedEffect(reading) {
        val r = reading
        if (r != null && feedNow != null) ReadStore.mark(context, feedNow.date, r.item)
    }

    if (showingSaved) {
        BackHandler { showingSaved = false }
        SavedScreen(
            onClose = { showingSaved = false },
            onOpen = { item -> showingSaved = false; reading = Reading(item, "saved") }
        )
        return
    }
    if (searching) {
        BackHandler { searching = false }
        SearchScreen(
            onClose = { searching = false },
            onOpen = { item -> searching = false; reading = Reading(item, "search") }
        )
        return
    }

    val onUpdate: () -> Unit = {
        val url = release?.apk
        if (url == null) {
            openUrl(context, "https://github.com/KazimeirzOdnowiciel/briefings/releases/latest")
        } else {
            scope.launch {
                downloading = true
                updateError = null
                updateError = Updater.downloadAndInstall(context, url)
                downloading = false
            }
        }
    }
    val stale = release?.tag?.let { isNewer(it.removePrefix("app-v"), BuildConfig.VERSION_NAME) } == true
    val updateLabel = if (stale) (if (downloading) "downloading…" else release!!.tag.removePrefix("app-")) else null

    SharedTransitionLayout {
        AnimatedContent(
            targetState = reading,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically { it / 26 }).togetherWith(fadeOut(tween(150)))
            },
            contentKey = { it?.item?.url },
            label = "reader"
        ) { target ->
            CompositionLocalProvider(
                LocalSharedScope provides this@SharedTransitionLayout,
                LocalAnimScope provides this
            ) {
                if (target == null) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Bg)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        // a reported fold means room for more, whatever the width says
                        val wide = fold != null || maxWidth > 700.dp
                        when (val s = state) {
                            is UiState.Loading -> LoadingGrid()
                            is UiState.Failed -> Failure(s.message) { attempt++ }
                            is UiState.Ready -> AnimatedContent(
                                targetState = category,
                                transitionSpec = {
                                    if (targetState != null && initialState == null) {
                                        (fadeIn(tween(220)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(120))
                                    } else {
                                        fadeIn(tween(200)) togetherWith fadeOut(tween(120))
                                    }
                                },
                                contentKey = { it == null },
                                label = "nav"
                            ) { cat ->
                                if (cat == null) {
                                    HomeScreen(
                                        feed = s.feed,
                                        wide = wide,
                                        offline = offline,
                                        dates = dates,
                                        selected = selected ?: s.feed.date,
                                        read = read,
                                        savedCount = savedCount,
                                        updateLabel = updateLabel,
                                        updateError = updateError,
                                        onUpdate = onUpdate,
                                        onSelectDate = { selected = it },
                                        onLatest = { selected = null; attempt++ },
                                        onCategory = { category = it },
                                        onSearch = { searching = true },
                                        onSaved = { showingSaved = true },
                                        refreshing = refreshing,
                                        onRefresh = { refreshing = true; attempt++ }
                                    )
                                } else {
                                    BackHandler { category = null }
                                    CategoryScreen(
                                        feed = s.feed,
                                        name = cat,
                                        wide = wide,
                                        read = read,
                                        onSwitch = { category = it },
                                        onBack = { category = null },
                                        onOpen = { item, section -> reading = Reading(item, section) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    BackHandler { reading = null }
                    val siblings = remember(feedNow, target.section) {
                        feedNow?.sections?.firstOrNull { it.name == target.section }
                            ?.items?.filter { !it.body.isNullOrBlank() } ?: emptyList()
                    }
                    Reader(
                        item = target.item,
                        edition = feedNow?.date.orEmpty(),
                        section = target.section,
                        siblings = siblings,
                        onNavigate = { next -> reading = Reading(next, target.section) }
                    ) { reading = null }
                }
            }
        }
    }
}

@Composable
private fun Failure(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        SunriseMark(44.dp)
        Spacer(Modifier.height(18.dp))
        Text(
            "Could not read the briefing",
            color = Ink, fontSize = 24.sp, lineHeight = 28.sp, fontFamily = Serif,
            fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp
        )
        Spacer(Modifier.height(8.dp))
        Text(message, color = Body, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(18.dp))
        Chip("Try again", on = true) { onRetry() }
    }
}

internal const val SITE_BASE = "https://kazimeirzodnowiciel.github.io/briefings/"

/** Ask the launcher to pin the widget — its own confirmation dialog, no long-press hunt. */
internal fun canPinWidget(context: android.content.Context): Boolean =
    runCatching {
        android.appwidget.AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
    }.getOrDefault(false)

internal fun pinWidget(context: android.content.Context) {
    runCatching {
        val provider = android.content.ComponentName(context, BriefingWidgetReceiver::class.java)
        context.getSystemService(android.appwidget.AppWidgetManager::class.java)
            ?.requestPinAppWidget(provider, null, null)
    }
}

@Composable
internal fun Pill(label: String, onClick: () -> Unit) = Chip(label, onClick = onClick)

/** "Today is a workout day: Wednesday — Row B: Intervals — 8 x 3 min…" -> "Wednesday · Row B: Intervals" */
internal fun compactWorkout(text: String): String {
    val rest = text.startsWith("😴")
    val cleaned = text.removePrefix("💪").removePrefix("😴").trim()
    if (rest) return "Rest day"
    val parts = cleaned.split(" — ")
    val name = parts.getOrNull(1)?.trim().orEmpty()
    return ("💪 " + name.ifEmpty { cleaned }).take(40)
}

/** Breathing placeholders in the shape of the Today screen. */
@Composable
private fun LoadingGrid() {
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(950), RepeatMode.Reverse), label = "skeletonAlpha"
    )
    val block = Panel.copy(alpha = alpha)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row { SunriseMark(30.dp) }
        Spacer(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(22.dp)).background(block))
        Spacer(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(22.dp)).background(block))
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.weight(1f).height(190.dp).clip(RoundedCornerShape(22.dp)).background(block))
                Spacer(Modifier.weight(1f).height(190.dp).clip(RoundedCornerShape(22.dp)).background(block))
            }
        }
    }
}

/**
 * The same image in a list and in the reader, keyed by story URL, so opening a
 * story morphs the picture instead of cross-fading two unrelated ones.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun StoryImage(url: String, storyUrl: String?, thumb: Boolean) {
    val shape = if (thumb) RoundedCornerShape(12.dp) else RoundedCornerShape(18.dp)
    val base = if (thumb) Modifier.size(76.dp) else Modifier.fillMaxWidth().height(220.dp)
    val mod = base.clip(shape).background(Line)
    val shared = LocalSharedScope.current
    val anim = LocalAnimScope.current
    if (shared != null && anim != null && storyUrl != null) {
        with(shared) {
            AsyncImage(
                model = url, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = mod.sharedElement(rememberSharedContentState("img-$storyUrl"), anim)
            )
        }
    } else {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = mod)
    }
}

/** "0.41" > "0.40", "0.100" > "0.99": compare dotted versions numerically, so a sideloaded
 *  build newer than the latest release is never offered a downgrade. */
internal fun isNewer(candidate: String, current: String): Boolean {
    val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
    val b = current.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
