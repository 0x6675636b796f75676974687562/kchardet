package com.saveourtool.chardet

import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.MalformedInputException
import java.nio.file.Files.newByteChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import kotlin.io.path.fileSize
import kotlin.io.path.useLines
import kotlin.system.measureNanoTime
import kotlin.text.Charsets.UTF_8

class Utf8Detector(private val readContent: Boolean) : CharsetDetector {
    override fun invoke(input: Path): Charset? =
        try {
            when {
                input.isUtf8 -> supportedCharsets[0]
                else -> null
            }
        } catch (ioe: IOException) {
            LOGGER.warn("When reading file: $input", ioe)
            null
        }

    override val supportedCharsets: List<Charset> = listOf(UTF_8)

    @get:Throws(IOException::class)
    private val Path.isUtf8: Boolean
        get() =
            when {
                readContent -> hasUtf8Content
                else -> hasBom
            }

    @get:Throws(IOException::class)
    private val Path.hasUtf8Content: Boolean
        get() =
            try {
                val charset = supportedCharsets[0]

                val lineCount: Int
                val nanos = measureNanoTime {
                    lineCount = useLines(charset) { lines ->
                        lines.count()
                    }
                }

                if (LOGGER.isDebugEnabled) {
                    val message = "$this: testing for $charset; $lineCount line(s) read in ${nanos / 1000L / 1e3} ms."
                    LOGGER.debug(message)
                }

                true
            } catch (ignored: MalformedInputException) {
                false
            }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(Utf8Detector::class.java)

        private val BOM = byteArrayOf(-17, -69, -65)

        @get:Throws(IOException::class)
        private val Path.hasBom: Boolean
            get() =
                when {
                    fileSize() < BOM.size -> false

                    else -> newByteChannel(this, READ).use { channel ->
                        val head = ByteBuffer.allocate(BOM.size)
                        channel.read(head)
                        head.flip()
                        head == ByteBuffer.wrap(BOM)
                    }
                }
    }
}
