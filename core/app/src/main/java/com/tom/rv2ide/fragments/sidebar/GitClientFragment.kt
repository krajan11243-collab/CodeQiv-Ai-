package com.tom.rv2ide.fragments.sidebar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.tom.rv2ide.R
import com.tom.rv2ide.configurations.GCProperties
import com.tom.rv2ide.databinding.FragmentGitClientBinding
import com.tom.rv2ide.viewmodel.GitViewModel
import com.tom.rv2ide.viewmodel.RepositoryStatus
import com.tom.rv2ide.fragments.*
import com.tom.rv2ide.utils.EditorSidebarActions

class GitClientFragment : Fragment() {
    
    private var _binding: FragmentGitClientBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: GitViewModel by activityViewModels()
    
    private var hasInitialized = false
    
    /** Whether the Git panel currently takes the whole window instead of the side panel. */
    private var isPanelExpanded = false
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGitClientBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRepoHeader()
        
        if (!hasInitialized) {
            viewModel.checkRepositoryStatus(GCProperties.userProject)
            setupObservers()
        } else {
            val currentStatus = viewModel.repositoryStatus.value
            when (currentStatus) {
                RepositoryStatus.INITIALIZED, RepositoryStatus.OPENED -> {
                    showMainContent()
                }
                else -> {
                    showInitScreen()
                }
            }
        }
    }
    
    /**
     * Repository context shown above the tabs: the project path and the branch
     * that is currently checked out. Purely presentational - it only mirrors
     * [GitViewModel] state and never triggers Git operations itself.
     */
    private fun setupRepoHeader() {
        binding.textRepoPath.text = GCProperties.userProject
        
        viewModel.currentBranch.observe(viewLifecycleOwner) { branch ->
            if (branch.isNullOrBlank()) {
                binding.textRepoBranch.visibility = View.GONE
            } else {
                binding.textRepoBranch.text = branch
                binding.textRepoBranch.visibility = View.VISIBLE
            }
        }
        
        binding.buttonExpand.setOnClickListener {
            setPanelExpanded(!isPanelExpanded)
        }
        updateExpandButton()
    }
    
    /**
     * Expands or collapses the Git side panel.
     *
     * The panel is the navigation view that hosts this fragment inside the
     * editor drawer. Expanding only widens that existing view to the full
     * window and collapses it back to its normal wrap-content width, so the
     * fragment, the pager and the selected tab all keep their state - no new
     * navigation or state container is introduced.
     */
    private fun setPanelExpanded(expand: Boolean) {
        if (expand == isPanelExpanded) return
        
        val drawer = activity?.findViewById<DrawerLayout>(R.id.editor_drawerLayout) ?: return
        val navView = drawer.findViewById<View>(R.id.startNav) ?: return
        val params = navView.layoutParams as? DrawerLayout.LayoutParams ?: return
        
        isPanelExpanded = expand
        params.width =
            if (expand) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        navView.layoutParams = params
        drawer.requestLayout()
        
        updateExpandButton()
    }
    
    private fun updateExpandButton() {
        if (_binding == null) return
        binding.buttonExpand.setImageResource(
            if (isPanelExpanded) R.drawable.ic_collapse_panel else R.drawable.ic_expand_panel
        )
        val label = if (isPanelExpanded) "Collapse Git panel" else "Expand Git panel"
        binding.buttonExpand.contentDescription = label
        binding.buttonExpand.tooltipText = label
    }
    
    /** Restores the normal side panel width, e.g. when another sidebar panel takes over. */
    private fun collapsePanelIfExpanded() {
        if (isPanelExpanded) setPanelExpanded(false)
    }
    
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) collapsePanelIfExpanded()
    }
    
    private fun setupObservers() {
        viewModel.isRepositoryInitialized.observe(viewLifecycleOwner) { initialized ->
            if (initialized && !hasInitialized) {
                viewModel.openExistingRepository(GCProperties.userProject)
            } else if (!initialized && !hasInitialized) {
                showInitScreen()
            }
        }
        
        viewModel.repositoryStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                RepositoryStatus.INITIALIZED, RepositoryStatus.OPENED -> {
                    if (!hasInitialized) {
                        hasInitialized = true
                        showMainContent()
                        viewModel.refreshAll()
                    }
                }
                RepositoryStatus.ERROR -> {
                    hasInitialized = false
                    showInitScreen()
                }
                else -> {}
            }
        }
    }
    
    private fun showInitScreen() {
        collapsePanelIfExpanded()
        binding.repoHeader.visibility = View.GONE
        binding.navigationRail.visibility = View.GONE
        binding.viewPager.visibility = View.GONE
        
        childFragmentManager.beginTransaction()
            .replace(R.id.containerInit, InitFragment())
            .commit()
        
        binding.containerInit.visibility = View.VISIBLE
    }
    
    private fun showMainContent() {
        binding.containerInit.visibility = View.GONE
        binding.repoHeader.visibility = View.VISIBLE
        binding.navigationRail.visibility = View.VISIBLE
        binding.viewPager.visibility = View.VISIBLE
        
        setupViewPager()
        setupNavigationRail()
    }
    
    private fun setupViewPager() {
        if (binding.viewPager.adapter == null) {
            val adapter = ViewPagerAdapter(this)
            binding.viewPager.adapter = adapter
        }
    }
    
    private fun setupNavigationRail() {
        binding.navigationRail.menu.clear()
        binding.navigationRail.inflateMenu(R.menu.navigation_rail_menu)
        
        binding.navigationRail.selectedItemId = R.id.nav_changes
        
        binding.navigationRail.setOnItemSelectedListener { item ->
            val position = when (item.itemId) {
                R.id.nav_changes -> 0
                R.id.nav_history -> 1
                R.id.nav_branches -> 2
                R.id.nav_remotes -> 3
                R.id.nav_settings -> 4
                else -> 0
            }
            binding.viewPager.currentItem = position
            true
        }
        
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val itemId = when (position) {
                    0 -> R.id.nav_changes
                    1 -> R.id.nav_history
                    2 -> R.id.nav_branches
                    3 -> R.id.nav_remotes
                    4 -> R.id.nav_settings
                    else -> R.id.nav_changes
                }
                binding.navigationRail.selectedItemId = itemId
            }
        })
    }
    
    private inner class ViewPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 5
        
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> ChangesFragment()
                1 -> HistoryFragment()
                2 -> BranchesFragment()
                3 -> RemotesFragment()
                4 -> SettingsFragment()
                else -> ChangesFragment()
            }
        }
    }
    
    override fun onDestroyView() {
        // Never leave the editor drawer full width behind.
        collapsePanelIfExpanded()
        super.onDestroyView()
        _binding = null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        EditorSidebarActions.removeFragmentFromCache("ide.editor.sidebar.gitclient")
    }
}