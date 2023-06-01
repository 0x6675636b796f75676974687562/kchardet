package com.saveourtool.chardet

import io.kotest.matchers.paths.shouldHaveFileSize
import io.kotest.matchers.shouldBe
import org.apache.log4j.ConsoleAppender
import org.apache.log4j.Level.TRACE
import org.apache.log4j.Logger.getRootLogger
import org.apache.log4j.PatternLayout
import org.junit.jupiter.api.BeforeAll
import java.nio.charset.Charset
import java.nio.charset.UnmappableCharacterException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.text.Charsets.ISO_8859_1
import kotlin.text.Charsets.US_ASCII
import kotlin.text.Charsets.UTF_16BE
import kotlin.text.Charsets.UTF_16LE
import kotlin.text.Charsets.UTF_8

class CharsetUtilitiesTest {
    @Test
    fun ascii() {
        assertFalse('\u0000'.isAscii)
        assertTrue(7.toChar().isAscii)
        assertTrue('\b'.isAscii)
        assertTrue('\t'.isAscii)
        assertTrue('\n'.isAscii)
        assertTrue(11.toChar().isAscii)
        assertTrue(12.toChar().isAscii)
        assertTrue('\r'.isAscii)
        assertTrue(27.toChar().isAscii)
        assertTrue('A'.isAscii)
        assertFalse('\u00A9'.isAscii)
    }

    @Test
    fun binary() {
        assertTrue('\u0000'.code.toByte().isBinary)
        assertFalse('A'.code.toByte().isBinary)
    }

    @Test
    fun `assembly info`() {
        /*
         * If 8-bit contents is stripped from AssemblyInfo.cs,
         * its charset will be detected as US-ASCII.
         */
        assertEquals(UTF_8, loadResource("AssemblyInfo.cs").charsetOrNull)
        assertEquals(UTF_8, loadResource("AssemblyInfo-bom.cs").charsetOrNull)
    }

    @Test
    fun `mfc resources`() {
        assertEquals(
            UTF_16LE,
            loadResource("c++-mfc.rc").charsetOrNull,
            "detected charset of MFC resource file in UTF-16LE w/o BOM"
        )
        assertEquals(
            UTF_16LE,
            loadResource("c++-mfc-bom.rc").charsetOrNull,
            "detected charset of MFC resource file in UTF-16LE with BOM"
        )
    }

    @Test
    fun `latin-1`() {
        val resources = linkedMapOf<String, Charset>().apply {
            this["latin-ascii.txt"] = US_ASCII
            this["latin-utf8-bom.txt"] = UTF_8
            this["latin-utf16be.txt"] = UTF_16BE
            this["latin-utf16be-bom.txt"] = UTF_16BE
            this["latin-utf16le.txt"] = UTF_16LE
            this["latin-utf16le-bom.txt"] = UTF_16LE
        }

        for ((name, expectedCharset) in resources) {
            val path = loadResource(name)
            assertEquals(expectedCharset, path.charsetOrNull, "Detected charset of ${path.fileName}")
            val lines = path.readLines(expectedCharset)
            assertEquals(2, lines.size)
            val it: Iterator<String> = lines.iterator()
            val first = it.next()
            assertEquals("ABCDEFGHIJKLMNOPQRSTUVWXYZ", if (first[0] == '\uFEFF') first.substring(1) else first)
            val second = it.next()
            assertEquals("abcdefghijklmnopqrstuvwxyz", second)
        }
    }

    @Test
    fun cyrillic() {
        /*
         * 8-bit Cyrillic and Unicode Cyrillic w/o BOM is *not* detected:
         * we need frequency tables to do this.
         */
        assertNull(
            loadResource("cyrillic-cp1251.txt").charsetOrNull,
            "Cyrillic, windows-1251: detected charset"
        )
        assertEquals(
            UTF_16BE,
            loadResource("cyrillic-utf16be.txt").charsetOrNull,
            "Cyrillic, UTF-16BE w/o BOM: detected charset"
        )
        assertEquals(
            UTF_16LE,
            loadResource("cyrillic-utf16le.txt").charsetOrNull,
            "Cyrillic, UTF-16LE w/o BOM: detected charset"
        )

        val resources = linkedMapOf<String, Charset>().apply {
            this["cyrillic-utf8-bom.txt"] = UTF_8
            this["cyrillic-utf16be-bom.txt"] = UTF_16BE
            this["cyrillic-utf16le-bom.txt"] = UTF_16LE
        }

        for ((name, expectedCharset) in resources) {
            val path = loadResource(name)
            assertEquals(expectedCharset, path.charsetOrNull)
            val lines = path.readLines(expectedCharset)
            assertEquals(2, lines.size)
            val it: Iterator<String> = lines.iterator()
            val first = it.next()
            assertEquals("\uFEFF\u0410\u0411\u0412\u0413\u0414\u0415\u0401\u0416\u0417\u0418\u0419\u041a\u041b\u041c\u041d\u041e\u041f\u0420\u0421\u0422\u0423\u0424\u0425\u0426\u0427\u0428\u0429\u042a\u042b\u042c\u042d\u042e\u042f", first)
            val second = it.next()
            assertEquals("\u0430\u0431\u0432\u0433\u0434\u0435\u0451\u0436\u0437\u0438\u0439\u043a\u043b\u043c\u043d\u043e\u043f\u0440\u0441\u0442\u0443\u0444\u0445\u0446\u0447\u0448\u0449\u044a\u044b\u044c\u044d\u044e\u044f", second)
        }
    }

    @Test
    fun `cyrillic (mode line)`() {
        loadResource("cyrillic-cp1251-modeline.py").charsetOrNull shouldBe Charset.forName("windows-1251")
    }

    /**
     * Tests charset auto-detection for the UTF-8 without BOM case.
     */
    @Test
    fun `cyrillic (UTF-8 without BOM)`() {
        assertEquals(
            UTF_8,
            loadResource("AssemblyInfo-cyrillic-utf8.cs").charsetOrNull,
            "Cyrillic, UTF-8 w/o BOM: detected charset",
        )
    }

    /**
     * Tests charset auto-detection for empty files.
     */
    @Test
    fun empty() {
        val path = loadResource("empty.txt")
        path shouldHaveFileSize 0L
        path.charsetOrNull shouldBe US_ASCII
        path.charsetOrDefault shouldBe US_ASCII
    }

    /**
     * 8-bit charsets like `windows-1251` may produce an
     * [UnmappableCharacterException] when used.
     */
    @Test
    fun `default charset`() {
        val path = loadResource("AssemblyInfo-cyrillic-utf8.cs")

        try {
            path.readLines(US_ASCII)
            fail("A CharacterCodingException should have been thrown.")
        } catch (cce: CharacterCodingException) {
            println(cce.message)
        }

        try {
            path.readLines(Charset.forName("windows-1251"))
            fail("A CharacterCodingException should have been thrown.")
        } catch (cce: CharacterCodingException) {
            println(cce.message)
        }

        path.readLines(ISO_8859_1)
    }

    @Test
    fun `malformed unicode`() {
        /*
         * Malformed UTF-8 w/o any BOM: content scanning is
         * automatically attempted, hence null charset.
         */
        assertNull(loadResource("malformed-utf8.txt").charsetOrNull)

        /*
         * Malformed UTF-8 with BOM: charset detected based on BOM.
         */
        assertEquals(UTF_8, loadResource("malformed-utf8-bom.txt").charsetOrNull)

        /*
         * Malformed UTF-8 with BOM: forced content scanning encounters
         * non-UTF-8 input, so null is returned.
         */
        assertNull(loadResource("malformed-utf8-bom.txt").charsetOrNull(readContent = true))
    }

    @Test
    fun `test binary`() {
        val path = loadResource("binary.bin")
        assertNull(path.charsetOrNull)
        assertNull(path.charsetOrDefault)
        assertTrue(path.isBinary)
        assertTrue(loadResource("latin-utf16be.txt").isBinary)
        assertTrue(loadResource("latin-utf16be-bom.txt").isBinary)
        assertTrue(loadResource("latin-utf16le.txt").isBinary)
        assertTrue(loadResource("latin-utf16le-bom.txt").isBinary)
        assertTrue(loadResource("cyrillic-utf16be.txt").isBinary)
        assertTrue(loadResource("cyrillic-utf16be-bom.txt").isBinary)
        assertTrue(loadResource("cyrillic-utf16le.txt").isBinary)
        assertTrue(loadResource("cyrillic-utf16le-bom.txt").isBinary)
    }

    @Test
    fun `chinese (GBK)`() {
        loadResource("chinese-gbk.txt").charsetOrNull shouldBe Charset.forName("GBK")
    }

    @Test
    fun `chinese (UTF-8)`() {
        loadResource("chinese-utf8.txt").charsetOrNull shouldBe UTF_8
        loadResource("chinese-utf8-bom.txt").charsetOrNull shouldBe UTF_8
    }

    @Test
    fun `chinese (UTF-16)`() {
        loadResource("chinese-utf16be.txt").charsetOrNull shouldBe UTF_16BE
        loadResource("chinese-utf16be-bom.txt").charsetOrNull shouldBe UTF_16BE
        loadResource("chinese-utf16le.txt").charsetOrNull shouldBe UTF_16LE
        loadResource("chinese-utf16le-bom.txt").charsetOrNull shouldBe UTF_16LE
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun setUpLoggers() {
            val appender = ConsoleAppender()
            appender.name = "CONSOLE_OUT"
            appender.target = "System.out"
            appender.layout = PatternLayout("%m%n")
            appender.activateOptions()

            val rootLogger = getRootLogger()
            rootLogger.addAppender(appender)
            rootLogger.level = TRACE
        }

        private fun loadResource(name: String): Path {
            val url = CharsetUtilitiesTest::class.java.getResource("/$name")
            assertNotNull(url)
            return Paths.get(url.toURI())
        }
    }
}
