package com.training.trackplanner

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class RestTimerOverlayController(private val context: Context) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var deleteTargetView: View? = null
    private var timeText: TextView? = null
    private var hintText: TextView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var currentState: RestTimerState = RestTimerState.Idle
    private var dismissedForCurrentAwaySession = false

    fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun resetDismissedForCurrentAwaySession() {
        dismissedForCurrentAwaySession = false
    }

    fun showOrUpdate(state: RestTimerState) {
        currentState = state
        if (!canDrawOverlays() || dismissedForCurrentAwaySession || !state.isActive) {
            remove()
            return
        }

        if (overlayView == null) {
            createOverlay(state)
        }
        timeText?.text = if (state.isFinished) "휴식 종료" else formatSeconds(state.remainingSeconds)
        hintText?.text = state.nextHint.ifBlank { state.exerciseName }
    }

    fun remove() {
        hideDeleteTarget()
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        timeText = null
        hintText = null
        overlayParams = null
    }

    private fun createOverlay(state: RestTimerState) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 22, 28, 18)
            setBackgroundColor(Color.argb(235, 31, 41, 55))
            setOnClickListener {
                context.startActivity(
                    RestTimerNavigation.targetIntent(context, currentState)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

        timeText = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            text = formatSeconds(state.remainingSeconds)
        }
        hintText = TextView(context).apply {
            setTextColor(Color.rgb(218, 224, 235))
            textSize = 13f
            text = state.nextHint
            maxLines = 2
        }
        val closeButton = Button(context).apply {
            text = "닫기"
            textSize = 12f
            setOnClickListener {
                dismissForAwaySession()
            }
        }

        container.addView(timeText)
        container.addView(hintText)
        container.addView(closeButton)

        val params = overlayLayoutParams()
        installDragHandler(container, params)
        runCatching {
            windowManager.addView(container, params)
            overlayView = container
            overlayParams = params
        }
    }

    private fun installDragHandler(view: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchStartRawX = 0f
        var touchStartRawY = 0f
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchStartRawX = event.rawX
                    touchStartRawY = event.rawY
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartRawX).toInt()
                    val dy = (event.rawY - touchStartRawY).toInt()
                    if (abs(dx) > 6 || abs(dy) > 6) moved = true
                    params.x = (startX + dx).coerceAtLeast(0)
                    params.y = (startY + dy).coerceAtLeast(0)
                    showDeleteTarget()
                    runCatching { windowManager.updateViewLayout(view, params) }
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    hideDeleteTarget()
                    if (moved && isInDeleteZone(event.rawX, event.rawY)) {
                        dismissForAwaySession()
                        true
                    } else if (!moved) {
                        context.startActivity(
                            RestTimerNavigation.targetIntent(context, currentState)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        true
                    } else {
                        moved
                    }
                }

                else -> false
            }
        }
    }

    private fun dismissForAwaySession() {
        dismissedForCurrentAwaySession = true
        remove()
    }

    private fun showDeleteTarget() {
        if (deleteTargetView != null || !canDrawOverlays()) return
        val deleteView = TextView(context).apply {
            text = "삭제"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(42, 18, 42, 18)
            setBackgroundColor(Color.argb(230, 185, 28, 28))
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 82
        }
        runCatching {
            windowManager.addView(deleteView, params)
            deleteTargetView = deleteView
        }
    }

    private fun hideDeleteTarget() {
        deleteTargetView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        deleteTargetView = null
    }

    private fun isInDeleteZone(rawX: Float, rawY: Float): Boolean {
        val metrics = context.resources.displayMetrics
        val centerX = metrics.widthPixels / 2f
        val bottomZoneTop = metrics.heightPixels - 260f
        val horizontalHit = abs(rawX - centerX) <= metrics.widthPixels * 0.32f
        return rawY >= bottomZoneTop && horizontalHit
    }

    private fun overlayLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (context.resources.displayMetrics.widthPixels - 360).coerceAtLeast(24)
            y = 120
        }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
}
