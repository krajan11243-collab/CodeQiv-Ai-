/*
 * GitHub push action for CodeQiv's mobile editor toolbar.
 * Distributed under GPL-3.0-or-later.
 */
package com.tom.rv2ide.actions.etc

import android.content.Context
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.tom.rv2ide.actions.ActionData
import com.tom.rv2ide.actions.ActionsRegistry
import com.tom.rv2ide.actions.EditorActivityAction
import com.tom.rv2ide.actions.getContext
import com.tom.rv2ide.actions.internal.DefaultActionsRegistry
import com.tom.rv2ide.projects.internal.ProjectManagerImpl
import com.tom.rv2ide.utils.EditorToolbarActionView
import com.tom.rv2ide.utils.GitHubAccountManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.net.URL

/** Pushes the open project to an existing GitHub repository using the connected account. */
class GitHubPushEditorAction(context: Context, override val order: Int) : EditorActivityAction() {
  override val id: String = "ide.editor.github.push"
  override var requiresUIThread: Boolean = true

  init {
    label = "Push"
    icon = ContextCompat.getDrawable(context, com.tom.rv2ide.resources.R.drawable.ic_github)
  }

  override fun getShowAsActionFlags(data: ActionData): Int = MenuItem.SHOW_AS_ACTION_ALWAYS

  override fun createActionView(data: ActionData): View? {
    val context = data.getContext() ?: return null
    return EditorToolbarActionView(context).apply {
      setLabel("Push")
      setIcon(icon, createColorFilter(data))
      setOnClickListener {
        (ActionsRegistry.getInstance() as? DefaultActionsRegistry)
            ?.executeAction(this@GitHubPushEditorAction, data)
      }
    }
  }

  override suspend fun execAction(data: ActionData): Boolean {
    val activity = data.getActivity() ?: return false
    val username = GitHubAccountManager.getConnectedAccount(activity)
    val token = GitHubAccountManager.getAccessToken(activity)
    if (username.isNullOrBlank() || token.isNullOrBlank()) {
      AlertDialog.Builder(activity)
          .setTitle("Connect GitHub first")
          .setMessage("Open CodeQiv home and connect your GitHub account before pushing a project.")
          .setPositiveButton("OK", null)
          .show()
      return false
    }

    val layout = LinearLayout(activity).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(activity, 20), dp(activity, 8), dp(activity, 20), dp(activity, 4))
    }
    val repositoryInput = EditText(activity).apply {
      hint = "https://github.com/owner/repository.git"
      singleLine = true
      setText("https://github.com/$username/")
    }
    val commitInput = EditText(activity).apply {
      hint = "Commit message"
      singleLine = true
      setText("Update from CodeQiv")
    }
    layout.addView(repositoryInput, LinearLayout.LayoutParams(-1, -2))
    layout.addView(commitInput, LinearLayout.LayoutParams(-1, -2))

    val dialog = AlertDialog.Builder(activity)
        .setTitle("Push project to GitHub")
        .setMessage("Enter an existing GitHub repository. CodeQiv stages project files, commits them, and pushes the current branch.")
        .setView(layout)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Push", null)
        .create()
    dialog.setOnShowListener {
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val remote = repositoryInput.text?.toString()?.trim().orEmpty()
        val commitMessage = commitInput.text?.toString()?.trim().orEmpty()
        if (!isValidGitHubUrl(remote)) {
          repositoryInput.error = "Enter an HTTPS github.com repository URL"
          return@setOnClickListener
        }
        if (commitMessage.isBlank()) {
          commitInput.error = "Enter a commit message"
          return@setOnClickListener
        }
        dialog.dismiss()
        Toast.makeText(activity, "Preparing GitHub push…", Toast.LENGTH_SHORT).show()
        actionScope.launch {
          val result = runCatching {
            withContext(Dispatchers.IO) {
              pushProject(ProjectManagerImpl.getInstance().projectDir, remote, commitMessage, username, token)
            }
          }
          withContext(Dispatchers.Main) {
            if (!activity.isFinishing) {
              val message = result.fold(
                  onSuccess = { "Project pushed to GitHub successfully." },
                  onFailure = { "GitHub push failed: ${it.message ?: it.javaClass.simpleName}" },
              )
              Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
            }
          }
        }
      }
    }
    dialog.show()
    return true
  }

  private fun isValidGitHubUrl(value: String): Boolean = runCatching {
    val url = URL(value)
    url.protocol == "https" && url.host.equals("github.com", ignoreCase = true) &&
        url.path.trim('/').split('/').let { parts -> parts.size == 2 && parts.all { it.isNotBlank() } }
  }.getOrDefault(false)

  private fun pushProject(root: File, remoteUrl: String, message: String, username: String, token: String) {
    require(root.isDirectory) { "The open project folder is unavailable." }
    val git = if (File(root, ".git").exists()) Git.open(root) else Git.init().setDirectory(root).call()
    git.use { repository ->
      val config = repository.repository.config
      config.setString("remote", "origin", "url", remoteUrl.removeSuffix("/"))
      config.save()
      repository.add().addFilepattern(".").call()
      repository.commit()
          .setMessage(message)
          .setAuthor(username, "$username@users.noreply.github.com")
          .setAllowEmpty(true)
          .call()
      val branch = repository.repository.branch.ifBlank { "main" }
      repository.push()
          .setRemote("origin")
          .setRefSpecs(RefSpec("HEAD:refs/heads/$branch"))
          .setCredentialsProvider(UsernamePasswordCredentialsProvider(username, token))
          .call()
    }
  }

  private fun dp(context: Context, value: Int): Int =
      (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
