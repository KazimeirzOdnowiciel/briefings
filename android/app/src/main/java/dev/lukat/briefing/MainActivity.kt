@file:OptIn(ExperimentalSharedTransitionApi::class)

package dev.lukat.briefing

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
private fun briefingColors() = if (Palette.light) {
    androidx.compose.material3.lightColorScheme(
        primary = Accent,
        background = Bg,
        surface = Panel,
        onPrimary = Color.White,
        onBackground = Ink,
        onSurface = Ink,
    )
} else {
    darkColorScheme(
        primary = Accent,
        background = Bg,
        surface = Panel,
        onPrimary = Color(0xFF04231F),
        onBackground = Ink,
        onSurface = Ink,
    )
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BriefingScreen() {
    val context = LocalContext.current
    val deepLinked = remember { dateFromIntent((context as? android.app.Activity)?.intent) }
    val wantsSearch = remember {
        (context as? android.app.Activity)?.intent?.getStringExtra("open") == "search"
    }
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
        state = UiState.Loading
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
    var reading by remember { mutableStateOf<Reading?>(null) }
    var searching by remember { mutableStateOf(wantsSearch) }
    var showingSaved by remember { mutableStateOf(false) }
    val savedCount = remember(reading, showingSaved) { SavedStore.all(context).size }
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

    SharedTransitionLayout {
        AnimatedContent(
            targetState = reading,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically { it / 26 })
                    .togetherWith(fadeOut(tween(150)))
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
                // A reported fold means two panes of room, whatever the width says.
                val wide = fold != null || maxWidth > 840.dp
                when (val s = state) {
                    is UiState.Loading -> LoadingList()
                    is UiState.Failed -> Failure(s.message) { attempt++ }
                    is UiState.Ready -> Edition(
                        feed = s.feed,
                        wide = wide,
                        offline = offline,
                        dates = dates,
                        selected = selected ?: s.feed.date,
                        onSelect = { selected = it },
                        onRetry = { selected = null; attempt++ },
                        updateTag = release?.tag,
                        downloading = downloading,
                        updateError = updateError,
                        onUpdate = {
                            val url = release?.apk
                            if (url == null) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(
                                            "https://github.com/KazimeirzOdnowiciel/briefings/releases/latest"))
                                    )
                                }
                            } else {
                                scope.launch {
                                    downloading = true
                                    updateError = null
                                    updateError = Updater.downloadAndInstall(context, url)
                                    downloading = false
                                }
                            }
                        },
                        onOpen = { item, section -> reading = Reading(item, section) },
                        onSearch = { searching = true },
                        onSaved = { showingSaved = true },
                        savedCount = savedCount,
                        refreshing = refreshing,
                        onRefresh = { refreshing = true; attempt++ }
                    )
                }
            }
                } else {
                    BackHandler { reading = null }
                    val feedNow = (state as? UiState.Ready)?.feed
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
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Could not read the briefing", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(message, color = Dim, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "TAP TO RETRY",
            color = Accent,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Panel)
                .clickable { onRetry() }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Edition(
    feed: Feed,
    wide: Boolean,
    offline: Boolean,
    dates: List<String>,
    selected: String,
    onSelect: (String?) -> Unit,
    onRetry: () -> Unit,
    updateTag: String?,
    downloading: Boolean,
    updateError: String?,
    onUpdate: () -> Unit,
    onOpen: (Item, String) -> Unit,
    onSearch: () -> Unit,
    onSaved: () -> Unit,
    savedCount: Int,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val open: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    val pad = PaddingValues(start = 18.dp, end = 18.dp, top = 26.dp, bottom = 40.dp)
    val header: @Composable () -> Unit = {
        Header(feed, wide, offline, dates, selected, onSelect, onRetry, updateTag, downloading,
            updateError, onUpdate, onSearch, onSaved, savedCount)
    }

    if (wide) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh) {
        TwoPane(
            feed = feed,
            offline = offline,
            dates = dates,
            selected = selected,
            onSelect = onSelect,
            onRetry = onRetry,
            updateTag = updateTag,
            downloading = downloading,
            updateError = updateError,
            onUpdate = onUpdate,
            onSearch = onSearch,
            onSaved = onSaved,
            savedCount = savedCount,
            onOpen = onOpen,
            open = open
        )
        }
    } else {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = pad
        ) {
            item { header() }
            items(feed.sections) { section ->
                SectionBlock(section, wide, open, onOpen)
                Spacer(Modifier.height(20.dp))
            }
            item { Footer(onRetry) }
        }
        }
    }
}

/**
 * Unfolded layout: a section rail on the left, the chosen section on the right.
 *
 * Written by hand rather than pulling in material3-adaptive's ListDetailPaneScaffold: the
 * behaviour we need is a rail plus a pane, and doing it here keeps the build free of another
 * experimental dependency. If pane animations or predictive-back navigator features are wanted
 * later, that library is the drop-in replacement for this function.
 */
@Composable
private fun TwoPane(
    feed: Feed,
    offline: Boolean,
    dates: List<String>,
    selected: String,
    onSelect: (String?) -> Unit,
    onRetry: () -> Unit,
    updateTag: String?,
    downloading: Boolean,
    updateError: String?,
    onUpdate: () -> Unit,
    onSearch: () -> Unit,
    onSaved: () -> Unit,
    savedCount: Int,
    onOpen: (Item, String) -> Unit,
    open: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val start = feed.sections.indexOfFirst { it.kind == "stories" }.coerceAtLeast(0)
    var index by remember(feed.date) { mutableIntStateOf(start) }
    val current = feed.sections.getOrNull(index) ?: feed.sections.firstOrNull()

    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .width(196.dp)
                .fillMaxHeight()
                .background(Bg),
            contentPadding = PaddingValues(vertical = 18.dp, horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                Text(
                    "SECTIONS",
                    color = Faint,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                )
            }
            itemsIndexed(feed.sections) { i, section ->
                val active = i == index
                val count = section.items.count { it.type == "story" }
                val bg by animateColorAsState(
                    if (active) Panel else Bg,
                    animationSpec = tween(180),
                    label = "railBg"
                )
                val bar by animateDpAsState(
                    if (active) 3.dp else 0.dp,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
                    label = "railBar"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            index = i
                        }
                        .padding(start = 6.dp, end = 9.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .width(bar)
                            .height(14.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Accent)
                    )
                    if (bar > 0.dp) Spacer(Modifier.width(5.dp))
                    Text(
                        section.name,
                        color = if (active) Ink else Dim,
                        fontSize = 12.5.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    if (count > 0) {
                        Text(
                            "$count",
                            color = Faint,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Line)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 36.dp)
        ) {
            item {
                Header(feed, true, offline, dates, selected, onSelect, onRetry, updateTag,
                    downloading, updateError, onUpdate, onSearch, onSaved, savedCount)
            }
            if (current != null) {
                item(key = "pane-${current.name}") {
                    SectionBlock(current, true, open, onOpen)
                    Spacer(Modifier.height(20.dp))
                }
            }
            item { Footer(onRetry) }
        }
    }
}

@Composable
private fun HeaderTitles(feed: Feed, wide: Boolean) {
    Text(
        "DAILY BRIEFING · WARSAW",
        color = Accent,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp
    )
    Spacer(Modifier.height(8.dp))
    Text(
        feed.title ?: "Daily Briefing",
        color = Ink,
        fontSize = if (wide) 36.sp else 27.sp,
        lineHeight = if (wide) 40.sp else 31.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp
    )
    feed.greeting?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, color = Dim, fontSize = 13.sp)
    }
}

@Composable
private fun Header(
    feed: Feed,
    wide: Boolean,
    offline: Boolean,
    dates: List<String>,
    selected: String,
    onSelect: (String?) -> Unit,
    onRetry: () -> Unit,
    updateTag: String?,
    downloading: Boolean,
    updateError: String?,
    onUpdate: () -> Unit,
    onSearch: () -> Unit,
    onSaved: () -> Unit,
    savedCount: Int,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(bottom = 22.dp)) {
        val heroImage = remember(feed.date) {
            feed.sections.asSequence()
                .flatMap { it.items.asSequence() }
                .mapNotNull { it.image }
                // publishers often point og:image at a generated social card that already
                // contains the headline — overlaying ours on top of that doubles the text
                .firstOrNull { url ->
                    url.isNotBlank() && listOf("og-image", "og_image", "social", "-card", "card-", "twitter", "share-image")
                        .none { url.lowercase().contains(it) }
                }
        }
        if (heroImage != null) {
            AsyncImage(
                model = heroImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (wide) 210.dp else 168.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Line)
            )
            Spacer(Modifier.height(16.dp))
        }
        HeaderTitles(feed, wide)
        Spacer(Modifier.height(12.dp))
        ChipRow(feed, offline)

        val stale = updateTag != null && updateTag != "app-v" + BuildConfig.VERSION_NAME
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            feed.audio?.let { AudioChip("$SITE_BASE$it", feed.date) }
            if (dates.isNotEmpty()) {
                Pill("SEARCH") { onSearch() }
            }
            Pill(if (savedCount > 0) "★ $savedCount" else "★") { onSaved() }
            if (canPinWidget(context)) {
                Pill("＋ WIDGET") { pinWidget(context) }
            }
            Pill(if (Palette.light) "☾ DARK" else "☀ LIGHT") {
                Palette.light = !Palette.light
                context.getSharedPreferences("briefing", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("light_theme", Palette.light).apply()
            }
            if (stale) {
                Pill(if (downloading) "↓ DOWNLOADING…" else "↑ UPDATE ${updateTag!!.removePrefix("app-")}", onUpdate)
            }
            if (updateError != null) {
                Text(
                    "update failed",
                    color = Amber,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (dates.size > 1) {
            Spacer(Modifier.height(12.dp))
            EditionPicker(dates, selected, onSelect, onRetry)
        }
    }
}

internal const val SITE_BASE = "https://kazimeirzodnowiciel.github.io/briefings/"

/** Ask the launcher to pin the widget — its own confirmation dialog, no long-press hunt. */
private fun canPinWidget(context: android.content.Context): Boolean =
    runCatching {
        android.appwidget.AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
    }.getOrDefault(false)

private fun pinWidget(context: android.content.Context) {
    runCatching {
        val provider = android.content.ComponentName(context, BriefingWidgetReceiver::class.java)
        context.getSystemService(android.appwidget.AppWidgetManager::class.java)
            ?.requestPinAppWidget(provider, null, null)
    }
}

@Composable
internal fun Pill(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Accent,
        fontSize = 10.5.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Panel)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp)
    )
}

/** Plays the published mp3 through a MediaSession, so lock screen and Live Alerts see it. */
@Composable
private fun AudioChip(url: String, editionKey: String) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("briefing", android.content.Context.MODE_PRIVATE)
    }
    val posKey = "audio_pos_$editionKey"
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(prefs.getFloat("audio_speed", 1f)) }
    var controller by remember { mutableStateOf<androidx.media3.session.MediaController?>(null) }
    val future = remember { Playback.controller(context) }

    DisposableEffect(Unit) {
        val ready = Runnable {
            controller = runCatching { future.get() }.getOrNull()
            controller?.addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        androidx.media3.common.Player.STATE_ENDED -> {
                            playing = false
                            prefs.edit().putInt(posKey, 0).apply()
                        }
                        androidx.media3.common.Player.STATE_READY -> loading = false
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                }
            })
        }
        future.addListener(ready, androidx.core.content.ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { controller?.pause() }
            androidx.media3.session.MediaController.releaseFuture(future)
        }
    }

    fun stop() {
        controller?.let { c ->
            runCatching {
                prefs.edit().putInt(posKey, c.currentPosition.toInt()).apply()
                c.pause()
            }
        }
        playing = false
        loading = false
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            when {
                loading -> "… LOADING"
                playing -> "■ STOP"
                else -> "▶ LISTEN"
            },
            color = if (playing) Amber else Accent,
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Panel)
                .clickable {
                    if (playing || loading) {
                        stop()
                    } else {
                        val c = controller
                        if (c == null) {
                            loading = true
                            return@clickable
                        }
                        runCatching {
                            loading = true
                            c.setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
                            c.prepare()
                            val resume = prefs.getInt(posKey, 0)
                            if (resume > 1500) c.seekTo(resume.toLong())
                            c.setPlaybackSpeed(speed)
                            c.play()
                        }.onFailure { loading = false }
                    }
                }
                .padding(horizontal = 11.dp, vertical = 6.dp)
        )

        if (playing || loading) {
            Text(
                if (speed == 1f) "1×" else "${speed}×",
                color = Dim,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Panel)
                    .clickable {
                        speed = when (speed) {
                            1f -> 1.25f
                            1.25f -> 1.5f
                            1.5f -> 2f
                            else -> 1f
                        }
                        prefs.edit().putFloat("audio_speed", speed).apply()
                        runCatching { controller?.setPlaybackSpeed(speed) }
                    }
                    .padding(horizontal = 9.dp, vertical = 6.dp)
            )
        }
    }
}

/** One chip per edition on the phone; tapping an older one reads it from disk. */
@Composable
private fun EditionPicker(
    dates: List<String>,
    selected: String,
    onSelect: (String?) -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val seen = remember(dates, selected) { Seen.dates(context) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "LATEST",
            color = if (selected == dates.first()) Accent else Faint,
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Panel)
                .clickable { onRetry() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
        dates.forEach { date ->
            val active = date == selected
            val bg by animateColorAsState(
                if (active) Color(0xFF16302C) else Panel,
                animationSpec = tween(220),
                label = "chipBg"
            )
            val scale by animateFloatAsState(
                if (active) 1f else 0.955f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
                label = "chipScale"
            )
            Row(
                modifier = Modifier
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(999.dp))
                    .background(bg)
                    .clickable { onSelect(date) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!seen.contains(date)) {
                    Box(
                        Modifier
                            .size(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Amber)
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    date.substring(5),
                    color = if (active) Accent else Dim,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun Footer(onRetry: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Written each morning by the briefing pipeline · tap to reload",
            color = Faint,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .weight(1f)
                .clickable { onRetry() }
        )
        Text(
            "SHARE",
            color = Accent,
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Panel)
                .clickable {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Today's briefing — $SITE_BASE")
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(send, "Share the briefing"))
                    }
                }
                .padding(horizontal = 11.dp, vertical = 6.dp)
        )
    }
}

/** "Today is a workout day: Wednesday — Row B: Intervals — 8 x 3 min…" -> "Wednesday · Row B: Intervals" */
private fun compactWorkout(text: String): String {
    val cleaned = text.removePrefix("💪").removePrefix("😴").trim()
    val parts = cleaned.split(" — ")
    val day = parts.getOrNull(0)?.substringAfterLast(":")?.trim().orEmpty()
    val name = parts.getOrNull(1)?.trim().orEmpty()
    val joiner = if (day.isNotEmpty() && name.isNotEmpty()) " · " else ""
    val short = (day + joiner + name).ifEmpty { cleaned }
    return short.take(46)
}

@Composable
private fun ChipRow(feed: Feed, offline: Boolean) {
    val chips = buildList {
        feed.workout?.let { add(compactWorkout(it)) }
        add("${feed.sections.size} sections" + (feed.readMinutes?.let { " · $it min read" } ?: ""))
        add(if (offline) "offline · stored copy" else feed.date)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { label ->
            val live = label.startsWith("💪")
            val warn = label.startsWith("offline")
            Text(
                label,
                color = when {
                    live -> Accent
                    warn -> Amber
                    else -> Dim
                },
                fontSize = 11.5.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Panel)
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            )
        }
    }
}

/** A stable hue per section, so World and Sport never look identical. */
private fun sectionAccent(name: String): Color {
    val hue = ((name.hashCode() % 360) + 360) % 360
    return Color.hsv(hue.toFloat(), if (Palette.light) 0.55f else 0.38f, if (Palette.light) 0.62f else 0.95f)
}

@Composable
private fun LoadingList() {
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(
        initialValue = 0.32f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(tween(950), RepeatMode.Reverse),
        label = "skeletonAlpha"
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 26.dp, bottom = 40.dp)
    ) {
        item {
            Box(
                Modifier
                    .fillMaxWidth(0.45f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Panel.copy(alpha = alpha))
            )
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.9f)
                    .height(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Panel)
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.6f)
                    .height(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Panel)
            )
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Panel)
            )
            Spacer(Modifier.height(24.dp))
        }
        items(6) { i ->
            Box(
                Modifier
                    .fillMaxWidth(if (i % 3 == 0) 0.7f else 0.95f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Panel.copy(alpha = alpha))
            )
            Spacer(Modifier.height(9.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Line)
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * The same image in the list and in the reader, keyed by story URL, so opening a
 * story morphs the picture instead of cross-fading two unrelated ones.
 */
@Composable
internal fun StoryImage(url: String, storyUrl: String?, thumb: Boolean) {
    val shape = if (thumb) RoundedCornerShape(10.dp) else RoundedCornerShape(14.dp)
    val base = if (thumb) {
        Modifier.size(66.dp)
    } else {
        Modifier.fillMaxWidth().height(190.dp)
    }.clip(shape).background(Line)
    val shared = LocalSharedScope.current
    val anim = LocalAnimScope.current
    if (shared != null && anim != null && storyUrl != null) {
        with(shared) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = base.sharedElement(rememberSharedContentState("img-$storyUrl"), anim)
            )
        }
    } else {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = base)
    }
}

@Composable
private fun SectionBlock(
    section: Section,
    wide: Boolean,
    open: (String) -> Unit,
    onOpen: (Item, String) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(sectionAccent(section.name))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                section.name.uppercase(),
                color = Ink,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                modifier = Modifier.weight(1f)
            )
            val count = section.items.count { it.type == "story" || it.type == "score" }
            if (count > 1) {
                AnimatedContent(
                    targetState = count,
                    transitionSpec = {
                        (fadeIn(tween(160)) + slideInVertically { -it / 2 })
                            .togetherWith(fadeOut(tween(120)))
                    },
                    label = "sectionCount"
                ) { n ->
                    Text(
                        "$n",
                        color = Faint,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Panel)
        ) {
            section.items.forEachIndexed { index, item ->
                if (index > 0) Divider()
                when (item.type) {
                    "score" -> ScoreRow(item)
                    "fixture" -> SimpleRow(item.text ?: "")
                    "note" -> SimpleRow(item.text ?: "")
                    else -> StoryRow(item, section.name, open, onOpen)
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Line)
    )
}

@Composable
private fun StoryRow(item: Item, sectionName: String, open: (String) -> Unit, onOpen: (Item, String) -> Unit) {
    val hasBody = !item.body.isNullOrBlank()
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    // a spring that overshoots a touch, so the row feels like it has mass
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.982f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 900f),
        label = "rowPress"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = press,
                indication = ripple(),
                enabled = hasBody || item.url != null
            ) {
                if (hasBody) onOpen(item, sectionName) else item.url?.let(open)
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
        Row {
            Text(
                (item.headline ?: "").let { if (item.day == 2) "$it  ·DAY 2" else it },
                color = Ink,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.1).sp,
                modifier = Modifier.weight(1f)
            )
            if (hasBody) {
                Text("▸", color = Accent, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }
        item.summary?.let {
            Spacer(Modifier.height(3.dp))
            Text(it, color = Dim, fontSize = 13.sp, lineHeight = 17.sp)
        }
        item.matters?.let {
            Spacer(Modifier.height(4.dp))
            Text("Why it matters: $it", color = Accent, fontSize = 12.sp, lineHeight = 16.sp)
        }
        item.source?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                if (item.paywall) "$it · paywall" else it,
                color = Faint,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Panel)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
        }
        item.image?.takeIf { it.isNotBlank() }?.let { url ->
            Spacer(Modifier.width(12.dp))
            StoryImage(url, item.url, thumb = true)
        }
    }
}

@Composable
private fun ScoreRow(item: Item) {
    val homeWin = (item.homeScore ?: 0) > (item.awayScore ?: 0)
    val awayWin = (item.homeScore ?: 0) < (item.awayScore ?: 0)
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        item.group?.let {
            Text(
                it.uppercase(),
                color = Amber,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.home ?: "",
                color = if (homeWin) Ink else Dim,
                fontWeight = if (homeWin) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${item.homeScore} – ${item.awayScore}",
                color = Ink,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0D141D))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            )
            Text(
                item.away ?: "",
                color = if (awayWin) Ink else Dim,
                fontWeight = if (awayWin) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 14.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            )
        }
        val note = listOfNotNull(item.flag, item.note).joinToString(" · ")
        if (note.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(note, color = Dim, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun SimpleRow(text: String) {
    Text(
        text,
        color = Dim,
        fontSize = 12.5.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}
