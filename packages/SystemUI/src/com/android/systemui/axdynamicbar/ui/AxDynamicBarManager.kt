package com.android.systemui.axdynamicbar.ui

import android.content.Context
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.driftvane.DriftBootTick
import javax.inject.Inject

@SysUISingleton
class AxDynamicBarManager
@Inject
constructor(
    private val viewModel: AxDynamicBarChipViewModel,
    private val expandedPanel: AxDynamicBarExpandedPanel,
    @Application private val context: Context,
) : CoreStartable {

    override fun start() {
        // DriftVaneOS: one subtle tick while the boot animation is loading
        DriftBootTick.onSystemUiStart(context)
        // DriftVaneOS: "Restart SystemUI" button in DriftVane Enhancements
        com.android.systemui.driftvane.DriftSystemUiRestart.register(context)
        viewModel.interactor.init()
        expandedPanel.init()
    }
}

