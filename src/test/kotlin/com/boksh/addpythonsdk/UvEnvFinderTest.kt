package com.boksh.addpythonsdk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class UvEnvFinderTest {

    @TempDir
    lateinit var tmp: Path

    private fun makeVenv(): Path {
        val venv = tmp.resolve(".venv")
        Files.createDirectories(venv.resolve("bin"))
        Files.createFile(venv.resolve("pyvenv.cfg"))
        return venv
    }

    @Test
    fun `returns the venv that contains the interpreter`() {
        val venv = makeVenv()
        assertEquals(venv, parseUvVenvPath("${venv.resolve("bin/python3")}\n"))
    }

    @Test
    fun `takes the first non-blank line and trims it`() {
        val venv = makeVenv()
        assertEquals(venv, parseUvVenvPath("\n  ${venv.resolve("bin/python3")}  \nignored\n"))
    }

    @Test
    fun `returns null when the interpreter is not inside a venv`() {
        Files.createDirectories(tmp.resolve("python/bin"))
        assertNull(parseUvVenvPath("${tmp.resolve("python/bin/python3.14")}\n"))
    }

    @Test
    fun `returns null for blank output`() {
        assertNull(parseUvVenvPath("   \n  \n"))
    }

    @Test
    fun `returns null for a path without a grandparent`() {
        assertNull(parseUvVenvPath("python3\n"))
    }
}
