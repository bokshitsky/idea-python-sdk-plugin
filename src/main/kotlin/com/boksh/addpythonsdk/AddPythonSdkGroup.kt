package com.boksh.addpythonsdk

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup

/**
 * The "Add Python SDK" submenu shown on module directories. Visible only when the selected
 * directory contains a pyproject.toml (a Poetry project marker).
 */
class AddPythonSdkGroup : DefaultActionGroup() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.singleOrNull()
        e.presentation.isEnabledAndVisible =
            e.project != null &&
                file != null &&
                file.isDirectory &&
                file.findChild("pyproject.toml") != null
    }
}
