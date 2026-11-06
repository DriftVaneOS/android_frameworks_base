/*
 * SPDX-FileCopyrightText: DriftVaneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.driftvane

import android.content.Context
import android.os.SystemProperties
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log

/**
 * DriftVaneOS: the one subtle tick while the boot animation is loading. SystemUI starts during
 * that part of the boot, and Android's vibrator service is already up, so this always works
 * (the boot animation itself adds the two hard hits on DRIFT and VANE).
 */
object DriftBootTick {
    private const val TAG = "DriftBootTick"

    fun onSystemUiStart(context: Context) {
        try {
            // only during boot, not when SystemUI restarts later
            if (SystemProperties.get("sys.boot_completed") == "1") return
            val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
            if (!vibrator.hasVibrator()) return
            vibrator.vibrate(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK),
            )
        } catch (e: Exception) {
            Log.w(TAG, "boot tick failed", e)
        }
    }
}
