/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.tom.rv2ide.actions.etc

import android.content.Context
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import com.tom.rv2ide.actions.ActionData
import com.tom.rv2ide.actions.ActionsRegistry
import com.tom.rv2ide.actions.getContext
import com.tom.rv2ide.actions.internal.DefaultActionsRegistry
import com.tom.rv2ide.actions.ActionItem
import com.tom.rv2ide.actions.ActionMenu
import com.tom.rv2ide.actions.EditorActivityAction
import com.tom.rv2ide.resources.R
import com.tom.rv2ide.utils.EditorToolbarActionView

/** @author Akash Yadav */
class FindActionMenu(context: Context, override val order: Int) :
    EditorActivityAction(), ActionMenu {

  override val children: MutableSet<ActionItem> = mutableSetOf()
  override val id: String = "ide.editor.find"

  init {
    label = context.getString(R.string.menu_find)
    icon = ContextCompat.getDrawable(context, R.drawable.ic_search)

    addAction(FindInFileAction(context, 0))
    addAction(FindInProjectAction(context, 1))
  }

  override fun getShowAsActionFlags(data: ActionData): Int =
      MenuItem.SHOW_AS_ACTION_ALWAYS

  override fun createActionView(data: ActionData): View? {
    val context = data.getContext() ?: return null
    return EditorToolbarActionView(context).apply {
      setLabel("Search")
      setIcon(icon, createColorFilter(data))
      setOnClickListener {
        PopupMenu(context, this).apply {
          children.sortedBy { child -> child.order }.forEach { child ->
            val entry = menu.add(0, child.itemId, child.order, child.label)
            child.icon?.let { drawable ->
              entry.icon =
                  drawable.constantState?.newDrawable(context.resources)?.mutate()
                      ?: drawable.mutate()
            }
            entry.setOnMenuItemClickListener {
              (ActionsRegistry.getInstance() as? DefaultActionsRegistry)?.executeAction(child, data)
              true
            }
          }
        }.show()
      }
    }
  }

  override fun prepare(data: ActionData) {
    super<EditorActivityAction>.prepare(data)
    super<ActionMenu>.prepare(data)
  }
}
