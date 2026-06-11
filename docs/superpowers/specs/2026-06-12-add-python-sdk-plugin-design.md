# Add Python SDK — IntelliJ plugin design

Date: 2026-06-12

## Goal

An IntelliJ Platform plugin that adds an **"Add Python SDK"** item to the Project View
context menu of virtualenv folders. One click registers the interpreter from the folder
as a Python SDK and assigns it to the module containing the folder.

## Target environments

Works everywhere the Python plugin is available:

- IntelliJ IDEA Ultimate (Python plugin)
- IntelliJ IDEA Community (Python Community Edition plugin)
- PyCharm (Professional and Community)

Achieved via `<depends>PythonCore</depends>` in `plugin.xml` — `PythonCore` is the base
Python plugin that both `Pythonid` (Pro) and PyCharm provide.

## Tech stack

- Kotlin
- Gradle + `org.jetbrains.intellij.platform` plugin 2.x
- Build target: IntelliJ IDEA Community + `PythonCore` plugin dependency for compilation
- Verification: `./gradlew runIde`

## Behavior

### Menu visibility (`update()`)

The action is visible/enabled when exactly one directory is selected in the Project View
and its name is one of: `.venv`, `venv`, `.virtual_env`.

No filesystem I/O in `update()` (runs on EDT) — only the name check.
`getActionUpdateThread()` returns `BGT`.

### Action (`actionPerformed()`)

1. Locate the interpreter inside the folder, in order:
   `bin/python3`, `bin/python` (Unix), `Scripts/python.exe` (Windows).
   If none found → error notification: folder does not look like a virtualenv. Stop.
2. Look for an existing SDK in `ProjectJdkTable` with the same home path.
   If found, reuse it (no duplicates); otherwise create one via
   `SdkConfigurationUtil.createAndAddSDK(interpreterPath, PythonSdkType.getInstance())`.
3. Find the module containing the folder (`ModuleUtilCore.findModuleForFile`).
   - Module found → `ModuleRootModificationUtil.setModuleSdk(module, sdk)`.
   - No module → SDK is registered only; notification explains it was added to the SDK
     table but not assigned.
4. Success notification naming the SDK and the module it was assigned to.

### Registration

`plugin.xml`: action added to the `ProjectViewPopupMenu` group, text "Add Python SDK".

## Error handling

- No interpreter inside the folder → error notification, nothing created.
- SDK creation fails (returns null) → error notification.
- Folder outside any module → SDK registered, info notification, no assignment.

## Components

- `AddPythonSdkAction` — the single `AnAction` with the logic above.
- `plugin.xml` — action registration, `PythonCore` dependency, notification group.

## Testing

Manual verification via `./gradlew runIde`: open a project containing a `.venv`,
check menu visibility on matching/non-matching folders, run the action, confirm the
SDK appears in Project Structure and is set as the module SDK. Optional small unit
test for the interpreter-discovery helper.

## Out of scope

- Creating virtualenvs (only registering existing ones)
- Content-based venv detection for arbitrary folder names
- Setting the project-level SDK
