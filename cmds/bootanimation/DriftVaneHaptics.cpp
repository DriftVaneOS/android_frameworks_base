/*
 * SPDX-FileCopyrightText: DriftVaneOS
 * SPDX-License-Identifier: Apache-2.0
 */

// DriftVaneOS: haptics for the boot animation.
//   part0 frame 24: DRIFT crashes down        -> strong hit
//   part0 frame 86: VANE smashes into it      -> strong hit
//   (the one subtle tick while loading comes from SystemUI when it starts)
// Talks to the vibrator HAL directly (the framework isn't up yet). If anything is missing,
// it does nothing.

#define LOG_TAG "DriftVaneHaptics"

#include "DriftVaneHaptics.h"

#include <aidl/android/hardware/vibrator/IVibrator.h>
#include <android/binder_manager.h>
#include <utils/Log.h>

#include <memory>
#include <mutex>
#include <thread>

namespace android {

using aidl::android::hardware::vibrator::Effect;
using aidl::android::hardware::vibrator::EffectStrength;
using aidl::android::hardware::vibrator::IVibrator;

namespace {

constexpr size_t kDriftFrame = 24;
constexpr size_t kVaneFrame = 86;

std::shared_ptr<IVibrator> vibrator() {
    // Keep looking until the vibrator HAL is up (it may start after the boot animation does).
    static std::mutex lock;
    static std::shared_ptr<IVibrator> vib;
    std::lock_guard<std::mutex> guard(lock);
    if (vib == nullptr) {
        const std::string name = std::string(IVibrator::descriptor) + "/default";
        ndk::SpAIBinder binder(AServiceManager_checkService(name.c_str()));
        if (binder.get() != nullptr) {
            vib = IVibrator::fromBinder(binder);
        }
        if (vib == nullptr) {
            ALOGW("vibrator HAL not up yet");
        }
    }
    return vib;
}

bool tryEffect(const std::shared_ptr<IVibrator>& vib, Effect effect, EffectStrength strength) {
    int32_t lengthMs = 0;
    return vib->perform(effect, strength, nullptr, &lengthMs).isOk();
}

void buzz(bool strong) {
    // off the render thread, so a slow HAL never drops frames
    std::thread([strong]() {
        std::shared_ptr<IVibrator> vib = vibrator();
        if (vib == nullptr) return;
        if (strong) {
            if (tryEffect(vib, Effect::THUD, EffectStrength::STRONG)) return;
            if (tryEffect(vib, Effect::HEAVY_CLICK, EffectStrength::STRONG)) return;
            (void)vib->on(60, nullptr);
        } else {
            if (tryEffect(vib, Effect::TICK, EffectStrength::LIGHT)) return;
            if (tryEffect(vib, Effect::CLICK, EffectStrength::LIGHT)) return;
            (void)vib->on(12, nullptr);
        }
    }).detach();
}

} // namespace

void driftVaneBootHaptics(size_t partIdx, int repeatIdx, size_t frameIdxInPart) {
    // The subtle "loading" tick comes from SystemUI (Android's vibrator service), so the boot
    // vibrates for sure even if the HAL isn't reachable from here.
    if (partIdx == 0 && repeatIdx == 0 &&
            (frameIdxInPart == kDriftFrame || frameIdxInPart == kVaneFrame)) {
        buzz(true);
    }
}

} // namespace android
