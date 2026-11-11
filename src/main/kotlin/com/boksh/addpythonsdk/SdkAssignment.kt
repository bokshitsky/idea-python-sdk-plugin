package com.boksh.addpythonsdk

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Registers [interpreterPath] as a Python SDK (reusing an existing one that points to the same
 * interpreter) and assigns it to the module that contains [moduleAnchor]. A newly created SDK is
 * tied to that module's content root, as the IDE does for venvs it creates. Reports the outcome
 * via balloon.
 *
 * honorExclusion=false: a venv folder is normally excluded from its module, but the excluding
 * module is exactly the one the SDK should be assigned to.
 */
fun assignInterpreterToModule(project: Project, interpreterPath: String, moduleAnchor: VirtualFile) {
    val fileIndex = ProjectFileIndex.getInstance(project)
    val module = fileIndex.getModuleForFile(moduleAnchor, false)
    val moduleRoot = fileIndex.getContentRootForFile(moduleAnchor, false)?.toNioPath()

    val sdk = ProjectJdkTable.getInstance().allJdks.firstOrNull { sameInterpreter(it.homePath, interpreterPath) }
        ?: createAndAddPythonSdk(interpreterPath, moduleRoot)
    if (sdk == null) {
        notify(project, "Failed to create a Python SDK for '$interpreterPath'.", NotificationType.ERROR)
        return
    }

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
 * Creates a Python SDK for [interpreterPath] and registers it. The SDK is given
 * [PythonSdkAdditionalData] up front: newer Python plugins (2026.x) reject an SDK without it
 * ("created by buggy code"), so the plain SdkConfigurationUtil.createAndAddSDK no longer works.
 */
private fun createAndAddPythonSdk(interpreterPath: String, moduleRoot: Path?): Sdk? {
    val home = LocalFileSystem.getInstance().refreshAndFindFileByPath(interpreterPath) ?: return null
    // Venv and Poetry flavors carry no extra data; any other flavor would need its own data type.
    val flavor = PythonSdkFlavor.getFlavor(interpreterPath)
        ?.takeIf { it.flavorDataClass == PyFlavorData.Empty::class.java } ?: return null
    @Suppress("UNCHECKED_CAST")
    val flavorAndData = PyFlavorAndData(PyFlavorData.Empty, flavor as PythonSdkFlavor<PyFlavorData.Empty>)
    val sdkType = PythonSdkType.getInstance()
    // Same steps as the internal SdkConfigurationUtil.setupSdk, done through public API:
    // create the SDK, then let the Python plugin read its version and library paths.
    val sdk = try {
        SdkConfigurationUtil.createSdk(
            ProjectJdkTable.getInstance().allJdks.asList(),
            home,
            sdkType,
            newPythonSdkAdditionalData(flavorAndData, moduleRoot),
            null,
        ).also(sdkType::setupSdkPaths)
    } catch (e: Exception) {
        if (e is ControlFlowException) throw e
        LOG.warn("Failed to set up a Python SDK for '$interpreterPath'", e)
        return null
    }
    SdkConfigurationUtil.addSdk(sdk)
    return sdk
}

private val LOG = Logger.getInstance("#com.boksh.addpythonsdk")

/**
 * Creates [PythonSdkAdditionalData] with whichever constructor this IDE offers publicly:
 * - 2026.2+: (PyFlavorAndData, Path) — the Path is the module root the SDK belongs to; the
 *   single-argument constructor still exists there but is internal and deprecated.
 * - 2025.1–2026.1: only (PyFlavorAndData).
 *
 * Both are looked up by reflection, so one build runs on every supported IDE without calling an
 * internal API. The cost: the compiler cannot check these calls, so if JetBrains changes the
 * constructors this fails at run time (reported as "Failed to create a Python SDK").
 */
private fun newPythonSdkAdditionalData(flavorAndData: PyFlavorAndData<*, *>, moduleRoot: Path?): PythonSdkAdditionalData {
    val cls = PythonSdkAdditionalData::class.java
    if (moduleRoot != null) {
        val withRoot = try {
            cls.getConstructor(PyFlavorAndData::class.java, Path::class.java)
        } catch (_: NoSuchMethodException) {
            null
        }
        if (withRoot != null) return withRoot.newInstance(flavorAndData, moduleRoot)
    }
    return cls.getConstructor(PyFlavorAndData::class.java).newInstance(flavorAndData)
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
