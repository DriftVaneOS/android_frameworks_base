/*
 * SPDX-FileCopyrightText: DriftVaneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.driftvane.glance

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

private enum class GlanceCard { MEDIA, BATTERY, ALARM }

private val CardShape = RoundedCornerShape(22.dp)

/** Most height the lock screen gives the stack (it sits between fingerprint and shortcuts). */
val DriftGlanceStackMaxHeight = 96.dp

@Composable
fun DriftGlanceStack(repo: DriftGlanceRepository, modifier: Modifier = Modifier) {
    val show by repo.canShow.collectAsStateWithLifecycle()
    val media by repo.media.mediaEvent.collectAsStateWithLifecycle()
    val battery by repo.battery.collectAsStateWithLifecycle()
    val alarm by repo.nextAlarm.collectAsStateWithLifecycle()
    val alpha by animateFloatAsState(if (show) 1f else 0f, label = "driftGlanceAlpha")
    if (alpha <= 0f) return

    val hasMedia = media?.track?.isNotBlank() == true
    val cards = buildList {
        if (hasMedia) add(GlanceCard.MEDIA)
        add(GlanceCard.BATTERY)
        if (alarm != null) add(GlanceCard.ALARM)
    }
    val pager = rememberPagerState(0) { cards.size }
    val cs = MaterialTheme.colorScheme
    val layers = cards.size > 1

    Column(
        modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (layers) {
                // the cards behind, peeking out under the front one
                Box(
                    Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 8.dp)
                        .clip(CardShape).background(cs.surfaceContainerLow)
                )
                Box(
                    Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                        .clip(CardShape).background(cs.surfaceContainer)
                )
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().padding(bottom = if (layers) 8.dp else 0.dp),
                pageSpacing = 12.dp,
                userScrollEnabled = show,
            ) { page ->
                when (cards.getOrNull(page)) {
                    GlanceCard.MEDIA -> media?.let {
                        MediaCard(it.track, it.artist, it.isPlaying, repo, show)
                    }
                    GlanceCard.BATTERY -> BatteryCard(battery)
                    GlanceCard.ALARM -> alarm?.let { AlarmCard(it, repo, show) }
                    null -> {}
                }
            }
        }
        if (layers) {
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(cards.size) { i ->
                    val selected = i == pager.currentPage
                    Box(
                        Modifier.width(if (selected) 14.dp else 5.dp).height(5.dp)
                            .clip(CircleShape)
                            .background(if (selected) cs.primary else Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}

@Composable
private fun CardRow(
    background: Color,
    onClick: (() -> Unit)?,
    content: @Composable RowScope.() -> Unit,
) {
    var m = Modifier.fillMaxSize().clip(CardShape).background(background)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(m.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        content()
    }
}

@Composable
private fun Badge(icon: ImageVector, bg: Color, tint: Color) {
    Box(Modifier.size(38.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun MediaCard(
    track: String,
    artist: String,
    playing: Boolean,
    repo: DriftGlanceRepository,
    enabled: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    CardRow(cs.primary, if (enabled) ({ repo.openMusic() }) else null) {
        Badge(Icons.Filled.MusicNote, cs.onPrimary.copy(alpha = 0.18f), cs.onPrimary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track, color = cs.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (artist.isNotBlank()) {
                Text(artist, color = cs.onPrimary.copy(alpha = 0.8f), fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        MediaButton(Icons.Filled.SkipPrevious, cs.onPrimary, enabled) { repo.skipPrev() }
        MediaButton(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, cs.onPrimary, enabled) {
            repo.togglePlayPause()
        }
        MediaButton(Icons.Filled.SkipNext, cs.onPrimary, enabled) { repo.skipNext() }
    }
}

@Composable
private fun MediaButton(icon: ImageVector, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun BatteryCard(b: DriftGlanceRepository.Battery) {
    val cs = MaterialTheme.colorScheme
    val low = b.level <= 15 && !b.plugged
    CardRow(cs.surfaceContainerHigh, null) {
        Badge(
            if (b.plugged) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryFull,
            cs.primary.copy(alpha = 0.18f),
            if (low) cs.error else cs.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${b.level}%", color = cs.onSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                when {
                    b.full -> "Charged"
                    b.plugged -> "Charging"
                    else -> "Battery"
                },
                color = cs.onSurfaceVariant, fontSize = 12.sp, maxLines = 1,
            )
        }
        Box(Modifier.width(88.dp).height(6.dp).clip(CircleShape).background(cs.surfaceContainerHighest)) {
            Box(
                Modifier.fillMaxWidth(b.level / 100f).fillMaxHeight().clip(CircleShape)
                    .background(if (low) cs.error else cs.primary)
            )
        }
    }
}

@Composable
private fun AlarmCard(time: Long, repo: DriftGlanceRepository, enabled: Boolean) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }
    val pattern = if (DateFormat.is24HourFormat(context)) "EEE HH:mm" else "EEE h:mm a"
    val mins = ((time - now) / 60_000L).coerceAtLeast(0L)
    val until = when {
        mins < 1 -> "now"
        mins < 60 -> "in ${mins}m"
        else -> "in ${mins / 60}h ${mins % 60}m"
    }
    CardRow(cs.surfaceContainerHigh, if (enabled) ({ repo.openAlarms() }) else null) {
        Badge(Icons.Filled.Alarm, cs.primary.copy(alpha = 0.18f), cs.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(DateFormat.format(pattern, time).toString(), color = cs.onSurface, fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("Next alarm", color = cs.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
        }
        Text(until, color = cs.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
