package com.example.bluehive.testutil

import org.junit.Assert.assertEquals
import org.junit.Test

/** The guards below lean on stripComments: it must drop comments and nothing else. */
class MainSourceTest {

    @Test
    fun `comments go, code stays`() {
        assertEquals("val a = 1 \nval b = 2", MainSource.stripComments("val a = 1 // one\nval b = /* two */2"))
    }

    @Test
    fun `a URL inside a string is not a comment`() {
        val code = "val u = \"https://example.com/a\" // note"
        assertEquals("val u = \"https://example.com/a\" \n", MainSource.stripComments(code))
    }

    @Test
    fun `nested block comments and quote characters`() {
        assertEquals("x  y '\"' z", MainSource.stripComments("x /* a /* b */ c */ y '\"' z"))
    }

    @Test
    fun `raw strings keep their slashes`() {
        val code = "val r = \"\"\"a // b\"\"\" // gone"
        assertEquals("val r = \"\"\"a // b\"\"\" \n", MainSource.stripComments(code))
    }
}
