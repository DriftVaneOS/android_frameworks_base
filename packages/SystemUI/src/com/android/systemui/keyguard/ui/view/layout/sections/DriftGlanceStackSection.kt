/*
 * SPDX-FileCopyrightText: DriftVaneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.Context
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.axion.compose.host.AxComposeView
import com.android.compose.theme.PlatformTheme
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.driftvane.glance.DriftGlanceRepository
import com.android.systemui.driftvane.glance.DriftGlanceStack
import com.android.systemui.driftvane.glance.DriftGlanceStackMaxHeight
import com.android.systemui.keyguard.domain.interactor.KeyguardBlueprintInteractor
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * DriftVaneOS: the glance stack. Swipeable cards (music, battery, next alarm) that sit right
 * below the fingerprint icon (which is drawn over the under-display sensor) and above the
 * bottom shortcut buttons.
 */
class DriftGlanceStackSection
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    @Application private val scope: CoroutineScope,
    private val repo: DriftGlanceRepository,
    private val blueprintInteractor: Lazy<KeyguardBlueprintInteractor>,
) : KeyguardSection() {

    private val viewId = R.id.driftvane_glance_stack
    private var settingJob: Job? = null

    private fun dp(v: Float): Int = (v * context.resources.displayMetrics.density + 0.5f).toInt()

    override fun addViews(constraintLayout: ConstraintLayout) {
        val view = AxComposeView(context).apply { id = viewId }
        constraintLayout.addView(view)
    }

    override fun bindData(constraintLayout: ConstraintLayout) {
        val view: AxComposeView = constraintLayout.requireViewById(viewId)
        view.setContent { PlatformTheme { DriftGlanceStack(repo) } }

        // Turning the switch on/off re-lays out the lock screen (gives notifications the
        // space back, or takes it).
        settingJob?.cancel()
        settingJob =
            scope.launch {
                repo.enabled.drop(1).collect {
                    blueprintInteractor.get().refreshBlueprint()
                }
            }
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        val on = repo.enabled.value
        val side = dp(16f)
        constraintSet.apply {
            // Below the fingerprint icon (drawn over the sensor) and above the bottom shortcut
            // buttons, so it never covers either. Card height follows the space there.
            constrainWidth(viewId, ConstraintSet.MATCH_CONSTRAINT)
            constrainHeight(viewId, ConstraintSet.MATCH_CONSTRAINT)
            constrainMaxHeight(viewId, dp(DriftGlanceStackMaxHeight.value))
            connect(viewId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, side)
            connect(viewId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, side)
            connect(viewId, ConstraintSet.TOP, R.id.device_entry_icon_view, ConstraintSet.BOTTOM, dp(6f))
            connect(viewId, ConstraintSet.BOTTOM, R.id.start_button, ConstraintSet.TOP, dp(6f))
            setVerticalBias(viewId, 0f)
            setVisibility(viewId, if (on) View.VISIBLE else View.GONE)
        }
    }

    override fun removeViews(constraintLayout: ConstraintLayout) {
        settingJob?.cancel()
        settingJob = null
        constraintLayout.removeView(viewId)
    }
}
