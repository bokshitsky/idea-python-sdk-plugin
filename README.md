# Add Python SDK

An IntelliJ Platform plugin that adds an **"Add Python SDK"** item to the Project View
context menu of virtualenv folders. One click registers the interpreter inside the folder
as a Python SDK and assigns it to the module that contains the folder.

## Features

- Adds an **Add Python SDK** action to the right-click menu of virtualenv folders
  (`.venv`, `venv`, `.virtual_env`, `.virtualenv`).
- Adds an **Add Python SDK → From Poetry** submenu to the right-click menu of module
  folders that contain a `pyproject.toml`. It runs `poetry env info --path` in that folder
  and uses the currently active Poetry virtualenv.
- Discovers the interpreter inside the folder (`bin/python3`, `bin/python` on Unix,
  `Scripts/python.exe` on Windows).
- Reuses an existing SDK with the same interpreter path instead of creating duplicates.
- Assigns the SDK to the module that contains the folder, even when a virtualenv folder
  is excluded from that module.
- Reports the result through balloon notifications.

## Supported IDEs

Works everywhere the Python plugin is available (`PythonCore`):

- IntelliJ IDEA Ultimate (Python plugin)
- IntelliJ IDEA Community (Python Community Edition plugin)
- PyCharm Professional and Community

Compatible with builds since `242` (2024.2 and later).

## Usage

### From a virtualenv folder

1. Right-click a virtualenv folder (`.venv`, `venv`, `.virtual_env`, `.virtualenv`) in
   the Project View.
2. Choose **Add Python SDK**.
3. The interpreter is registered as a Python SDK and set as the SDK of the containing
   module. A notification confirms which SDK was assigned to which module.

### From Poetry

1. Right-click a module folder that contains a `pyproject.toml` in the Project View.
2. Choose **Add Python SDK → From Poetry**.
3. The plugin runs `poetry env info --path` in that folder, registers the interpreter of
   the active Poetry virtualenv as a Python SDK, and assigns it to the module. A
   notification confirms the result.

   Requires `poetry` to be on your shell `PATH` and an environment to exist
   (`poetry install`).

## Build plugin

```
./gradlew buildPlugin
```

The packaged plugin is written to `build/distributions/`.

## Release

Releases are published to GitHub Releases by the `.github/workflows/release.yml`
workflow, which triggers on a pushed `v*` tag, builds the plugin, and attaches the
zip to the release. The plugin version is taken from the tag.

Commit your changes first, then run:

```
./release.sh 1.0.0
```

This creates the `v1.0.0` tag and pushes it, which starts the release workflow.
