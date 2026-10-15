/*
 * CodeQiv editor toolbar action view.
 * Distributed under GPL-3.0-or-later.
 */
package com.tom.rv2ide.utils

import android.content.Context
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.tom.rv2ide.R

/** Compact icon-over-label action used by the mobile editor toolbar. */
class EditorToolbarActionView(context: Context) : LinearLayout(context) {
  private val iconView = ImageView(context)
  private val labelView = TextView(context)

  init {
    orientation = VERTICAL
    gravity = Gravity.CENTER
    minimumWidth = dp(56)
    layoutParams = ViewGroup.LayoutParams(dp(58), dp(72))
    setPadding(dp(4), dp(5), dp(4), dp(3))
    isClickable = true
    isFocusable = true

    val selectable = TypedValue()
    if (context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, selectable, true) &&
        selectable.resourceId != 0
    ) {
      setBackgroundResource(selectable.resourceId)
    }

    iconView.scaleType = ImageView.ScaleType.FIT_CENTER
    addView(iconView, LayoutParams(dp(25), dp(25)))

    labelView.gravity = Gravity.CENTER
    labelView.maxLines = 1
    labelView.textSize = 11f
    labelView.setTextColor(context.resolveAttr(R.attr.colorOnSurface))
    addView(
        labelView,
        LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
          topMargin = dp(3)
        },
    )
  }

  fun setLabel(label: CharSequence) {
    labelView.text = label
    contentDescription = label
  }

  fun setIcon(drawable: Drawable?, filter: ColorFilter? = null) {
    val copy = drawable?.constantState?.newDrawable(resources)?.mutate() ?: drawable?.mutate()
    iconView.setImageDrawable(copy)
    iconView.colorFilter = filter
  }

  /** Add the soft neon tile used by the AI shortcut. */
  fun setIconGlow(color: Int) {
    iconView.background =
        GradientDrawable().apply {
          shape = GradientDrawable.RECTANGLE
          cornerRadius = dp(7).toFloat()
          setColor(Color.argb(24, Color.red(color), Color.green(color), Color.blue(color)))
          setStroke(dp(1), color)
        }
    iconView.setPadding(dp(3), dp(3), dp(3), dp(3))
    iconView.elevation = dp(5).toFloat()
  }

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
