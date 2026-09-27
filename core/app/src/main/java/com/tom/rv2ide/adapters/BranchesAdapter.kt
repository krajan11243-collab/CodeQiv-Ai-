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
package com.tom.rv2ide.adapters

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tom.rv2ide.R
import com.tom.rv2ide.databinding.ItemBranchBinding

/**
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
 */

class BranchesAdapter(
    private val onCheckoutClick: (String) -> Unit,
    private val onDeleteClick: (String) -> Unit,
    private val getCurrentBranch: () -> String
) : ListAdapter<String, BranchesAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBranchBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ViewHolder(
        private val binding: ItemBranchBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(branch: String) {
            val isCurrentBranch = branch == getCurrentBranch()
            val view = binding.root
            
            // Branch names are shown verbatim - the UI never parses or rewrites them.
            binding.textBranchName.text = branch
            binding.textBranchName.setTypeface(null, if (isCurrentBranch) Typeface.BOLD else Typeface.NORMAL)
            binding.textBranchName.setTextColor(
                resolveThemeColor(view, if (isCurrentBranch) R.attr.colorPrimary else R.attr.colorOnSurface)
                    ?: binding.textBranchName.currentTextColor
            )
            
            // A dot marks the checked out branch; other rows keep the same
            // space reserved so every branch name lines up in one column.
            binding.viewCurrentDot.visibility = if (isCurrentBranch) View.VISIBLE else View.INVISIBLE
            resolveThemeColor(view, R.attr.colorPrimary)?.let { color ->
                binding.viewCurrentDot.backgroundTintList = ColorStateList.valueOf(color)
            }
            
            // Nothing to check out or delete on the branch that is already
            // checked out, so that row stays a clean single line.
            binding.buttonMore.visibility = if (isCurrentBranch) View.GONE else View.VISIBLE
            
            binding.buttonMore.setOnClickListener { anchor ->
                val popup = PopupMenu(view.context, anchor)
                popup.inflate(R.menu.branch_row_actions)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_checkout -> {
                            if (!isCurrentBranch) {
                                onCheckoutClick(branch)
                            }
                            true
                        }
                        R.id.action_delete -> {
                            onDeleteClick(branch)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }
        
        private fun resolveThemeColor(view: View, attr: Int): Int? {
            val context = view.context
            val typedValue = TypedValue()
            if (!context.theme.resolveAttribute(attr, typedValue, true)) return null
            return if (typedValue.resourceId != 0) {
                ContextCompat.getColor(context, typedValue.resourceId)
            } else {
                typedValue.data
            }
        }
    }
    
    private class DiffCallback : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
        
        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean {
            return oldItem == newItem
        }
    }
}
