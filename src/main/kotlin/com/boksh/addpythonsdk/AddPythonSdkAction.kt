package com.boksh.addpythonsdk

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import java.nio.file.Paths

class AddPythonSdkAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.singleOrNull()
        e.presentation.isEnabledAndVisible =
            e.project != null && file != null && file.isDirectory && file.name in VENV_DIR_NAMES
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val venvDir = e.getData(CommonDataKeys.VIRTUAL_FILE)?.takeIf { it.isDirectory } ?: return

        val interpreter = findVenvInterpreter(Paths.get(venvDir.path))
        if (interpreter == null) {
            notify(project, "No Python interpreter found in '${venvDir.name}' — is it a virtualenv?", NotificationType.ERROR)
            return
        }

        assignInterpreterToModule(project, interpreter.toString(), venvDir)
    }
}
