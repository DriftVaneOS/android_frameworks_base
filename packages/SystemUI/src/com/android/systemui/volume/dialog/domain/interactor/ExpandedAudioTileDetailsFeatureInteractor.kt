/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.volume.dialog.domain.interactor

import android.content.Context
import android.os.UserHandle
import android.provider.Settings
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.qs.flags.ExpandedAudioDetailedView
import com.android.systemui.qs.flags.QsDetailedView
import javax.inject.Inject

@SysUISingleton
class ExpandedAudioTileDetailsFeatureInteractor
@Inject
constructor(@Application private val context: Context) {
    private val isEnabled = QsDetailedView.isEnabled && ExpandedAudioDetailedView.isEnabled

    fun isEnabled(): Boolean {
        return isEnabled
    }

    /**
     * DriftVaneOS: the volume dialog is horizontal at the top of the screen (TouchWiz style) when
     * the user turns it on, or when the platform feature asks for it.
     */
    fun isVolumeDialogVertical(): Boolean {
        val top =
            Settings.System.getIntForUser(
                context.contentResolver,
                KEY_VOLUME_PANEL_TOP,
                0,
                UserHandle.USER_CURRENT,
            ) == 1
        return !(isEnabled || top)
    }

    companion object {
        const val KEY_VOLUME_PANEL_TOP = "driftvane_volume_panel_top"
    }
}
