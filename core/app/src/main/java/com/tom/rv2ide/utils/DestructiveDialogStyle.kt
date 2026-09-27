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

import android.content.res.ColorStateList
import android.util.TypedValue
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

/**
 * Presentation helpers for Git dialogs.
 *
 * Keeps the visual treatment of destructive confirmations consistent across the
 * Git UI without touching any Git behaviour.
 *
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

/**
 * Styles a destructive confirmation dialog (delete branch, discard changes,
 * remove remote, clear credentials): the confirm button is filled with the
 * theme's error color and its label switches to the matching on-error color so
 * the action cannot be mistaken for a routine one while staying readable.
 *
 * The dialog itself, its title, its message and its callbacks are untouched.
 */
fun AlertDialog.styleAsDestructive() {
    val typedValue = TypedValue()
    if (!context.theme.resolveAttribute(androidx.appcompat.R.attr.colorError, typedValue, true)) return

    val colorStateList = if (typedValue.resourceId != 0) {
        ContextCompat.getColorStateList(context, typedValue.resourceId)
    } else {
        ColorStateList.valueOf(typedValue.data)
    }

    val confirmButton = getButton(AlertDialog.BUTTON_POSITIVE)
    colorStateList?.let { confirmButton.backgroundTintList = it }

    // Keep the label legible on top of the error-colored background.
    val onError = TypedValue()
    if (context.theme.resolveAttribute(com.tom.rv2ide.R.attr.colorOnError, onError, true)) {
        val color = if (onError.resourceId != 0) {
            ContextCompat.getColor(context, onError.resourceId)
        } else {
            onError.data
        }
        confirmButton.setTextColor(color)
    }
}
