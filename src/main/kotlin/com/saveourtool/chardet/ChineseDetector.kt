package com.saveourtool.chardet

import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Path

class ChineseDetector(private val readContent: Boolean) : CharsetDetector {
    override fun invoke(input: Path): Charset? =
        try {
            when {
                readContent -> supportedCharsets.firstOrNull(input::hasCharset)

                else -> null
            }
        } catch (ioe: IOException) {
            null
        }

    override val supportedCharsets: List<Charset> by lazy {
        sequenceOf(
            /*
             * The original standard, dated 1980 (a.k.a. EUC-CN).
             */
            "GB2312",

            /*-
             * An extension of GB2312,
             * established in 1993 and standardized in 1995 (GBK 1.0).
             *
             * Aliases: windows-936, CP936.
             *
             * Should be prioritized over MS936.
             */
            "GBK",

            /*
             * MS936.
             *
             * This is a Microsoft's implementation of GBK
             * in Windows 95 and Windows NT 3.51.
             *
             * It's an extension of the original GBK (1993)
             * and a subset of GBK 1.0 (1995).
             */
            "x-mswin-936",

            /*
             * An extension of GBK, dated 2000, for Simplified Chinese (zh_CN).
             */
            "GB18030",

            /*
             * Traditional Chinese (zh_TW, zh_HK), dated 1984.
             */
            "Big5",
        )
            .filter(Charset::isSupported)
            .map(Charset::forName)
            .toList()
    }
}
