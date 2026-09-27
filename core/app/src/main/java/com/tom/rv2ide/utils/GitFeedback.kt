/*
 *  This file is part of AndroidCodeStudio.
 *
 *  AndroidCodeStudio is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidCodeStudio is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidCodeStudio.  If not, see <https://www.gnu.org/licenses/>.
*/
package com.tom.rv2ide.utils

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import com.tom.rv2ide.R
import com.google.android.material.R as MaterialR
import com.google.android.material.snackbar.Snackbar

/**
 * Presentation helpers for Git feedback.
 *
 * The Git screens surface every operation result through a Snackbar so that the
 * user always sees the outcome of an action. These helpers only change how a
 * message looks - what is reported, when it is reported and how errors are
 * handled further up the stack stays exactly the same.
 *
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

/** How a feedback message should be presented. */
enum class GitFeedbackStyle {
    /** A finished operation that succeeded. */
    SUCCESS,

    /** Neutral, non urgent information. */
    INFO,

    /** A failed or refused operation - shown longer and in the error color. */
    ERROR,
}

/**
 * Shows [message] on the closest Snackbar with the styling of [style].
 *
 * Success uses the theme's primary container, errors use the theme's error
 * color (and stay on screen longer), everything else keeps the default
 * neutral Snackbar appearance. Errors are never hidden or turned into a modal
 * dialog.
 */
fun View.showGitFeedback(
    message: String,
    style: GitFeedbackStyle = GitFeedbackStyle.INFO,
) {
    val snackbar =
        Snackbar.make(
            this,
            message,
            if (style == GitFeedbackStyle.ERROR) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT,
        )

    val colors =
        when (style) {
            GitFeedbackStyle.SUCCESS ->
                pairOf(context, R.attr.colorPrimaryContainer, R.attr.colorOnPrimaryContainer)
            GitFeedbackStyle.ERROR -> pairOf(context, R.attr.colorError, R.attr.colorOnError)
            GitFeedbackStyle.INFO -> null
        }

    if (colors != null) {
        snackbar.view.backgroundTintList = ColorStateList.valueOf(colors.first)
        findMessageView(snackbar.view)?.setTextColor(colors.second)
    }

    snackbar.show()
}

/** The message TextView of a Snackbar - its label always has to stay readable. */
private fun findMessageView(view: View): TextView? {
    if (view is TextView && view !is Button) return view
    if (view is ViewGroup) {
        for (index in 0 until view.childCount) {
            findMessageView(view.getChildAt(index))?.let { return it }
        }
    }
    return view.findViewById(MaterialR.id.snackbar_text)
}

private fun pairOf(context: Context, @AttrRes background: Int, @AttrRes foreground: Int): Pair<Int, Int>? {
    val bg = resolveThemeColor(context, background) ?: return null
    val fg = resolveThemeColor(context, foreground) ?: return null
    return bg to fg
}

private fun resolveThemeColor(context: Context, @AttrRes attr: Int): Int? {
    val typedValue = TypedValue()
    if (!context.theme.resolveAttribute(attr, typedValue, true)) return null
    return if (typedValue.resourceId != 0) {
        ContextCompat.getColor(context, typedValue.resourceId)
    } else {
        typedValue.data
    }
}
