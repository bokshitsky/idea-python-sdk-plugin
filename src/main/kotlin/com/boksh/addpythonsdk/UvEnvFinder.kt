package com.boksh.addpythonsdk

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Runs `uv python find` in [workDir] and returns the path of the uv project virtualenv,
 * or null if uv is unavailable or the project has no environment yet.
 *
 * Inside a uv project, `uv python find` returns the interpreter of the project's `.venv`
 * (also for workspace members and subdirectories). When no environment exists it silently
 * falls back to a system or uv-managed Python, so the result is accepted only if it lives
 * inside a virtualenv.
 *
 * Uses the console parent environment so the user's shell PATH (where `uv` typically
 * lives) is honored even when the IDE was launched from a GUI with a minimal PATH.
 */
fun uvVenvPath(workDir: Path): Path? {
    val commandLine = GeneralCommandLine("uv", "python", "find", "--no-python-downloads")
        .withWorkDirectory(workDir.toFile())
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)

    val output = try {
        ExecUtil.execAndGetOutput(commandLine)
    } catch (_: Exception) {
        return null
    }
    if (output.exitCode != 0) return null
    return parseUvVenvPath(output.stdout)
}

/**
 * Extracts the virtualenv directory from `uv python find` output (first non-blank line is
 * the interpreter path). Returns null when that interpreter is not inside a virtualenv.
 */
fun parseUvVenvPath(stdout: String): Path? {
    val interpreter = stdout.lineSequence()
        .map(String::trim)
        .firstOrNull(String::isNotEmpty)
        ?.let(Paths::get)
        ?: return null
    // <venv>/bin/python3 on Unix, <venv>\Scripts\python.exe on Windows.
    val venv = interpreter.parent?.parent ?: return null
    return venv.takeIf { Files.isRegularFile(it.resolve("pyvenv.cfg")) }
}
