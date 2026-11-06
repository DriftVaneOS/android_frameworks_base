/*
 * SPDX-FileCopyrightText: DriftVaneOS
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <stddef.h>

namespace android {

// DriftVaneOS: called for every boot animation frame; vibrates on the DRIFT and VANE hits and
// once, gently, when the loading loop starts.
void driftVaneBootHaptics(size_t partIdx, int repeatIdx, size_t frameIdxInPart);

} // namespace android
