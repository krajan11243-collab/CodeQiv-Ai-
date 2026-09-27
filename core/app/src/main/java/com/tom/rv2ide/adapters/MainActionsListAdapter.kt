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

package com.tom.rv2ide.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.tom.rv2ide.R
import com.tom.rv2ide.models.MainScreenAction

/**
 * Adapter for the actions available on the main screen.
 *
 * @param itemLayoutRes item layout to inflate. Any layout with a
 *   [MaterialButton] with ID `R.id.actionButton` works (row or grid tile).
 *
 * @author Akash Yadav
 */
class MainActionsListAdapter
@JvmOverloads
constructor(
    val actions: List<MainScreenAction> = emptyList(),
    @LayoutRes private val itemLayoutRes: Int = R.layout.layout_main_action_item,
) : RecyclerView.Adapter<MainActionsListAdapter.VH>() {
  class VH(val root: View, val actionButton: MaterialButton) :
      RecyclerView.ViewHolder(root)

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
    val view = LayoutInflater.from(parent.context).inflate(itemLayoutRes, parent, false)
    return VH(view, view.findViewById(R.id.actionButton))
  }

  override fun getItemCount(): Int = actions.size

  fun getAction(index: Int) = actions[index]

  override fun onBindViewHolder(holder: VH, position: Int) {
    val action = getAction(index = position)

    holder.actionButton.apply {
      setText(action.text)
      setIconResource(action.icon)
      setOnClickListener { action.onClick?.invoke(action, it) }
      action.onLongClick?.let { onLongClick -> setOnLongClickListener { onLongClick(action, it) } }
    }
  }
}
