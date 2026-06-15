package com.boksh.addpythonsdk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class PoetryEnvFinderTest {

    @Test
    fun `parses the venv path from a single line`() {
        val out = "/home/u/.cache/pypoetry/virtualenvs/proj-AbCdEf-py3.11\n"
        assertEquals(
            Paths.get("/home/u/.cache/pypoetry/virtualenvs/proj-AbCdEf-py3.11"),
            parsePoetryEnvPath(out),
        )
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals(Paths.get("/tmp/venv"), parsePoetryEnvPath("   /tmp/venv   \n"))
    }

    @Test
    fun `takes the first non-blank line`() {
        assertEquals(Paths.get("/tmp/venv"), parsePoetryEnvPath("\n\n/tmp/venv\nignored\n"))
    }

    @Test
    fun `returns null for blank output`() {
        assertNull(parsePoetryEnvPath("   \n  \n"))
    }

    @Test
    fun `returns null for empty output`() {
        assertNull(parsePoetryEnvPath(""))
    }
}
