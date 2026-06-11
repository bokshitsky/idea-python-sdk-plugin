# Add Python SDK Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** IntelliJ Platform plugin adding an "Add Python SDK" context-menu action on virtualenv folders (`.venv`, `venv`, `.virtual_env`) that registers the interpreter as a Python SDK and assigns it to the containing module.

**Architecture:** Single `AnAction` registered in `ProjectViewPopupMenu`. Pure-JVM helper `findVenvInterpreter` (unit-testable, no platform deps) locates the interpreter; the action uses platform APIs (`SdkConfigurationUtil`, `ProjectJdkTable`, `ModuleRootModificationUtil`) for SDK registration and module assignment. Depends on the `PythonCore` plugin so it works in IDEA Ultimate/CE (with Python plugin) and PyCharm.

**Tech Stack:** Kotlin, Gradle, `org.jetbrains.intellij.platform` Gradle plugin 2.x, JUnit 5. Spec: `docs/superpowers/specs/2026-06-12-add-python-sdk-plugin-design.md`.

---

### Task 1: Gradle project scaffold

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `.gitignore`
- Create: `src/main/resources/META-INF/plugin.xml` (minimal, filled in Task 3)
- Create: gradle wrapper (`gradlew`, `gradle/wrapper/*`)

- [x] **Step 1: Check toolchain availability**

Run: `java -version; which gradle`
Expected: some JDK present. If `gradle` is missing, install via `brew install gradle` or download a distribution; the wrapper is generated once and used afterwards.

- [x] **Step 2: Determine a compatible PythonCore plugin version**

The `PythonCore` marketplace plugin version must match the target IDE build (we target IDEA Community 2024.3, branch `243`). Query the marketplace:

Run: `curl -s "https://plugins.jetbrains.com/api/plugins/PythonCore/updates?size=100" | python3 -c "import json,sys; [print(u['version'], u.get('since'), u.get('until')) for u in json.load(sys.stdin)]" | head -30`

Pick the newest version whose `since`/`until` range includes build `243.*` and use it in `build.gradle.kts` below (placeholder `<PYTHON_CORE_VERSION>`).

- [x] **Step 3: Create the build files**

`settings.gradle.kts`:
```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "add-python-sdk"
```

`build.gradle.kts`:
```kotlin
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.boksh"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3.5")
        plugin("PythonCore", "<PYTHON_CORE_VERSION>")
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(kotlin("stdlib"))
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
        }
    }
    buildSearchableOptions = false
}

tasks.test {
    useJUnitPlatform()
}
```

`gradle.properties`:
```properties
kotlin.stdlib.default.dependency=false
org.gradle.jvmargs=-Xmx2g
```

`.gitignore`:
```
.gradle/
build/
.idea/
.intellijPlatform/
```

`src/main/resources/META-INF/plugin.xml` (minimal for now):
```xml
<idea-plugin>
    <id>com.boksh.add-python-sdk</id>
    <name>Add Python SDK</name>
    <vendor email="bokshitsky@gmail.com">Evgeny Bokshitsky</vendor>
    <description>Adds an "Add Python SDK" action to the Project View context menu of virtualenv folders (.venv, venv, .virtual_env). Registers the interpreter as a Python SDK and assigns it to the containing module.</description>
    <depends>com.intellij.modules.platform</depends>
    <depends>PythonCore</depends>
</idea-plugin>
```

- [x] **Step 4: Generate the wrapper**

Run: `gradle wrapper --gradle-version 8.13`
Expected: `gradlew`, `gradlew.bat`, `gradle/wrapper/` created.

- [x] **Step 5: Verify the project configures and compiles**

Run: `./gradlew compileKotlin`
Expected: `BUILD SUCCESSFUL` (downloads the IDE and PythonCore on first run; may take several minutes).

- [x] **Step 6: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties .gitignore gradlew gradlew.bat gradle/ src/
git commit -m "build: scaffold IntelliJ plugin project with PythonCore dependency"
```

---

### Task 2: Interpreter finder (TDD)

**Files:**
- Create: `src/main/kotlin/com/boksh/addpythonsdk/VenvInterpreterFinder.kt`
- Test: `src/test/kotlin/com/boksh/addpythonsdk/VenvInterpreterFinderTest.kt`

- [x] **Step 1: Write the failing tests**

`src/test/kotlin/com/boksh/addpythonsdk/VenvInterpreterFinderTest.kt`:
```kotlin
package com.boksh.addpythonsdk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class VenvInterpreterFinderTest {

    @TempDir
    lateinit var venv: Path

    private fun createFile(relative: String): Path {
        val file = venv.resolve(relative)
        Files.createDirectories(file.parent)
        return Files.createFile(file)
    }

    @Test
    fun `finds bin python3 on unix layout`() {
        val python3 = createFile("bin/python3")
        assertEquals(python3, findVenvInterpreter(venv))
    }

    @Test
    fun `falls back to bin python when python3 is absent`() {
        val python = createFile("bin/python")
        assertEquals(python, findVenvInterpreter(venv))
    }

    @Test
    fun `prefers python3 over python`() {
        createFile("bin/python")
        val python3 = createFile("bin/python3")
        assertEquals(python3, findVenvInterpreter(venv))
    }

    @Test
    fun `finds Scripts python exe on windows layout`() {
        val python = createFile("Scripts/python.exe")
        assertEquals(python, findVenvInterpreter(venv))
    }

    @Test
    fun `returns null for a folder without interpreter`() {
        createFile("lib/site.py")
        assertNull(findVenvInterpreter(venv))
    }

    @Test
    fun `venv folder names constant covers required names`() {
        assertEquals(setOf(".venv", "venv", ".virtual_env"), VENV_DIR_NAMES)
    }
}
```

- [x] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.boksh.addpythonsdk.VenvInterpreterFinderTest"`
Expected: FAIL — compilation error, `findVenvInterpreter` / `VENV_DIR_NAMES` unresolved.

- [x] **Step 3: Write the implementation**

`src/main/kotlin/com/boksh/addpythonsdk/VenvInterpreterFinder.kt`:
```kotlin
package com.boksh.addpythonsdk

import java.nio.file.Files
import java.nio.file.Path

val VENV_DIR_NAMES = setOf(".venv", "venv", ".virtual_env")

fun findVenvInterpreter(venvDir: Path): Path? =
    listOf("bin/python3", "bin/python", "Scripts/python.exe")
        .map(venvDir::resolve)
        .firstOrNull(Files::exists)
```

- [x] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.boksh.addpythonsdk.VenvInterpreterFinderTest"`
Expected: PASS, 6 tests.

- [x] **Step 5: Commit**

```bash
git add src/main/kotlin/com/boksh/addpythonsdk/VenvInterpreterFinder.kt src/test/kotlin/com/boksh/addpythonsdk/VenvInterpreterFinderTest.kt
git commit -m "feat: add virtualenv interpreter discovery"
```

---

### Task 3: Action and registration

**Files:**
- Create: `src/main/kotlin/com/boksh/addpythonsdk/AddPythonSdkAction.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

The action's behavior depends on live platform services (SDK table, modules), so it is verified by `runIde` in Task 4 rather than unit tests; all pure logic was extracted and tested in Task 2.

- [x] **Step 1: Write the action**

`src/main/kotlin/com/boksh/addpythonsdk/AddPythonSdkAction.kt`:
```kotlin
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
```

- [x] **Step 2: Register the action and notification group in plugin.xml**

Replace `src/main/resources/META-INF/plugin.xml` with:
```xml
<idea-plugin>
    <id>com.boksh.add-python-sdk</id>
    <name>Add Python SDK</name>
    <vendor email="bokshitsky@gmail.com">Evgeny Bokshitsky</vendor>
    <description>Adds an "Add Python SDK" action to the Project View context menu of virtualenv folders (.venv, venv, .virtual_env). Registers the interpreter as a Python SDK and assigns it to the containing module.</description>
    <depends>com.intellij.modules.platform</depends>
    <depends>PythonCore</depends>

    <extensions defaultExtensionNs="com.intellij">
        <notificationGroup id="Add Python SDK" displayType="BALLOON"/>
    </extensions>

    <actions>
        <action id="AddPythonSdk.AddPythonSdkAction"
                class="com.boksh.addpythonsdk.AddPythonSdkAction"
                text="Add Python SDK"
                description="Register this virtualenv as a Python SDK and assign it to the module">
            <add-to-group group-id="ProjectViewPopupMenu" anchor="last"/>
        </action>
    </actions>
</idea-plugin>
```

- [x] **Step 3: Build the plugin**

Run: `./gradlew buildPlugin`
Expected: `BUILD SUCCESSFUL`, zip in `build/distributions/`.

- [x] **Step 4: Run the plugin verifier checks built into the build**

Run: `./gradlew verifyPluginStructure`
Expected: no errors (warnings acceptable).

- [x] **Step 5: Commit**

```bash
git add src/main/kotlin/com/boksh/addpythonsdk/AddPythonSdkAction.kt src/main/resources/META-INF/plugin.xml
git commit -m "feat: add Add Python SDK action for virtualenv folders"
```

---

### Task 4: Manual verification

**Files:** none (verification only)

- [x] **Step 1: Launch a sandbox IDE**

Run: `./gradlew runIde`
Expected: IDEA Community 2024.3 sandbox starts with the plugin and PythonCore installed.

- [x] **Step 2: Verify in the sandbox (user-driven)**

1. Create/open a project containing a virtualenv: `python3 -m venv .venv` in the project root.
2. Right-click `.venv` in the Project View → "Add Python SDK" is present.
3. Right-click any other folder (e.g. `src`) → the item is absent.
4. Invoke the action on `.venv` → success notification; File → Project Structure → SDKs shows the new Python SDK; the module SDK is set to it.
5. Invoke again → no duplicate SDK appears in the SDK table.

- [x] **Step 3: Commit plan checkboxes and wrap up**

```bash
git add docs/
git commit -m "docs: mark implementation plan complete"
```
