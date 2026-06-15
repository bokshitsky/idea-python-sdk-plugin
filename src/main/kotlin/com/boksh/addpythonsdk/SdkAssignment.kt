package com.boksh.addpythonsdk

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.sdk.PythonSdkType

/**
 * Registers [interpreterPath] as a Python SDK (reusing an existing one with the same path)
 * and assigns it to the module that contains [moduleAnchor]. Reports the outcome via balloon.
 *
 * honorExclusion=false: a venv folder is normally excluded from its module, but the excluding
 * module is exactly the one the SDK should be assigned to.
 */
fun assignInterpreterToModule(project: Project, interpreterPath: String, moduleAnchor: VirtualFile) {
    val sdk = ProjectJdkTable.getInstance().allJdks.firstOrNull { it.homePath == interpreterPath }
        ?: SdkConfigurationUtil.createAndAddSDK(interpreterPath, PythonSdkType.getInstance())
    if (sdk == null) {
        notify(project, "Failed to create a Python SDK for '$interpreterPath'.", NotificationType.ERROR)
        return
    }

    val module = ProjectFileIndex.getInstance(project).getModuleForFile(moduleAnchor, false)
    if (module == null) {
        notify(
            project,
            "SDK '${sdk.name}' added, but '${moduleAnchor.name}' is outside any module — assign it manually in Project Structure.",
            NotificationType.INFORMATION,
        )
        return
    }

    ModuleRootModificationUtil.setModuleSdk(module, sdk)
    notify(project, "SDK '${sdk.name}' assigned to module '${module.name}'.", NotificationType.INFORMATION)
}

fun notify(project: Project, content: String, type: NotificationType) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Add Python SDK")
        .createNotification(content, type)
        .notify(project)
}
