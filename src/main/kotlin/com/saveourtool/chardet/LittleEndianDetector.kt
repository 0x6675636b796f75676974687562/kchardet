package com.saveourtool.chardet

import java.nio.ByteOrder
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.nio.charset.Charset
import kotlin.text.Charsets.UTF_16LE

class LittleEndianDetector(readContent: Boolean) : Utf16Detector(readContent) {
    override val supportedCharsets: List<Charset> = listOf(UTF_16LE)

    override val byteOrder: ByteOrder
        get() =
            LITTLE_ENDIAN
}
