package dev.debugkit.network.internal

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import dev.debugkit.DebugKit
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.WeakHashMap

/**
 * Pins a "MOCK ON" pill to the top of every app screen while any mock rule is enabled, so nobody
 * files a bug against a mocked response by mistake. Plain Views on the activity's content frame,
 * so it works on XML and Compose screens alike. Tapping it opens DebugKit.
 */
internal object MockIndicator : Application.ActivityLifecycleCallbacks {

    private const val DEBUGKIT_ACTIVITY = "dev.debugkit.ui.DebugKitActivity"

    private var activeRules = 0
    private val badges = WeakHashMap<Activity, TextView>()

    fun install(application: Application, activeRuleCount: Flow<Int>) {
        application.registerActivityLifecycleCallbacks(this)
        MainScope().launch {
            activeRuleCount.collect { count ->
                activeRules = count
                badges.keys.toList().forEach(::refresh)
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity.javaClass.name != DEBUGKIT_ACTIVITY) refresh(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        badges.remove(activity)
    }

    private fun refresh(activity: Activity) {
        val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        val badge = badges.getOrPut(activity) { createBadge(activity) }
        // setContentView() clears the content frame, so re-attach when needed.
        if (badge.parent !== content) {
            (badge.parent as? ViewGroup)?.removeView(badge)
            content.addView(
                badge,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                ),
            )
            ViewCompat.requestApplyInsets(badge)
        }
        badge.text = if (activeRules == 1) "MOCK ON" else "MOCK ON · $activeRules"
        badge.isVisible = activeRules > 0
        badge.bringToFront()
    }

    private fun createBadge(activity: Activity) = TextView(activity).apply {
        val density = resources.displayMetrics.density
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding((10 * density).toInt(), (3 * density).toInt(), (10 * density).toInt(), (3 * density).toInt())
        background = GradientDrawable().apply {
            setColor("#D32F2F".toColorInt())
            cornerRadius = 50 * density
        }
        elevation = 8 * density
        contentDescription = "Mock responses are active. Tap to open DebugKit."
        setOnClickListener { DebugKit.open(activity) }
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = top + (4 * density).toInt() }
            insets
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
