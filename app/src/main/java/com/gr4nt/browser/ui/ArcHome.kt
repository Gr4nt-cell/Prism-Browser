package com.gr4nt.browser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gr4nt.browser.browser.AppLinks
import com.gr4nt.browser.browser.AppPrompt
import com.gr4nt.browser.browser.Browser
import com.gr4nt.browser.browser.BrowserTab
import com.gr4nt.browser.data.Site
import com.gr4nt.browser.data.Store
import com.gr4nt.browser.data.hostOf

/** Pinned sites first (pin with ^ › pin), then the most visited ones, like Arc's Top Sites. */
fun topSites(limit: Int = 8): List<Site> =
    (Store.favorites.items + Store.frequentlyVisited(limit)).distinctBy { hostOf(it.url).removePrefix("www.") }.take(limit)

/**
 * Arc Search new tab: nothing but your Top Sites as round icons; the search field is the bar
 * at the bottom (the keyboard opens straight away on a new tab).
 */
@Composable
fun ArcHome(tab: BrowserTab?) {
    val c = Ios.c
    val sites = topSites()
    Column(
        Modifier.fillMaxSize().wallpaper().statusBarsPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        if (tab?.isPrivate == true) {
            Icon(Icons.Outlined.VisibilityOff, null, tint = c.secondary, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(8.dp))
            Text("Private Tab", style = Ios.title3, color = c.label)
            Spacer(Modifier.height(4.dp))
            Text(
                "Pages you visit here aren't saved to your history or Archive.",
                style = Ios.footnote, color = c.secondary, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
        }
        if (sites.isNotEmpty()) {
            Text("Top Sites", style = Ios.footnote.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp), color = c.secondary)
            Spacer(Modifier.height(14.dp))
            Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                sites.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { s -> TopSite(s, Modifier.weight(1f)) }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            Text("Search or type a URL below", style = Ios.subhead, color = c.secondary)
        }
        Spacer(Modifier.weight(1.4f))
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun TopSite(s: Site, modifier: Modifier) {
    val c = Ios.c
    val pinned = Store.favorites.items.any { it.url == s.url }
    Column(
        modifier.pressable(scaleTo = 0.88f, onLongClick = { if (pinned) Store.favorites.remove(s.url) }) { Browser.load(s.url) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(58.dp).glassCard(CircleShape), contentAlignment = Alignment.Center) {
            SiteTile(s.url, s.title, size = 34.dp, corner = 17.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            s.title.ifBlank { hostOf(s.url) }.substringBefore(" - ").substringBefore(" | "),
            style = Ios.caption, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

/** "Open in YouTube" banner: the link keeps loading here; one tap hands it to the app. */
@Composable
fun AppPromptBanner(p: AppPrompt?, modifier: Modifier = Modifier) {
    LaunchedEffect(p) {
        if (p != null) { kotlinx.coroutines.delay(8000); if (AppLinks.prompt == p) AppLinks.prompt = null }
    }
    AnimatedVisibility(
        p != null, modifier,
        enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { -it * 2 } + fadeIn(),
        exit = slideOutVertically { -it * 2 } + fadeOut()
    ) {
        val prompt = p ?: return@AnimatedVisibility
        val c = Ios.c
        Row(
            Modifier.statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp).widthIn(max = 520.dp).fillMaxWidth()
                .glass(RoundedCornerShape(24.dp), strong = true).padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = prompt.app.icon
            if (icon != null) Image(icon.asImageBitmap(), null, Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Open in ${prompt.app.label}?", style = Ios.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 1)
                Text(hostOf(prompt.url), style = Ios.caption, color = c.secondary, maxLines = 1)
            }
            Text(
                "Open", style = Ios.subhead.copy(fontWeight = FontWeight.SemiBold), color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.pressable(scaleTo = 0.9f) { Browser.activity?.let { AppLinks.open(it, prompt.url, prompt.app) } }
                    .clip(CircleShape).background(c.tint).padding(horizontal = 16.dp, vertical = 7.dp)
            )
            InlineIconButton(Icons.Outlined.Close, "Dismiss", size = 36.dp, iconSize = 16.dp, tint = c.secondary) { AppLinks.prompt = null }
        }
    }
}

