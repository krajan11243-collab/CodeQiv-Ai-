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

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.tom.rv2ide.R
import com.tom.rv2ide.databinding.LayoutMainActionItemBinding
import com.tom.rv2ide.models.MainScreenAction

/** Mobile-first action cards for the CodeQiv home screen. */
class MainActionsListAdapter
@JvmOverloads
constructor(val actions: List<MainScreenAction> = emptyList()) :
    RecyclerView.Adapter<MainActionsListAdapter.VH>() {

  class VH(val binding: LayoutMainActionItemBinding) : RecyclerView.ViewHolder(binding.root)

  private val accents = intArrayOf(
      Color.rgb(157, 82, 255),
      Color.rgb(0, 174, 255),
      Color.rgb(0, 229, 159),
      Color.rgb(255, 166, 50),
      Color.rgb(255, 61, 174),
      Color.rgb(0, 205, 239),
      Color.rgb(255, 65, 89),
  )

  private val subtitles = listOf(
      "Start a new Android project",
      "Open your saved project",
      "Clone and sync a Git repository",
      "Run commands in terminal",
      "App settings and customization",
      "SDK, Gradle, Build & tools",
      "Guides and documentation",
  )

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
      VH(LayoutMainActionItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

  override fun getItemCount(): Int = actions.size

  fun getAction(index: Int) = actions[index]

  override fun onBindViewHolder(holder: VH, position: Int) {
    val action = getAction(position)
    val binding = holder.binding
    val accent = accents[position % accents.size]
    val context = binding.root.context

    (binding.root as? MaterialCardView)?.apply {
      strokeColor = accent
      setCardBackgroundColor(Color.rgb(15, 14, 24))
      strokeWidth = (1.1f * resources.displayMetrics.density).toInt()
      radius = 18f * resources.displayMetrics.density
    }

    binding.actionTitle.text = context.getString(action.text)
    binding.actionSubtitle.text = subtitles.getOrElse(position) { "Open " + context.getString(action.text) }
    binding.actionIcon.setImageResource(action.icon)
    binding.actionIcon.imageTintList = ColorStateList.valueOf(accent)
    binding.actionChevron.setTextColor(accent)

    val density = context.resources.displayMetrics.density
    val tile = GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      cornerRadius = 14f * density
      setColor(Color.argb(30, Color.red(accent), Color.green(accent), Color.blue(accent)))
      setStroke((1.2f * density).toInt().coerceAtLeast(1), accent)
    }
    binding.actionIconTile.background = tile

    val click: (View) -> Unit = { view -> action.onClick?.invoke(action, view) }
    binding.actionContainer.setOnClickListener(click)
    binding.root.setOnClickListener(click)
    binding.actionContainer.setOnLongClickListener { view ->
      action.onLongClick?.invoke(action, view) ?: false
    }
  }
}
