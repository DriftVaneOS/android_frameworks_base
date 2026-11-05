/*
 * SPDX-FileCopyrightText: DriftVaneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.driftvane.qs

import android.database.ContentObserver
import android.os.Handler
import android.os.UserHandle
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.util.settings.SecureSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DriftVaneOS: "Classic" Quick Settings (Android 13 style): every tile big, two per row, the whole
 * tile filled with the accent color when on, one corner shape. Read from Compose.
 */
object DriftQsStyle {
    /** Compose state, so tiles redraw when the switch changes. */
    var classic by mutableStateOf(true)
        internal set
}

@SysUISingleton
class DriftQsStyleRepository
@Inject
constructor(
    @Main private val mainHandler: Handler,
    private val secureSettings: SecureSettings,
) {
    companion object {
        private const val TAG = "DriftQsStyle"
        /** Secure setting, 1 = Classic Quick Settings (default), 0 = stock. */
        const val KEY_CLASSIC = "driftvane_qs_classic"
    }

    private val _classic = MutableStateFlow(read())
    val classic: StateFlow<Boolean> = _classic.asStateFlow()

    private fun read(): Boolean =
        try {
            secureSettings.getIntForUser(KEY_CLASSIC, 1, UserHandle.USER_CURRENT) == 1
        } catch (e: Exception) {
            true
        }

    private fun update() {
        val v = read()
        _classic.value = v
        DriftQsStyle.classic = v
    }

    init {
        DriftQsStyle.classic = _classic.value
        try {
            secureSettings.registerContentObserverForUserSync(
                KEY_CLASSIC,
                false,
                object : ContentObserver(mainHandler) {
                    override fun onChange(selfChange: Boolean) {
                        update()
                    }
                },
                UserHandle.USER_ALL,
            )
        } catch (e: Exception) {
            Log.w(TAG, "couldn't watch $KEY_CLASSIC", e)
        }
    }
}
