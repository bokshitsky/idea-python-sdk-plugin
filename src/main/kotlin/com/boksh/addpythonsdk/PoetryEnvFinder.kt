package com.boksh.addpythonsdk

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Runs `poetry env info --path` in [workDir] and returns the path of the currently active
 * Poetry virtualenv, or null if Poetry is unavailable or no environment is active.
 *
 * Uses the console parent environment so the user's shell PATH (where `poetry` typically
 * lives) is honored even when the IDE was launched from a GUI with a minimal PATH.
 */
fun poetryVenvPath(workDir: Path): Path? {
    val commandLine = GeneralCommandLine("poetry", "env", "info", "--path")
        .withWorkDirectory(workDir.toFile())
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)

    val output = try {
        ExecUtil.execAndGetOutput(commandLine)
    } catch (_: Exception) {
        return null
    }
    if (output.exitCode != 0) return null
    return parsePoetryEnvPath(output.stdout)
}

/** Extracts the venv path from `poetry env info --path` output (first non-blank line). */
fun parsePoetryEnvPath(stdout: String): Path? =
    stdout.lineSequence()
        .map(String::trim)
        .firstOrNull(String::isNotEmpty)
        ?.let(Paths::get)
