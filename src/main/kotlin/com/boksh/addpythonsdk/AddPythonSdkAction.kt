package com.boksh.addpythonsdk

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.jetbrains.python.sdk.PythonSdkType
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

        val interpreterPath = interpreter.toString()
        val sdk = ProjectJdkTable.getInstance().allJdks.firstOrNull { it.homePath == interpreterPath }
            ?: SdkConfigurationUtil.createAndAddSDK(interpreterPath, PythonSdkType.getInstance())
        if (sdk == null) {
            notify(project, "Failed to create a Python SDK for '$interpreterPath'.", NotificationType.ERROR)
            return
        }

        val module = ModuleUtilCore.findModuleForFile(venvDir, project)
        if (module == null) {
            notify(
                project,
                "SDK '${sdk.name}' added, but '${venvDir.name}' is outside any module — assign it manually in Project Structure.",
                NotificationType.INFORMATION,
            )
            return
        }

        ModuleRootModificationUtil.setModuleSdk(module, sdk)
        notify(project, "SDK '${sdk.name}' assigned to module '${module.name}'.", NotificationType.INFORMATION)
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Add Python SDK")
            .createNotification(content, type)
            .notify(project)
    }
}
