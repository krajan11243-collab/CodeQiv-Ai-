/*
 * AI shortcut for the mobile editor toolbar.
 * Distributed under GPL-3.0-or-later.
 */
package com.tom.rv2ide.actions.etc

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.MenuItem
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import com.tom.rv2ide.R
import com.tom.rv2ide.actions.ActionData
import com.tom.rv2ide.actions.ActionsRegistry
import com.tom.rv2ide.actions.EditorActivityAction
import com.tom.rv2ide.actions.getActivity
import com.tom.rv2ide.actions.getContext
import com.tom.rv2ide.actions.internal.DefaultActionsRegistry
import com.tom.rv2ide.actions.sidebar.AIAgentSidebarAction
import com.tom.rv2ide.fragments.sidebar.EditorSidebarFragment
import com.tom.rv2ide.utils.EditorToolbarActionView

class AIEditorToolbarAction(context: Context, override val order: Int) : EditorActivityAction() {
  override val id: String = "ide.editor.toolbar.ai"
  override var requiresUIThread: Boolean = true

  init {
    label = "AI"
    icon = ContextCompat.getDrawable(context, R.drawable.ic_ai_agent)
  }

  override fun getShowAsActionFlags(data: ActionData): Int =
      MenuItem.SHOW_AS_ACTION_ALWAYS

  override fun createActionView(data: ActionData): View? {
    val context = data.getContext() ?: return null
    val accent = ContextCompat.getColor(context, R.color.codeqiv_editor_accent)
    return EditorToolbarActionView(context).apply {
      setLabel("AI")
      setIcon(icon, PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_ATOP))
      setIconGlow(accent)
      setOnClickListener {
        (ActionsRegistry.getInstance() as? DefaultActionsRegistry)
            ?.executeAction(this@AIEditorToolbarAction, data)
      }
    }
  }

  override suspend fun execAction(data: ActionData): Boolean {
    val activity = data.getActivity() ?: return false
    val sidebar =
        activity.supportFragmentManager.findFragmentById(R.id.drawer_sidebar) as? EditorSidebarFragment
            ?: return false
    activity.binding.editorDrawerLayout.openDrawer(GravityCompat.START)
    sidebar.openAction(AIAgentSidebarAction.ID)
    return true
  }

  override fun createColorFilter(data: ActionData) =
      data.getContext()?.let {
        PorterDuffColorFilter(
            ContextCompat.getColor(it, R.color.codeqiv_editor_accent),
            PorterDuff.Mode.SRC_ATOP,
        )
      }
}
