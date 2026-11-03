/*
 * Copyright (C) 2025-2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.axdynamicbar.data.source

import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DriftVaneOS: crDroid feeds sports scores and "Now Playing" into the dynamic bar from its
 * QuickLook service, which LineageOS does not have. This keeps the same API so the rest of the
 * dynamic bar is unchanged, but never produces sports / now-playing events.
 */
@SysUISingleton
class SmartspaceIslandManager @Inject constructor() {
    private val _sportsEvents = MutableStateFlow<List<IslandEvent.Sports>>(emptyList())
    val sportsEvents: StateFlow<List<IslandEvent.Sports>> = _sportsEvents.asStateFlow()

    private val _nowPlayingEvent = MutableStateFlow<IslandEvent.NowPlaying?>(null)
    val nowPlayingEvent: StateFlow<IslandEvent.NowPlaying?> = _nowPlayingEvent.asStateFlow()

    fun startListening() {}

    fun stopListening() {
        _sportsEvents.value = emptyList()
        _nowPlayingEvent.value = null
    }

    @Suppress("UNUSED_PARAMETER")
    fun clearSportsEvent(key: String, team1: String? = null, team2: String? = null) {}

    fun dismissNowPlaying() {
        _nowPlayingEvent.value = null
    }
}
