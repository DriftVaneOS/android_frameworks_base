/*
 * SPDX-FileCopyrightText: DriftVaneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.driftvane

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log

/**
 * DriftVaneOS: "Restart SystemUI" from DriftVane Enhancements. Settings sends
 * [ACTION_RESTART]; only apps holding the STATUS_BAR permission (system apps) can send it.
 * SystemUI exits and Android starts it again right away (it's a persistent app).
 */
object DriftSystemUiRestart {
    const val ACTION_RESTART = "org.driftvane.action.RESTART_SYSTEMUI"
    private const val TAG = "DriftSystemUiRestart"
    private var registered = false

    fun register(context: Context) {
        if (registered) return
        registered = true
        try {
            context.registerReceiver(
                object : BroadcastReceiver() {
                    override fun onReceive(c: Context, intent: Intent) {
                        Log.i(TAG, "restarting SystemUI")
                        // a moment for the Settings toast to show
                        Handler(Looper.getMainLooper()).postDelayed(
                            { Process.killProcess(Process.myPid()) },
                            500L,
                        )
                    }
                },
                IntentFilter(ACTION_RESTART),
                android.Manifest.permission.STATUS_BAR,
                null,
                Context.RECEIVER_EXPORTED,
            )
        } catch (e: Exception) {
            Log.w(TAG, "couldn't listen for restart requests", e)
        }
    }
}
