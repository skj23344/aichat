package com.coder.aichat.animation

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.view.isVisible

/**
 * 统一的动画工具类 — "清晰不死板" 的核心。
 * 用物理感强的 overshoot / decelerate 插值器，替代生硬的线性动画。
 */
object ItemAnimators {

    /** 列表项进入动画：淡入 + 上移 + 轻微回弹 */
    fun itemEnter(view: View, position: Int, delayMillis: Long = 40L) {
        val delay = position * delayMillis
        val animator = AnimatorSet()

        val alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f)
        alpha.duration = 280

        val translationY = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 32f, 0f)
        translationY.duration = 380
        translationY.interpolator = DecelerateInterpolator(1.8f)

        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.96f, 1f)
        scaleX.duration = 380
        scaleX.interpolator = OvershootInterpolator(1.2f)

        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.96f, 1f)
        scaleY.duration = 380
        scaleY.interpolator = OvershootInterpolator(1.2f)

        animator.playTogether(alpha, translationY, scaleX, scaleY)
        animator.startDelay = delay.coerceAtMost(160L)
        animator.start()
    }

    /** 卡片按压反馈：放大 — 松手回弹 */
    fun press(view: View) {
        val animator = AnimatorSet()
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 0.96f)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 0.96f)
        scaleX.duration = 120
        scaleY.duration = 120
        animator.playTogether(scaleX, scaleY)
        animator.start()
    }

    /** 卡片释放：回弹到原始大小 */
    fun release(view: View) {
        val animator = AnimatorSet()
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.96f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.96f, 1f)
        scaleX.duration = 260
        scaleY.duration = 260
        scaleX.interpolator = OvershootInterpolator(1.6f)
        scaleY.interpolator = OvershootInterpolator(1.6f)
        animator.playTogether(scaleX, scaleY)
        animator.start()
    }

    /** 打字指示器三个点交错呼吸动画 */
    fun typingDots(dot1: View, dot2: View, dot3: View) {
        val duration = 700L

        fun dotAnimator(dot: View, delay: Long): ObjectAnimator {
            val animator = ObjectAnimator.ofFloat(dot, View.ALPHA, 0.3f, 1f)
            animator.duration = duration
            animator.startDelay = delay
            animator.repeatCount = ValueAnimator.INFINITE
            animator.repeatMode = ValueAnimator.REVERSE
            animator.interpolator = DecelerateInterpolator()
            return animator
        }

        val a1 = dotAnimator(dot1, 0L)
        val a2 = dotAnimator(dot2, 200L)
        val a3 = dotAnimator(dot3, 400L)
        a1.start(); a2.start(); a3.start()

        // 绑定到 tag，方便停止
        dot1.tag = a1
        dot2.tag = a2
        dot3.tag = a3
    }

    fun stopTypingDots(dot1: View, dot2: View, dot3: View) {
        listOf(dot1, dot2, dot3).forEach { dot ->
            (dot.tag as? Animator)?.cancel()
            dot.alpha = 1f
        }
    }

    /** 底部输入栏随内容高度平滑变化 */
    fun animateHeight(view: View, targetHeight: Int, duration: Long = 220) {
        val lp = view.layoutParams
        val animator = ValueAnimator.ofInt(lp.height, targetHeight)
        animator.duration = duration
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener {
            lp.height = it.animatedValue as Int
            view.requestLayout()
        }
        animator.start()
    }

    /** 视图中枢：淡入 + 缩放入场 */
    fun fadeScaleIn(view: View, duration: Long = 320) {
        view.alpha = 0f
        view.scaleX = 0.92f
        view.scaleY = 0.92f
        view.isVisible = true
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(duration)
            .setInterpolator(OvershootInterpolator(1.4f))
            .start()
    }
}
