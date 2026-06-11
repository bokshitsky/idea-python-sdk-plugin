package com.boksh.addpythonsdk

import java.nio.file.Files
import java.nio.file.Path

val VENV_DIR_NAMES = setOf(".venv", "venv", ".virtual_env", ".virtualenv")

fun findVenvInterpreter(venvDir: Path): Path? =
    listOf("bin/python3", "bin/python", "Scripts/python.exe")
        .map(venvDir::resolve)
        .firstOrNull(Files::exists)
