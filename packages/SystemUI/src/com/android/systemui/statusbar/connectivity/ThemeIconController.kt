/*
 * SPDX-FileCopyrightText: DriftVaneOS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.connectivity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DriftVaneOS: the small part of crDroid's ThemeIconController that the themed battery needs.
 * [themeVersion] goes up when the theme (e.g. a battery style overlay) changes, so the battery
 * re-reads its shape from resources.
 */
object ThemeIconController {
    private val _themeVersion = MutableStateFlow(0L)
    val themeVersion: StateFlow<Long> = _themeVersion.asStateFlow()

    fun bump() {
        _themeVersion.value++
    }
}
