package com.saveourtool.chardet

import org.slf4j.LoggerFactory
import java.io.IOException
import java.lang.Character.UnicodeBlock
import java.lang.Character.UnicodeBlock.ARROWS
import java.lang.Character.UnicodeBlock.BASIC_LATIN
import java.lang.Character.UnicodeBlock.BOX_DRAWING
import java.lang.Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
import java.lang.Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
import java.lang.Character.UnicodeBlock.GREEK
import java.lang.Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
import java.lang.Character.UnicodeBlock.LATIN_1_SUPPLEMENT
import java.lang.Character.UnicodeBlock.LETTERLIKE_SYMBOLS
import java.lang.Character.UnicodeBlock.MATHEMATICAL_OPERATORS
import java.lang.Character.UnicodeBlock.SUPPLEMENTAL_MATHEMATICAL_OPERATORS
import java.lang.System.nanoTime
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.nio.charset.MalformedInputException
import java.nio.file.Files.newByteChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import kotlin.io.path.fileSize
import kotlin.io.path.useLines

abstract class Utf16Detector(private val readContent: Boolean) :
    CharsetDetector {
    protected abstract val byteOrder: ByteOrder

    final override fun invoke(input: Path): Charset? =
        try {
            when {
                input.isUtf16 -> supportedCharsets[0]
                else -> null
            }
        } catch (ioe: IOException) {
            LOGGER.warn("When reading file: $input", ioe)
            null
        }

    @get:Throws(IOException::class)
    private val Path.isUtf16: Boolean
        get() =
            when {
                readContent -> hasUtf16Content
                else -> hasBom
            }

    @get:Throws(IOException::class)
    private val Path.hasUtf16Content: Boolean
        get() =
            try {
                val t0 = nanoTime()
                var lineCount = 0L
                var charCount = 0L

                val charset = supportedCharsets[0]

                try {
                    val xtraUnicodeBlocks = mutableSetOf<UnicodeBlock>()

                    useLines(charset) { lines ->
                        lines.onEach {
                            lineCount++
                        }
                            .flatMap(CharSequence::asSequence)
                            .onEach {
                                charCount++
                            }
                            .map(UnicodeBlock::of)
                            .filterNot(ALLOWED_UNICODE_BLOCKS::contains)
                            .onEach(xtraUnicodeBlocks::add)
                            .none {
                                xtraUnicodeBlocks.size > XTRA_UNICODE_BLOCK_LIMIT
                            }
                    }
                } finally {
                    if (LOGGER.isDebugEnabled) {
                        val nanos = nanoTime() - t0
                        val message = "$this: testing for $charset; $lineCount line(s) read; $charCount char(s) processed in ${nanos / 1000L / 1e3} ms."
                        LOGGER.debug(message)
                    }
                }
            } catch (ignored: MalformedInputException) {
                false
            }

    @get:Throws(IOException::class)
    private val Path.hasBom: Boolean
        get() =
            when {
                fileSize() < BOM_LENGTH -> false

                else -> newByteChannel(this, READ).use { channel ->
                    val head = ByteBuffer.allocate(BOM_LENGTH).order(byteOrder)
                    channel.read(head)
                    head.flip()
                    head.asCharBuffer().get() == BOM
                }
            }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(Utf16Detector::class.java)

        private const val BOM = '\uFEFF'

        private const val BOM_LENGTH = 2

        /**
         * If a text file, when read as `UTF-16LE` or `UTF-16BE`, contains more
         * than [XTRA_UNICODE_BLOCK_LIMIT] Unicode blocks in addition to
         * commonly used [ALLOWED_UNICODE_BLOCKS],
         * remove this charset from the candidates list.
         *
         * It is assumed that at most **1** extra Unicode block is used for
         * locale-specific symbols (e.g.: Cyrillic). For Chinese encoded in
         * UTF-16 w/o any BOM, the limit should be expanded to include **3**
         * extra blocks:
         *
         *  - [CJK_SYMBOLS_AND_PUNCTUATION] (U+3000..U+303F)
         *  - [CJK_UNIFIED_IDEOGRAPHS] (U+4E00..U+9FFF)
         *  - [HALFWIDTH_AND_FULLWIDTH_FORMS] (U+FF00..U+FFEF)
         *
         * Enabling the same check for `UTF-8`, while technically correct, makes
         * no sense,
         * as `UTF-8` detector runs after the `UTF-16*` family.
         *
         * @see ALLOWED_UNICODE_BLOCKS
         */
        private const val XTRA_UNICODE_BLOCK_LIMIT = 3

        /**
         * Unicode blocks that are commonly present in source code files.
         *
         * @see XTRA_UNICODE_BLOCK_LIMIT
         */
        private val ALLOWED_UNICODE_BLOCKS = linkedSetOf(
            BASIC_LATIN,
            LATIN_1_SUPPLEMENT,
            BOX_DRAWING,
            GREEK,
            MATHEMATICAL_OPERATORS,
            SUPPLEMENTAL_MATHEMATICAL_OPERATORS,
            ARROWS,
            LETTERLIKE_SYMBOLS,
        )
    }
}
