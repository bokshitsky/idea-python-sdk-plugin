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
        assertEquals(setOf(".venv", "venv", ".virtual_env", ".virtualenv"), VENV_DIR_NAMES)
    }
}
