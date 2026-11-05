/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.keyguard.ui.view

import android.companion.virtualdevice.flags.Flags
import android.content.Context
import android.database.ContentObserver
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.graphics.drawable.AnimatedStateListDrawable
import android.graphics.drawable.AnimatedVectorDrawable
import android.util.AttributeSet
import android.util.StateSet
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.android.systemui.common.ui.view.TouchHandlingView
import com.android.systemui.log.TouchHandlingViewLogger
import com.android.systemui.res.R

class DeviceEntryIconView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttrs: Int = 0,
    logger: TouchHandlingViewLogger? = null,
) : FrameLayout(context, attrs, defStyleAttrs) {

    val touchHandlingView: TouchHandlingView =
        TouchHandlingView(
            context = context,
            attrs = attrs,
            longPressDuration = {
                if (Flags.viewconfigurationApis())
                    ViewConfiguration.get(context).longPressTimeoutMillis.toLong()
                else ViewConfiguration.getLongPressTimeout().toLong()
            },
            allowedTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop(),
            logger = logger,
        )
    val iconView: ImageView = ImageView(context, attrs).apply { id = R.id.device_entry_icon_fg }
    val bgView: ImageView = ImageView(context, attrs).apply { id = R.id.device_entry_icon_bg }
    val aodFpDrawable: LottieDrawable = LottieDrawable()
    var accessibilityHintType: AccessibilityHintType = AccessibilityHintType.NONE

    private var animatedIconDrawable: AnimatedStateListDrawable = AnimatedStateListDrawable()

    // DriftVaneOS: swappable fingerprint icon (Settings.System driftvane_fp_icon)
    private val fpDrawable: DrawableWrapper =
        object : DrawableWrapper(context.getDrawable(R.drawable.ic_fingerprint)!!) {}
    private val fpObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = applyFingerprintStyle()
        }

    init {
        setupIconStates()
        setupIconTransitions()
        setupAccessibilityDelegate()

        // Ordering matters. From background to foreground we want:
        //     bgView, iconView, longpressHandlingView overlay
        addBgImageView()
        addIconImageView()
        addTouchHandlingView()
    }

    private fun setupAccessibilityDelegate() {
        accessibilityDelegate =
            object : AccessibilityDelegate() {
                private val accessibilityBouncerHint =
                    AccessibilityNodeInfo.AccessibilityAction(
                        AccessibilityNodeInfoCompat.ACTION_CLICK,
                        resources.getString(R.string.accessibility_bouncer),
                    )
                private val accessibilityEnterHint =
                    AccessibilityNodeInfo.AccessibilityAction(
                        AccessibilityNodeInfoCompat.ACTION_CLICK,
                        resources.getString(R.string.accessibility_enter_hint),
                    )

                override fun onInitializeAccessibilityNodeInfo(
                    v: View,
                    info: AccessibilityNodeInfo,
                ) {
                    super.onInitializeAccessibilityNodeInfo(v, info)
                    when (accessibilityHintType) {
                        AccessibilityHintType.BOUNCER -> info.addAction(accessibilityBouncerHint)
                        AccessibilityHintType.ENTER -> info.addAction(accessibilityEnterHint)
                        AccessibilityHintType.NONE -> return
                    }
                }
            }
    }

    /**
     * Setups different icon states.
     * - All lottie views will require a LottieOnCompositionLoadedListener to update
     *   LottieProperties (like color) of the view.
     * - Drawable properties can be updated using ImageView properties like imageTintList.
     */
    private fun setupIconStates() {
        // Lockscreen States
        // LOCK
        animatedIconDrawable.addState(
            getIconState(IconType.LOCK, false),
            context.getDrawable(R.drawable.ic_lock)!!,
            R.id.locked,
        )
        // UNLOCK
        animatedIconDrawable.addState(
            getIconState(IconType.UNLOCK, false),
            context.getDrawable(R.drawable.ic_unlocked)!!,
            R.id.unlocked,
        )
        // FINGERPRINT
        animatedIconDrawable.addState(
            getIconState(IconType.FINGERPRINT, false),
            fpDrawable,
            R.id.locked_fp,
        )

        // AOD states
        // LOCK
        animatedIconDrawable.addState(
            getIconState(IconType.LOCK, true),
            context.getDrawable(R.drawable.ic_lock_aod)!!,
            R.id.locked_aod,
        )
        // UNLOCK
        animatedIconDrawable.addState(
            getIconState(IconType.UNLOCK, true),
            context.getDrawable(R.drawable.ic_unlocked_aod)!!,
            R.id.unlocked_aod,
        )
        // FINGERPRINT
        LottieCompositionFactory.fromRawRes(mContext, R.raw.udfps_aod_fp).addListener { result ->
            aodFpDrawable.setComposition(result)
        }
        animatedIconDrawable.addState(
            getIconState(IconType.FINGERPRINT, true),
            aodFpDrawable,
            R.id.udfps_aod_fp,
        )

        // WILDCARD: should always be the last state added since any states will match with this
        // and therefore won't get matched with subsequent states.
        animatedIconDrawable.addState(
            StateSet.WILD_CARD,
            context.getDrawable(R.color.transparent)!!,
            R.id.no_icon,
        )
    }

    private fun setupIconTransitions() {
        // LockscreenFp <=> LockscreenUnlocked
        animatedIconDrawable.addTransition(
            R.id.locked_fp,
            R.id.unlocked,
            context.getDrawable(R.drawable.fp_to_unlock) as AnimatedVectorDrawable,
            /* reversible */ false,
        )
        animatedIconDrawable.addTransition(
            R.id.unlocked,
            R.id.locked_fp,
            context.getDrawable(R.drawable.unlock_to_fp) as AnimatedVectorDrawable,
            /* reversible */ false,
        )

        // LockscreenLocked <=> AodLocked
        animatedIconDrawable.addTransition(
            R.id.locked_aod,
            R.id.locked,
            context.getDrawable(R.drawable.lock_aod_to_ls) as AnimatedVectorDrawable,
            /* reversible */ false,
        )
        animatedIconDrawable.addTransition(
            R.id.locked,
            R.id.locked_aod,
            context.getDrawable(R.drawable.lock_ls_to_aod) as AnimatedVectorDrawable,
            /* reversible */ false,
        )

        // LockscreenUnlocked <=> AodUnlocked
        animatedIconDrawable.addTransition(
            R.id.unlocked_aod,
            R.id.unlocked,
            context.getDrawable(R.drawable.unlocked_aod_to_ls) as AnimatedVectorDrawable,
            /* reversible */ false,
        )
        animatedIconDrawable.addTransition(
            R.id.unlocked,
            R.id.unlocked_aod,
            context.getDrawable(R.drawable.unlocked_ls_to_aod) as AnimatedVectorDrawable,
            /* reversible */ false,
        )

        // LockscreenLocked <=> LockscreenUnlocked
        animatedIconDrawable.addTransition(
            R.id.locked,
            R.id.unlocked,
            context.getDrawable(R.drawable.lock_to_unlock) as AnimatedVectorDrawable,
            /* reversible */ false,
        )
        animatedIconDrawable.addTransition(
            R.id.unlocked,
            R.id.locked,
            context.getDrawable(R.drawable.unlocked_to_locked) as AnimatedVectorDrawable,
            /* reversible */ false,
        )

        // LockscreenFingerprint => LockscreenLocked
        animatedIconDrawable.addTransition(
            R.id.locked_fp,
            R.id.locked,
            context.getDrawable(R.drawable.fp_to_locked) as AnimatedVectorDrawable,
            /* reversible */ false,
        )

        // LockscreenUnlocked <=> AodLocked
        animatedIconDrawable.addTransition(
            R.id.unlocked,
            R.id.locked_aod,
            context.getDrawable(R.drawable.unlocked_to_aod_lock) as AnimatedVectorDrawable,
            /* reversible */ false,
        )
    }

    private fun addTouchHandlingView() {
        addView(touchHandlingView)
        val lp = touchHandlingView.layoutParams as LayoutParams
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT
        touchHandlingView.layoutParams = lp
    }

    private fun addIconImageView() {
        iconView.scaleType = ImageView.ScaleType.CENTER_CROP
        iconView.setImageDrawable(animatedIconDrawable)
        addView(iconView)
        val lp = iconView.layoutParams as LayoutParams
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT
        lp.gravity = Gravity.CENTER
        iconView.layoutParams = lp
    }

    private fun addBgImageView() {
        bgView.setImageDrawable(context.getDrawable(R.drawable.fingerprint_bg))
        addView(bgView)
        val lp = bgView.layoutParams as LayoutParams
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT
        bgView.layoutParams = lp
        bgView.alpha = 0f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.System.getUriFor(FP_STYLE_KEY), false, fpObserver, UserHandle.USER_ALL
            )
        }
        applyFingerprintStyle()
    }

    override fun onDetachedFromWindow() {
        runCatching { context.contentResolver.unregisterContentObserver(fpObserver) }
        super.onDetachedFromWindow()
    }

    /** DriftVaneOS: pick the fingerprint icon style; 0 keeps the stock icon. */
    private fun applyFingerprintStyle() {
        val style = runCatching {
            Settings.System.getIntForUser(context.contentResolver, FP_STYLE_KEY, 0, UserHandle.USER_CURRENT)
        }.getOrDefault(0)
        val res = when (style) {
            1 -> R.drawable.dv_fp_ripple
            2 -> R.drawable.dv_fp_dots
            3 -> R.drawable.dv_fp_ring
            4 -> R.drawable.dv_fp_vane
            5 -> R.drawable.dv_fp_scan
            else -> R.drawable.ic_fingerprint
        }
        val d: Drawable = context.getDrawable(res)?.mutate() ?: return
        d.bounds = fpDrawable.bounds
        iconView.imageTintList?.let { d.setTintList(it) }
        fpDrawable.drawable = d
        fpDrawable.invalidateSelf()
        iconView.invalidate()
    }

    fun getIconState(icon: IconType, aod: Boolean): IntArray {
        val lockIconState = IntArray(2)
        when (icon) {
            IconType.LOCK -> lockIconState[0] = android.R.attr.state_first
            IconType.UNLOCK -> lockIconState[0] = android.R.attr.state_last
            IconType.FINGERPRINT -> lockIconState[0] = android.R.attr.state_middle
            IconType.NONE -> return StateSet.NOTHING
        }
        if (aod) {
            lockIconState[1] = android.R.attr.state_single
        } else {
            lockIconState[1] = -android.R.attr.state_single
        }
        return lockIconState
    }

    enum class IconType(val contentDescriptionResId: Int) {
        LOCK(R.string.accessibility_lock_icon),
        UNLOCK(R.string.accessibility_unlock_button),
        FINGERPRINT(R.string.accessibility_fingerprint_label),
        NONE(-1),
    }

    enum class AccessibilityHintType {
        NONE,
        BOUNCER,
        ENTER,
    }
}

/** DriftVaneOS: Settings.System key for the lock screen fingerprint icon style. */
private const val FP_STYLE_KEY = "driftvane_fp_icon"
