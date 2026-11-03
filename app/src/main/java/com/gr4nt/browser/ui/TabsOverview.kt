package com.gr4nt.browser.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.Job
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.gr4nt.browser.browser.Browser
import com.gr4nt.browser.browser.BrowserTab
import com.gr4nt.browser.data.Store
import com.gr4nt.browser.data.ToolbarLayout
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Tab switcher laid out like the iOS Recents (app switcher): cards are stacked in a deck,
 * the focused card sits in the middle, newer tabs peek in from the right and older tabs
 * pile up behind it on the left. Swipe sideways to flip through, swipe a card up to close it,
 * tap a card to zoom into it.
 */
@Composable
fun TabsOverview(visible: Boolean) {
    AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(220))) {
        LaunchedEffect(Unit) { Browser.tabsPrivate = Browser.current?.isPrivate ?: false; Browser.archiveStaleTabs() }
        val private = Browser.tabsPrivate
        val base = Ios.c
        androidx.compose.runtime.CompositionLocalProvider(LocalIos provides if (private) DarkIos else base) {
            val c = Ios.c
            var q by remember { mutableStateOf("") }
            val normalCount = Browser.tabs.count { !it.isPrivate }
            val group = Browser.tabs.filter { it.isPrivate == private }
                .filter { q.isBlank() || it.displayTitle.contains(q, true) || it.url.contains(q, true) }
            Box(Modifier.fillMaxSize().safariGlassWallpaper(private)) {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlassCircleButton(Icons.Outlined.Inventory2, "Archive", size = 44.dp) { Browser.showArchive = true }
                        Text(if (private) "Private Browsing" else "Your tabs · $normalCount", style = Ios.title3,
                            color = c.label, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        GlassCircleButton(Icons.Outlined.Settings, "Settings", size = 44.dp) { Browser.showSettings = true }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).height(44.dp)
                        .glass(RoundedCornerShape(22.dp), elevation = 3.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Search, null, tint = c.secondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(q, { q = it }, singleLine = true, textStyle = Ios.callout.copy(color = c.label), cursorBrush = SolidColor(c.tint),
                            modifier = Modifier.weight(1f), decorationBox = { inner -> Box { if (q.isEmpty()) Text("Search Tabs", style = Ios.callout, color = c.secondary); inner() } })
                        if (q.isNotEmpty()) InlineIconButton(Icons.Outlined.Close, "Clear Search", size = 30.dp, iconSize = 16.dp) { q = "" }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (group.isEmpty()) EmptyDeck(private, q.isNotBlank())
                        else if (isLargeScreen()) SafariTabGrid(group, private)
                        else key(private) {
                            CardDeck(
                                group = group,
                                private = private,
                                arc = Store.settings.layout == ToolbarLayout.ARC
                            )
                        }
                    }
                }
                Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    GlassCircleButton(SafariSymbols.Plus, "New Tab", size = 48.dp) {
                        Browser.newTab(private = private); Browser.showTabs = false; Browser.editing = true
                    }
                    Row(Modifier.height(46.dp).glass(RoundedCornerShape(23.dp)).padding(4.dp)) {
                        Segment("Private", private) { Browser.tabsPrivate = true }
                        Segment("$normalCount Tabs", !private) { Browser.tabsPrivate = false }
                    }
                    GlassCircleButton(Icons.Outlined.Check, "Done", size = 48.dp, tint = c.tint) {
                        val current = Browser.current
                        if (current == null || current.isPrivate != private) {
                            val first = Browser.tabs.lastOrNull { it.isPrivate == private } ?: Browser.newTab(private = private)
                            Browser.select(first.id)
                        }
                        Browser.showTabs = false
                    }
                }
            }
        }
    }
}

@Composable
private fun SafariTabGrid(tabs: List<BrowserTab>, private: Boolean) {
    LazyVerticalGrid(
        columns = if (isLargeScreen()) GridCells.Adaptive(220.dp) else GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 130.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tabs, key = { it.id }) { tab -> SafariTabCard(tab, private) }
    }
}

@Composable
private fun SafariTabCard(tab: BrowserTab, private: Boolean) {
    val c = Ios.c
    val shape = RoundedCornerShape(22.dp)
    var swipe by remember(tab.id) { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 110.dp.toPx() }
    val selected = Browser.currentId == tab.id
    Column(
        Modifier.fillMaxWidth().height(if (isLargeScreen()) 280.dp else 238.dp)
            .graphicsLayer { translationX = swipe; alpha = (1f - abs(swipe) / (threshold * 2f)).coerceIn(0.25f, 1f) }
            .pointerInput(tab.id) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (abs(swipe) > threshold) Browser.close(tab.id) else swipe = 0f },
                    onDragCancel = { swipe = 0f }
                ) { change, drag -> change.consume(); swipe += drag }
            }.glassCard(shape)
            .then(if (selected) Modifier.border(2.dp, c.tint, shape) else Modifier)
            .pressable(scaleTo = 0.98f) { Browser.select(tab.id); Browser.showTabs = false }
            .padding(8.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (tab.isStartPage) "Start Page" else tab.host.ifBlank { tab.displayTitle },
                style = Ios.caption.copy(fontWeight = FontWeight.SemiBold), color = c.label,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Box(Modifier.size(26.dp).pressable(scaleTo = 0.8f) { Browser.close(tab.id) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, "Close tab", tint = c.secondary, modifier = Modifier.size(16.dp))
            }
        }
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp))
                .background(if (private) Color(0xFF3A3F4D) else Color.White.copy(alpha = 0.66f)),
            contentAlignment = Alignment.Center
        ) {
            val thumb = tab.thumbnail
            if (thumb != null && !tab.isStartPage) {
                Image(thumb.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else if (tab.isStartPage) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.StarOutline, null, tint = c.tint, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("Favorites", style = Ios.caption, color = c.secondary)
                }
            } else {
                Text(
                    (tab.host.firstOrNull() ?: tab.displayTitle.firstOrNull())?.uppercaseChar()?.toString() ?: "•",
                    style = Ios.title, color = c.tint
                )
            }
        }
    }
}

@Composable
private fun EmptyDeck(private: Boolean, searching: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        if (searching) {
            Text("No Matching Tabs", style = Ios.title3, color = if (private) Color.White else Ios.c.label)
        } else {
            Icon(Icons.Outlined.Lock, null, tint = Color.White, modifier = Modifier.size(54.dp))
            Spacer(Modifier.height(14.dp))
            Text("Private Browsing", style = Ios.title3, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                "Prism Browser won't remember the pages you visit, your search history or AutoFill information after you close a tab.",
                style = Ios.subhead, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center
            )
        }
    }
}

/** Horizontal x offset (as a fraction of card width) for a card at relative position [rel]. */
private fun deckX(rel: Float, arc: Boolean = false): Float =
    if (arc) rel * 1.06f                             // Arc / Android Recents: cards side by side
    else if (rel >= 0f) rel * 0.93f                  // newer tabs: fan out to the right
    else -0.36f * ln(1f + (-rel) * 1.7f)              // older tabs: pile up behind, on the left

/** Title/close visibility: fully visible for the focused card and newer, fading out as a card sinks into the pile. */
private fun titleAlpha(rel: Float, arc: Boolean = false): Float = if (arc) 1f else if (rel >= -0.3f) 1f else (1f - (-rel - 0.3f) / 0.5f).coerceIn(0f, 1f)

private fun deckScale(rel: Float, arc: Boolean = false): Float =
    if (arc) 1f - abs(rel).coerceAtMost(1f) * 0.1f
    else if (rel >= 0f) 1f else (1f + rel * 0.045f).coerceAtLeast(0.8f)

/** Spring used when the deck settles after a swipe: quick, with almost no wobble. */
private val DeckSettle = spring<Float>(dampingRatio = 0.88f, stiffness = 420f)
/** Spring used when cards close up the gap left by a closed tab. */
private val DeckReflow = spring<Float>(dampingRatio = 0.86f, stiffness = 360f)

@Composable
private fun CardDeck(group: List<BrowserTab>, private: Boolean, arc: Boolean = false) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    val startIndex = group.indexOfFirst { it.id == Browser.currentId }.let { if (it < 0) group.size - 1 else it }

    // Deck position in "cards". Plain float state: drags write it directly every frame (no
    // coroutine per touch event) and a single cancellable animation drives the settle.
    var pos by remember { mutableFloatStateOf(startIndex.toFloat()) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var lastDetent by remember { mutableIntStateOf(startIndex) }
    fun settleTo(target: Float, velocity: Float = 0f, spec: AnimationSpec<Float> = DeckSettle) {
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(pos, target, velocity, spec) { v, _ -> pos = v }
        }
    }

    // When a tab is closed, keep the focused card still and let the others glide into the gap.
    var lastIds by remember { mutableStateOf(group.map { it.id }) }
    LaunchedEffect(group.map { it.id }) {
        val ids = group.map { it.id }
        val removedBefore = lastIds.withIndex().count { (i, id) -> id !in ids && i < pos.roundToInt() }
        lastIds = ids
        val max = (group.size - 1).coerceAtLeast(0).toFloat()
        val target = (pos.roundToInt() - removedBefore).toFloat().coerceIn(0f, max)
        if (target != pos) settleTo(target, spec = DeckReflow)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val cardW = w * 0.80f
        val cardH = (h * 0.68f).coerceAtMost(cardW * 1.80f)
        // Shorter detents make a deliberate swipe advance tabs sooner; fast flicks still project.
        val step = if (arc) cardW * 1.04f else cardW * 0.74f
        val cardWdp = with(density) { cardW.toDp() }
        val cardHdp = with(density) { cardH.toDp() }

        // only compose the cards that can actually be seen
        val window by remember(group.size) {
            derivedStateOf { val c = pos.roundToInt(); (c - 6).coerceAtLeast(0)..(c + 3).coerceAtMost(group.size - 1) }
        }

        Box(
            Modifier.fillMaxSize().pointerInput(group.map { it.id }) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking(); settleJob?.cancel() },
                    onDragEnd = {
                        val v = (-tracker.calculateVelocity().x / step).coerceIn(-14f, 14f)   // cards / second
                        // project the fling so a quick flick can travel several tabs
                        val projected = pos + v * 0.30f
                        val target = projected.roundToInt().coerceIn(0, group.size - 1).toFloat()
                        settleTo(target, v)
                    },
                    onDragCancel = { settleTo(pos.roundToInt().coerceIn(0, group.size - 1).toFloat()) }
                ) { change, dx ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val max = (group.size - 1).toFloat()
                    val delta = -dx / step
                    // rubber-band past the ends
                    val resist = if ((pos < 0f && delta < 0f) || (pos > max && delta > 0f)) 0.3f else 1f
                    pos = (pos + delta * resist).coerceIn(-0.7f, max + 0.7f)
                    val detent = pos.roundToInt()
                    if (detent != lastDetent && detent in group.indices) {
                        lastDetent = detent
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
            }
        ) {
            group.forEachIndexed { i, t ->
                if (i in window) key(t.id) {
                    // each card glides to its new slot when tabs before it are closed
                    val slot by animateFloatAsState(i.toFloat(), DeckReflow, label = "slot")
                    DeckCard(
                        tab = t,
                        index = i,
                        rel = { slot - pos },
                        cardW = cardW,
                        cardH = cardH,
                        cardWdp = cardWdp,
                        cardHdp = cardHdp,
                        screenH = h,
                        private = private,
                        arc = arc,
                        onFocus = { settleTo(i.toFloat()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeckCard(
    tab: BrowserTab,
    index: Int,
    rel: () -> Float,
    cardW: Float,
    cardH: Float,
    cardWdp: androidx.compose.ui.unit.Dp,
    cardHdp: androidx.compose.ui.unit.Dp,
    screenH: Float,
    private: Boolean,
    arc: Boolean,
    onFocus: () -> Unit
) {
    val c = Ios.c
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    // swipe-up-to-close: finger-tracked offset in a plain float state, one animation at a time
    var dy by remember { mutableFloatStateOf(0f) }
    var dyJob by remember { mutableStateOf<Job?>(null) }
    var closing by remember { mutableStateOf(false) }
    val zoom = remember { Animatable(1f) }        // tap-to-open zoom
    val press = remember { Animatable(1f) }

    fun animateDy(target: Float, velocity: Float, spec: AnimationSpec<Float>, then: (() -> Unit)? = null) {
        dyJob?.cancel()
        dyJob = scope.launch {
            animate(dy, target, velocity, spec) { v, _ -> dy = v }
            then?.invoke()
        }
    }
    fun flyAway(velocity: Float) {
        if (closing) return
        closing = true
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        // keep the finger's momentum and accelerate off the top; remove the tab as it leaves
        animateDy(-screenH * 1.15f, velocity.coerceAtMost(-2500f), tween(240, easing = androidx.compose.animation.core.FastOutLinearInEasing)) {
            Browser.close(tab.id)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(index.toFloat() + if (zoom.value > 1f) 1000f else 0f),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(cardWdp)
                .graphicsLayer {
                    val r = rel()
                    // lift progress: 0 at rest → 1 when the card has travelled ~60% of the screen
                    val lift = (-dy / (screenH * 0.6f)).coerceIn(0f, 1f)
                    val s = deckScale(r, arc) * press.value * zoom.value * (1f - lift * 0.12f)
                    translationX = deckX(r, arc) * cardW
                    translationY = dy + (if (r < 0f && !arc) (-r).coerceAtMost(4f) * 6f else 0f)
                    scaleX = s; scaleY = s
                    rotationX = lift * 6f
                    cameraDistance = 14f * density
                    alpha = when {
                        arc && abs(r) > 2.5f -> 0f
                        r < -5f -> 0f
                        r < -4f -> 1f - (-r - 4f)
                        else -> 1f
                    } * (1f - lift * 0.85f)
                }
        ) {
            // App-switcher style title above the card
            Row(
                Modifier.fillMaxWidth().padding(start = 6.dp, end = 2.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = tab.favicon
                if (icon != null) {
                    Image(icon.asImageBitmap(), null, Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)))
                } else {
                    SiteTile(tab.url, tab.displayTitle, size = 20.dp, corner = 5.dp)
                }
                Spacer(Modifier.width(7.dp))
                Text(
                    tab.displayTitle, style = Ios.footnote.copy(fontWeight = FontWeight.SemiBold),
                    color = if (private || c.dark) Color.White else c.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    // cards buried in the pile show only their icon, like iOS Recents
                    modifier = Modifier.weight(1f).graphicsLayer { alpha = titleAlpha(rel(), arc) }
                )
                Box(
                    Modifier.size(26.dp).graphicsLayer { val r = rel(); alpha = if (arc) (1f - abs(r)).coerceIn(0f, 1f) else if (r > 0.5f) 0f else titleAlpha(r) }
                        .pressable(scaleTo = 0.8f) { flyAway(-3000f) }
                        .clip(CircleShape).background(Color.Black.copy(alpha = 0.28f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.Close, "Close", tint = Color.White, modifier = Modifier.size(14.dp)) }
            }

            val isCurrent = tab.id == Browser.currentId
            Box(
                Modifier
                    .width(cardWdp)
                    .height(cardHdp)
                    .shadow(20.dp, RoundedCornerShape(30.dp), ambientColor = Color.Black.copy(alpha = 0.22f), spotColor = Color.Black.copy(alpha = 0.3f))
                    .clip(RoundedCornerShape(30.dp))
                    .background(c.card)
                    .pointerInput(tab.id) {
                        val tracker = VelocityTracker()
                        detectVerticalDragGestures(
                            onDragStart = { tracker.resetTracking(); dyJob?.cancel() },
                            onDragEnd = {
                                val vy = tracker.calculateVelocity().y      // px / s, negative = upward
                                // close on a decent upward flick, or when dragged far enough
                                if (vy < -1200f || dy < -cardH * 0.25f) flyAway(vy)
                                else animateDy(0f, vy, spring(dampingRatio = 0.7f, stiffness = 420f))
                            },
                            onDragCancel = { animateDy(0f, 0f, spring(dampingRatio = 0.7f, stiffness = 420f)) }
                        ) { ch, d ->
                            if (closing) return@detectVerticalDragGestures
                            ch.consume()
                            tracker.addPosition(ch.uptimeMillis, ch.position)
                            // follows the finger 1:1 upward, resists pulling down
                            dy = (dy + if (dy + d > 0f) d * 0.25f else d).coerceAtLeast(-screenH * 1.2f)
                        }
                    }
                    .pointerInput(tab.id) {
                        detectTapGestures(
                            onPress = {
                                scope.launch { press.animateTo(0.965f, Motion.press) }
                                tryAwaitRelease()
                                scope.launch { press.animateTo(1f, Motion.release) }
                            },
                            onTap = {
                                val r = rel()
                                if (abs(r) > 0.5f) {
                                    onFocus()
                                } else {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    scope.launch {
                                        zoom.animateTo(1.28f, spring(dampingRatio = 0.9f, stiffness = 420f))
                                    }
                                    scope.launch {
                                        kotlinx.coroutines.delay(120)
                                        Browser.select(tab.id)
                                        Browser.showTabs = false
                                    }
                                }
                            }
                        )
                    }
            ) {
                val thumb = tab.thumbnail
                if (thumb != null && !tab.isStartPage) {
                    Image(
                        thumb.asImageBitmap(), null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        Modifier.fillMaxSize().wallpaper(),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                    ) {
                        if (tab.isStartPage) {
                            Icon(Icons.Outlined.StarOutline, null, tint = c.secondary, modifier = Modifier.size(38.dp))
                            Spacer(Modifier.height(6.dp))
                            Text("Start Page", style = Ios.subhead, color = c.secondary)
                        } else {
                            SiteTile(tab.url, tab.displayTitle, tab.favicon, size = 64.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(tab.host, style = Ios.footnote, color = c.secondary)
                        }
                    }
                }
                // depth shading for cards further back in the pile
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val r = rel()
                        alpha = if (r < 0f && !arc) ((-r) * 0.12f).coerceAtMost(0.4f) else 0f
                    }.background(Color.Black)
                )
                if (isCurrent) {
                    Box(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                            .clip(CircleShape).background(c.tint).padding(horizontal = 10.dp, vertical = 3.dp)
                    ) { Text("Current", style = Ios.caption2.copy(fontWeight = FontWeight.Bold), color = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun Segment(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = Ios.c
    Box(
        Modifier.height(38.dp)
            .pressable(scaleTo = 0.92f, onClick = onClick)
            .clip(RoundedCornerShape(19.dp))
            .background(if (selected) (if (c.dark) Color(0x66FFFFFF).copy(alpha = 0.22f) else Color.White.copy(alpha = 0.9f)) else Color.Transparent)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = Ios.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.label)
    }
}
