package com.eric.pucklauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eric.pucklauncher.ScheduleViewModel
import com.eric.pucklauncher.UiState
import com.eric.pucklauncher.data.Game
import com.eric.pucklauncher.data.Phase
import com.eric.pucklauncher.data.Quality
import com.eric.pucklauncher.data.likely1080Count
import com.eric.pucklauncher.data.quality
import com.eric.pucklauncher.data.Stream
import com.eric.pucklauncher.data.TimeFmt
import com.eric.pucklauncher.data.phase
import com.eric.pucklauncher.data.sortGames
import kotlinx.coroutines.delay
import java.time.Instant

@Composable
fun PuckApp(
    vm: ScheduleViewModel,
    widthClass: WindowWidthSizeClass,
    onOpenBrowser: (Stream) -> Unit,
    onPlayInApp: (Game, Stream) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    PuckScreen(state, widthClass, vm::refresh, onOpenBrowser, onPlayInApp)
}

/** Stateless screen (also used by screenshot tests with [fixedNow] / [initialSelectedId]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuckScreen(
    state: UiState,
    widthClass: WindowWidthSizeClass,
    onRefresh: () -> Unit,
    onOpenBrowser: (Stream) -> Unit,
    onPlayInApp: (Game, Stream) -> Unit,
    fixedNow: Instant? = null,
    initialSelectedId: String? = null,
) {
    var selectedId by rememberSaveable { mutableStateOf(initialSelectedId) }
    val now by produceState(fixedNow ?: Instant.now()) {
        if (fixedNow == null) while (true) { delay(30_000); value = Instant.now() }
    }
    val games = remember(state.games, now) { sortGames(state.games, now) }
    val selected = games.firstOrNull { it.id == selectedId }
    // Side-by-side list + feeds once there is room (S24 Ultra landscape is ~832dp, DeX windows are wider).
    val widthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val twoPane = widthClass != WindowWidthSizeClass.Compact && widthDp >= 760

    if (!twoPane && selected != null) {
        BackHandler { selectedId = null }
        Scaffold(topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { selectedId = null }, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to games", Modifier.size(32.dp))
                    }
                },
                title = { Text("${selected.away?.abbrev ?: selected.awayCity} @ ${selected.home?.abbrev ?: selected.homeCity} · streams", fontSize = 22.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Rink),
            )
        }) { pad ->
            FeedsPane(selected, now, onOpenBrowser, onPlayInApp, Modifier.padding(pad).fillMaxSize())
        }
        return
    }

    Scaffold(topBar = { MainTopBar(state, onRefresh = onRefresh) }) { pad ->
        BoxWithConstraints(Modifier.padding(pad).fillMaxSize()) {
            val paneWidth = (maxWidth * 0.4f).coerceIn(360.dp, 560.dp)
            Row(Modifier.fillMaxSize()) {
                PullToRefreshBox(
                    isRefreshing = state.loading,
                    onRefresh = onRefresh,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    GameGrid(
                        state = state, games = games, now = now, selectedId = selectedId,
                        columns = if (widthClass == WindowWidthSizeClass.Compact) GridCells.Fixed(1) else GridCells.Adaptive(320.dp),
                        onSelect = { selectedId = it.id }, onRetry = onRefresh,
                    )
                }
                if (twoPane) {
                    VerticalDivider(color = MaterialTheme.colorScheme.outline)
                    Box(Modifier.width(paneWidth).fillMaxHeight()) {
                        if (selected != null) FeedsPane(selected, now, onOpenBrowser, onPlayInApp, Modifier.fillMaxSize())
                        else Text(
                            "Pick a game to see its streams",
                            Modifier.align(Alignment.Center).padding(24.dp), fontSize = 20.sp, color = Muted,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(state: UiState, onRefresh: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text("🏒 Puck Launcher", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                val sub = buildString {
                    append(state.updatedAt?.let { "Updated ${TimeFmt.tokyoTime(it)} JST" } ?: "NHL · times in Tokyo (JST)")
                    if (state.updatedAt != null && !state.nhlOk) append(" · live scores unavailable")
                }
                Text(sub, fontSize = 14.sp, color = Muted)
            }
        },
        actions = {
            IconButton(onClick = onRefresh, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Filled.Refresh, "Refresh", Modifier.size(32.dp))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Rink),
    )
}

@Composable
private fun GameGrid(
    state: UiState, games: List<Game>, now: Instant, selectedId: String?,
    columns: GridCells, onSelect: (Game) -> Unit, onRetry: () -> Unit,
) {
    LazyVerticalGrid(
        columns = columns,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        val full: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan) = { GridItemSpan(maxLineSpan) }
        when {
            !state.loadedOnce -> item(span = full) {
                MessageBox("Loading tonight's games…", null, null, null)
            }
            games.isEmpty() && state.error != null -> item(span = full) {
                MessageBox("Couldn't reach onhockey.tv", state.error, "Tap to retry", onRetry)
            }
            games.isEmpty() -> item(span = full) {
                MessageBox("No NHL games today", "onhockey.tv has no NHL games listed right now.", "Check again", onRetry)
            }
            else -> {
                if (state.error != null) item(span = full) {
                    Banner("Couldn't refresh (${state.error}). Showing earlier data. Tap to retry.", onRetry)
                }
                items(games, key = { it.id }) { g ->
                    GameCard(g, now, selected = g.id == selectedId, onClick = { onSelect(g) })
                }
            }
        }
    }
}

@Composable
private fun MessageBox(title: String, detail: String?, action: String?, onAction: (() -> Unit)?) {
    Column(
        Modifier.fillMaxWidth().padding(top = 64.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (onAction == null) CircularProgressIndicator(Modifier.size(56.dp))
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (detail != null) Text(detail, fontSize = 16.sp, color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.heightIn(min = 64.dp).widthIn(min = 220.dp)) {
                Text(action, fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun Banner(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(16.dp)) { Text(text, fontSize = 16.sp) }
    }
}

@Composable
private fun StatusBadge(game: Game, now: Instant) {
    val phase = game.phase(now)
    val (bg, fg) = when (phase) {
        Phase.LIVE -> GoalRed to Color.White
        Phase.STARTED -> Color(0xFF7A2B2B) to Color.White
        Phase.UPCOMING -> IceDeep to Color(0xFFDDF3FF)
        Phase.FINAL -> Color(0xFF2B3442) to Muted
    }
    Text(
        TimeFmt.statusLabel(game, now),
        Modifier.background(bg, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1,
    )
}

@Composable
private fun TeamLine(city: String, nickname: String?, abbrev: String?, score: Int?, big: Boolean = true, home: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            abbrev ?: "—",
            Modifier.width(56.dp).background(IceDeep, RoundedCornerShape(6.dp)).padding(vertical = 4.dp),
            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ice,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                if (home) withStyle(androidx.compose.ui.text.SpanStyle(color = Muted, fontSize = 18.sp)) { append("@ ") }
                append(city)
                if (nickname != null) withStyle(androidx.compose.ui.text.SpanStyle(color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Normal)) { append("  $nickname") }
            },
            Modifier.weight(1f),
            fontSize = if (big) 26.sp else 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = Color.White,
        )
        if (score != null) Text("$score", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
    }
}

@Composable
private fun GameCard(game: Game, now: Instant, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val border = when {
        focused -> BorderStroke(3.dp, Color.White)
        selected -> BorderStroke(3.dp, Ice)
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    }
    Card(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(18.dp),
        border = border,
        colors = CardDefaults.cardColors(containerColor = if (hovered || focused) BoardHover else Board, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(game, now)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(TimeFmt.tokyoTime(game.startUtc), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ice)
                    Text("${TimeFmt.tokyoDay(game.startUtc, now)} · JST", fontSize = 13.sp, color = Muted)
                }
            }
            val l = game.live
            TeamLine(game.awayCity, game.away?.nickname, game.away?.abbrev, l?.awayScore)
            TeamLine(game.homeCity, game.home?.nickname, game.home?.abbrev, l?.homeScore, home = true)
            val n = game.streamCount
            val hd = game.likely1080Count
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hd > 0) Text(
                    "$hd× 1080p",
                    Modifier.background(qualityColors(Quality.HD1080).first, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                    color = qualityColors(Quality.HD1080).second, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                )
                Text(
                    if (n > 0) "▶ $n stream${if (n == 1) "" else "s"} · ${game.groups.joinToString(" · ") { it.label }}"
                    else game.note ?: "No streams yet",
                    fontSize = 15.sp, color = if (n > 0) Color(0xFFCFE9FF) else Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun FeedsPane(
    game: Game, now: Instant,
    onOpenBrowser: (Stream) -> Unit, onPlayInApp: (Game, Stream) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hideSd by rememberSaveable { mutableStateOf(false) }
    val sdCount = game.groups.sumOf { g -> g.streams.count { it.quality == Quality.SD } }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(game, now)
                TeamLine(game.awayCity, game.away?.nickname, game.away?.abbrev, game.live?.awayScore, big = false)
                TeamLine(game.homeCity, game.home?.nickname, game.home?.abbrev, game.live?.homeScore, big = false, home = true)
                Text(
                    "${TimeFmt.tokyoDay(game.startUtc, now)} ${TimeFmt.tokyoTime(game.startUtc)} JST · ${game.streamCount} streams",
                    fontSize = 16.sp, color = Muted,
                )
                Text("Tap a stream to open it in your browser. ▶ plays it inside the app (beta).", fontSize = 14.sp, color = Muted)
                Text("Quality badges are estimates from sampled streams: 1080p / 720p likely, SD = below 720p, ? = unknown.", fontSize = 14.sp, color = Muted)
                if (sdCount > 0) Row(
                    Modifier.heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Switch(checked = hideSd, onCheckedChange = { hideSd = it })
                    Text("Hide SD feeds ($sdCount)", fontSize = 18.sp)
                }
            }
        }
        if (game.streamCount == 0) item(key = "empty") {
            Text(game.note ?: "No streams yet. They usually appear close to puck drop.", fontSize = 20.sp, modifier = Modifier.padding(vertical = 24.dp))
        }
        game.groups.forEach { g ->
            val group = if (hideSd) g.copy(streams = g.streams.filter { it.quality != Quality.SD }) else g
            if (group.streams.isEmpty()) return@forEach
            item(key = "g-${group.label}") {
                Text(
                    group.label.uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Black, color = Ice,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            itemsIndexed(group.streams, key = { i, st -> "s-${group.label}-$i-${st.url}" }) { i, s ->
                StreamRow(i + 1, s, onOpen = { onOpenBrowser(s) }, onPlay = { onPlayInApp(game, s) })
            }
        }
    }
}

@Composable
private fun StreamRow(index: Int, s: Stream, onOpen: () -> Unit, onPlay: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = onOpen,
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = Modifier.weight(1f).heightIn(min = 68.dp),
        ) {
            QualityBadge(s.quality)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.channel.ifBlank { "Stream #$index" }, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${s.host} · #$index · open in browser", fontSize = 13.sp, maxLines = 1)
            }
        }
        FilledTonalButton(
            onClick = onPlay,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.heightIn(min = 68.dp).widthIn(min = 88.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, null, Modifier.size(28.dp))
            Column {
                Text("In app", fontSize = 15.sp)
                Text("beta", fontSize = 11.sp)
            }
        }
    }
}

fun qualityColors(q: Quality): Pair<Color, Color> = when (q) {
    Quality.HD1080 -> Color(0xFF1B8A3A) to Color.White
    Quality.HD720 -> Color(0xFFF2B01E) to Color(0xFF1A1200)
    Quality.SD -> Color(0xFF5B6470) to Color.White
    Quality.UNKNOWN -> Color(0xFF26364D) to Color(0xFFCFE0F2)
}

@Composable
private fun QualityBadge(q: Quality) {
    val (bg, fg) = qualityColors(q)
    Box(
        Modifier.widthIn(min = 76.dp).heightIn(min = 44.dp).background(bg, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(q.label, color = fg, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}
