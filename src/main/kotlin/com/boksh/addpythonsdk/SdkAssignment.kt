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
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Registers [interpreterPath] as a Python SDK (reusing an existing one that points to the same
 * interpreter) and assigns it to the module that contains [moduleAnchor]. Reports the outcome
 * via balloon.
 *
 * honorExclusion=false: a venv folder is normally excluded from its module, but the excluding
 * module is exactly the one the SDK should be assigned to.
 */
fun assignInterpreterToModule(project: Project, interpreterPath: String, moduleAnchor: VirtualFile) {
    val sdk = ProjectJdkTable.getInstance().allJdks.firstOrNull { sameInterpreter(it.homePath, interpreterPath) }
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

/**
 * True when [existingHomePath] and [interpreterPath] point to the same interpreter. After an exact
 * string match, it compares the venv each interpreter lives in (the parent of its bin/Scripts
 * directory), so a Poetry venv already registered as bin/python is recognized when we resolve it
 * to bin/python3, or vice versa.
 *
 * Symlinks are resolved only on the venv directory, never on the python binary itself: inside a
 * venv bin/python is a symlink to the base interpreter, so resolving it would wrongly equate the
 * venv with the global Python it was created from.
 */
private fun sameInterpreter(existingHomePath: String?, interpreterPath: String): Boolean {
    if (existingHomePath == null) return false
    if (existingHomePath == interpreterPath) return true
    val existingVenv = venvOf(existingHomePath) ?: return false
    return existingVenv == venvOf(interpreterPath)
}

/** The venv directory an interpreter belongs to: the parent of its bin/Scripts directory. */
private fun venvOf(interpreterPath: String): Path? {
    val binDir = try {
        Paths.get(interpreterPath).parent
    } catch (_: Exception) {
        null
    } ?: return null
    val venvDir = binDir.parent ?: return null
    return try {
        venvDir.toRealPath()
    } catch (_: Exception) {
        try {
            venvDir.toAbsolutePath().normalize()
        } catch (_: Exception) {
            null
        }
    }
}

fun notify(project: Project, content: String, type: NotificationType) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Add Python SDK")
        .createNotification(content, type)
        .notify(project)
}
