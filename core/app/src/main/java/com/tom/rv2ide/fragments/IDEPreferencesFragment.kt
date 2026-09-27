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

package com.tom.rv2ide.fragments

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import com.google.android.material.transition.MaterialSharedAxis
import com.tom.rv2ide.R
import com.tom.rv2ide.preferences.IPreference
import com.tom.rv2ide.preferences.IPreferenceGroup
import com.tom.rv2ide.preferences.IPreferenceScreen
import com.tom.rv2ide.preferences.observers.LSPStateObserver

class IDEPreferencesFragment : BasePreferenceFragment() {

  private var children: List<IPreference> = emptyList()

  private val serverStateListener = {
    refreshPreferences()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true).apply {
        duration = 600
    }
    returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false).apply {
        duration = 600
    }
    exitTransition = MaterialSharedAxis(MaterialSharedAxis.Z, true).apply {
        duration = 600
    }
    reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, false).apply {
        duration = 600
    }
  }

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?,
  ): View {
    return super.onCreateView(inflater, container, savedInstanceState)
  }

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    super.onCreatePreferences(savedInstanceState, rootKey)

    if (context == null) {
      return
    }

    @Suppress("DEPRECATION")
    this.children = arguments?.getParcelableArrayList(EXTRA_CHILDREN) ?: emptyList()

    preferenceScreen.removeAll()
    if (isRootScreen()) {
      addHeader()
    }
    addChildren(this.children, preferenceScreen)
    if (isRootScreen()) {
      styleRootRows()
    }
  }

  override fun onResume() {
    super.onResume()
    LSPStateObserver.addListener(serverStateListener)
  }

  override fun onPause() {
    super.onPause()
    LSPStateObserver.removeListener(serverStateListener)
  }

  private fun refreshPreferences() {
    if (context == null) {
      return
    }

    preferenceScreen.removeAll()
    if (isRootScreen()) {
      addHeader()
    }
    addChildren(this.children, preferenceScreen)
    if (isRootScreen()) {
      styleRootRows()
    }
  }

  private fun addChildren(children: List<IPreference>, pref: PreferenceGroup) {
    for (child in children) {
      val preference = child.onCreateView(requireContext())
      if (child is IPreferenceScreen) {
        preference.fragment = IDEPreferencesFragment::class.java.name
        preference.extras.putParcelableArrayList(EXTRA_CHILDREN, ArrayList(child.children))
        pref.addPreference(preference)
        continue
      }

      if (child is IPreferenceGroup) {
        pref.addPreference(preference as PreferenceCategory)
        addChildren(child.children, preference)
        continue
      }

      pref.addPreference(preference)
    }
  }

  companion object {
    const val EXTRA_CHILDREN = "ide.preferences.fragment.children"
    const val EXTRA_IS_ROOT = "ide.preferences.fragment.is_root"

    /**
     * Root-screen row icons by preference key. Central mapping so individual
     * *PrefExts files stay untouched. Keys and navigation are unchanged.
     */
    private val ROOT_ROW_ICONS =
        mapOf(
            "idepref_general" to R.drawable.ic_settings,
            "idepref_editor" to R.drawable.ic_code,
            "idepref_ai_agent" to R.drawable.ic_ai_agent,
            "idepref_build_n_run" to R.drawable.ic_run,
            "ide.preferences.terminal" to R.drawable.ic_terminal,
            "ide.prefs.developerOptions" to R.drawable.ic_bug,
            "idepref_changelog" to R.drawable.ic_history,
            "idepref_about" to R.drawable.ic_info,
        )
  }

  private fun isRootScreen(): Boolean = arguments?.getBoolean(EXTRA_IS_ROOT) == true

  /**
   * Root-only presentation. Nested screens keep the stock AndroidX look:
   * they are created by the framework without EXTRA_IS_ROOT.
   */
  private fun addHeader() {
    val header =
        Preference(requireContext()).apply {
          key = "acszen_settings_header"
          layoutResource = R.layout.preference_header
          isSelectable = false
        }
    preferenceScreen.addPreference(header)
  }

  private fun styleRootRows() {
    val context = requireContext()
    for (i in 0 until preferenceScreen.preferenceCount) {
      val preference = preferenceScreen.getPreference(i)
      // Uniform alignment, including the rows without an icon.
      preference.isIconSpaceReserved = true
      ROOT_ROW_ICONS[preference.key]?.let { resId ->
        tintedIcon(context, resId)?.let { preference.icon = it }
      }
    }
  }

  private fun tintedIcon(context: Context, resId: Int): Drawable? {
    val drawable = ContextCompat.getDrawable(context, resId)?.mutate() ?: return null
    DrawableCompat.setTint(drawable, secondaryTextColor(context))
    return drawable
  }

  private fun secondaryTextColor(context: Context): Int {
    val typedValue = TypedValue()
    if (context.theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true) &&
        typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
        typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
      return typedValue.data
    }
    return ContextCompat.getColor(context, android.R.color.darker_gray)
  }
}
