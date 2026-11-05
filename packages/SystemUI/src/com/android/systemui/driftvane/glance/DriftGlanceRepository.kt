/*
 * SPDX-FileCopyrightText: DriftVaneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.driftvane.glance

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.BatteryManager
import android.os.Handler
import android.os.UserHandle
import android.provider.AlarmClock
import android.util.Log
import com.android.systemui.axdynamicbar.data.source.MediaIslandManager
import com.android.systemui.axdynamicbar.domain.AxDynamicBarInteractor
import com.android.systemui.axdynamicbar.ui.AxDynamicBarKeyguardExpansion
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.settings.UserTracker
import com.android.systemui.util.settings.SecureSettings
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** DriftVaneOS: data for the lock screen glance stack (music, battery, next alarm). */
@SysUISingleton
class DriftGlanceRepository
@Inject
constructor(
    @Application private val context: Context,
    @Application private val scope: CoroutineScope,
    @Main private val mainHandler: Handler,
    private val secureSettings: SecureSettings,
    private val broadcastDispatcher: BroadcastDispatcher,
    private val userTracker: UserTracker,
    private val activityStarter: ActivityStarter,
    val media: MediaIslandManager,
    dynamicBar: AxDynamicBarInteractor,
    keyguardExpansion: AxDynamicBarKeyguardExpansion,
) {
    companion object {
        private const val TAG = "DriftGlance"
        /** Secure setting, 1 = show the glance stack on the lock screen (default). */
        const val KEY_ENABLED = "driftvane_lockscreen_glance"
    }

    data class Battery(val level: Int, val plugged: Boolean, val full: Boolean)

    // ------------------------------------------------------------------ on/off switch
    private val _enabled = MutableStateFlow(readEnabled())
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private fun readEnabled(): Boolean =
        try {
            secureSettings.getIntForUser(KEY_ENABLED, 1, UserHandle.USER_CURRENT) == 1
        } catch (e: Exception) {
            true
        }

    init {
        try {
            secureSettings.registerContentObserverForUserSync(
                KEY_ENABLED,
                false,
                object : ContentObserver(mainHandler) {
                    override fun onChange(selfChange: Boolean) {
                        _enabled.value = readEnabled()
                    }
                },
                UserHandle.USER_ALL,
            )
        } catch (e: Exception) {
            Log.w(TAG, "couldn't watch $KEY_ENABLED", e)
        }
    }

    // ------------------------------------------------------------------ battery
    private val batteryManager: BatteryManager? = context.getSystemService(BatteryManager::class.java)

    private fun readBattery(): Battery {
        val level = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        val charging = batteryManager?.isCharging ?: false
        return Battery(level.coerceIn(0, 100), charging, level >= 100 && charging)
    }

    val battery: StateFlow<Battery> =
        broadcastDispatcher
            .broadcastFlow(IntentFilter(Intent.ACTION_BATTERY_CHANGED)) { intent, _ ->
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                val pct = if (level >= 0 && scale > 0) level * 100 / scale else readBattery().level
                Battery(pct.coerceIn(0, 100), plugged, plugged && status == BatteryManager.BATTERY_STATUS_FULL)
            }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), readBattery())

    // ------------------------------------------------------------------ next alarm
    private val alarmManager: AlarmManager? = context.getSystemService(AlarmManager::class.java)

    private fun readAlarm(): Long? =
        try {
            alarmManager?.getNextAlarmClock(userTracker.userId)?.triggerTime
        } catch (e: Exception) {
            null
        }

    val nextAlarm: StateFlow<Long?> =
        broadcastDispatcher
            .broadcastFlow(IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED), UserHandle.ALL)
            .onStart { emit(Unit) }
            .map { readAlarm() }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), null)

    // ------------------------------------------------------------------ when to show
    private val onLockScreen =
        combine(
            dynamicBar.isOnKeyguard,
            dynamicBar.isDozing,
            dynamicBar.dozeAmount.map { it > 0f }.distinctUntilChanged(),
            dynamicBar.isBouncerShowing,
            keyguardExpansion.isExpanded,
        ) { onKg, dozing, dozeAmt, bouncer, barExpanded ->
            onKg && !dozing && !dozeAmt && !bouncer && !barExpanded
        }

    val canShow: StateFlow<Boolean> =
        combine(enabled, onLockScreen) { on, kg -> on && kg }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, false)

    // ------------------------------------------------------------------ actions
    fun togglePlayPause() = media.togglePlayPause()

    fun skipNext() = media.skipNext()

    fun skipPrev() = media.skipPrev()

    fun openMusic() = media.openMediaApp()

    fun openAlarms() {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activityStarter.postStartActivityDismissingKeyguard(intent, 0)
    }
}
