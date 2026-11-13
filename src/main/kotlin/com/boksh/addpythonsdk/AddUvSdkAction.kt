package com.boksh.addpythonsdk

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import java.nio.file.Paths

/**
 * The "From uv" item under the "Add Python SDK" submenu. Resolves the uv project
 * virtualenv for the selected module directory and assigns its interpreter to the module.
 */
class AddUvSdkAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val moduleDir = e.getData(CommonDataKeys.VIRTUAL_FILE)?.takeIf { it.isDirectory } ?: return
        val workDir = Paths.get(moduleDir.path)

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "Resolving uv environment", true) {
                override fun run(indicator: ProgressIndicator) {
                    val venvPath = uvVenvPath(workDir)
                    if (venvPath == null) {
                        notify(
                            project,
                            "Could not resolve a uv environment in '${moduleDir.name}'. " +
                                "Is uv installed and an environment created (uv sync)?",
                            NotificationType.ERROR,
                        )
                        return
                    }

                    val interpreter = findVenvInterpreter(venvPath)
                    if (interpreter == null) {
                        notify(
                            project,
                            "uv reported '$venvPath', but no Python interpreter was found there.",
                            NotificationType.ERROR,
                        )
                        return
                    }

                    ApplicationManager.getApplication().invokeLater {
                        assignInterpreterToModule(project, interpreter.toString(), moduleDir)
                    }
                }
            },
        )
    }
}
