package com.saveourtool.chardet

import java.nio.charset.Charset
import java.nio.file.Path

internal interface CharsetDetector : (Path) -> Charset? {
    val supportedCharsets: List<Charset>
}
