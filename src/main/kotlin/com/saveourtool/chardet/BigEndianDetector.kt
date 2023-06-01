package com.saveourtool.chardet

import java.nio.ByteOrder
import java.nio.ByteOrder.BIG_ENDIAN
import java.nio.charset.Charset
import kotlin.text.Charsets.UTF_16BE

class BigEndianDetector(readContent: Boolean) : Utf16Detector(readContent) {
    override val supportedCharsets: List<Charset> = listOf(UTF_16BE)

    override val byteOrder: ByteOrder
        get() =
            BIG_ENDIAN
}
