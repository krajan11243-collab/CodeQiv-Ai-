package com.tom.rv2ide.activities

import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.tom.rv2ide.R
import com.tom.rv2ide.fragments.sidebar.ArtificialFragment

/** Standalone entry point for CodeQiv AI, using the existing project-aware agent UI. */
class AIAgentActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 8, 10)
        window.navigationBarColor = android.graphics.Color.rgb(8, 8, 10)
        val host = FrameLayout(this).apply {
            id = android.view.View.generateViewId()
            setBackgroundColor(android.graphics.Color.rgb(8, 8, 10))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(host)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(host.id, ArtificialFragment().apply {
                    arguments = Bundle().apply {
                        putBoolean(ArtificialFragment.ARG_STANDALONE_CODEQIV_CHAT, true)
                    }
                })
                .commit()
        }
    }
}
