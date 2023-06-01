package com.saveourtool.chardet

import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.text.Charsets.US_ASCII

class AsciiDetector(private val readContent: Boolean) : CharsetDetector {
    override fun invoke(input: Path): Charset? =
        try {
            when {
                readContent && input.isAscii -> supportedCharsets[0]

                else -> null
            }
        } catch (ioe: IOException) {
            LOGGER.warn("When reading file: $input", ioe)
            null
        }

    override val supportedCharsets: List<Charset> = listOf(US_ASCII)

    companion object {
        private val LOGGER = LoggerFactory.getLogger(AsciiDetector::class.java)
    }
}
