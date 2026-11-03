/*
 * SPDX-FileCopyrightText: DriftVaneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.driftvane

import android.content.Context
import android.content.res.ColorStateList
import android.database.ContentObserver
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.util.AttributeSet
import android.widget.ImageView
import com.android.systemui.plugins.DarkIconDispatcher
import com.android.systemui.plugins.DarkIconDispatcher.DarkReceiver

/** DriftVaneOS logo next to the status bar clock. Settings.System driftvane_statusbar_logo. */
class DriftVaneLogoView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : ImageView(context, attrs), DarkReceiver {

    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = update()
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(KEY),
            false,
            observer,
            UserHandle.USER_ALL,
        )
        update()
    }

    override fun onDetachedFromWindow() {
        context.contentResolver.unregisterContentObserver(observer)
        super.onDetachedFromWindow()
    }

    private fun update() {
        val on =
            Settings.System.getIntForUser(context.contentResolver, KEY, 0, UserHandle.USER_CURRENT) == 1
        visibility = if (on) VISIBLE else GONE
    }

    override fun onDarkChanged(areas: ArrayList<Rect>, darkIntensity: Float, tint: Int) {
        imageTintList = ColorStateList.valueOf(DarkIconDispatcher.getTint(areas, this, tint))
    }

    companion object {
        const val KEY = "driftvane_statusbar_logo"
    }
}
