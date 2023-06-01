@file:JvmName("CharsetUtilities")

package com.saveourtool.chardet

import java.io.IOException
import java.lang.Character.UnicodeBlock.BASIC_LATIN
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.useLines
import kotlin.text.Charsets.ISO_8859_1

private const val BEL = '\u0007'
private const val VT = '\u000B'
private const val FF = '\u000C'
private const val ESC = '\u001B'

/**
 * ASCII ([BASIC_LATIN]) control codes which are acceptable in ASCII text:
 *
 * -  7 (`BEL`, `\a`);
 * -  8 (`BS`, `\b`);
 * -  9 (`HT`, `\t`);
 * - 10 (`LF`, `\n`);
 * - 11 (`VT`, `\v`);
 * - 12 (`FF`, `\f`);
 * - 13 (`CR`, `\r`);
 * - 27 (`ESC`, `\e`).
 */
private val VALID_CONTROL_CODES: String = StringBuilder().apply {
    append(BEL)
    append('\b')
    append('\t')
    append('\n')
    append(VT)
    append(FF)
    append('\r')
    append(ESC)
}.toString()

internal val Char.isAscii: Boolean
    get() =
        this in VALID_CONTROL_CODES || this in ' '..'~'  // 0x20 to 0x7E

@get:Throws(IOException::class)
internal val Path.isAscii: Boolean
    get() =
        inputStream().buffered().use { stream ->
            stream.iterator()
                .asSequence()
                .map(Byte::toInt)
                .map(Int::toChar)
                .all(Char::isAscii)
        }

internal val Byte.isBinary: Boolean
    get() {
        val c = (toInt() and 255).toChar()
        return c < ' ' && c !in VALID_CONTROL_CODES  // Less than 0x20
    }

/**
 * Returns `true` if the receiver is a binary file, `false` otherwise.
 * Note that for UTF-16 encoded files this method will always return `true`,
 * so UTF-16 detection should be invoked _before_ this method is called.
 *
 * @return `true` if the receiver is a binary file, `false` otherwise.
 */
@get:Throws(IOException::class)
internal val Path.isBinary: Boolean
    get() =
        inputStream().buffered().use { stream ->
            stream.iterator()
                .asSequence()
                .any(Byte::isBinary)
        }

@get:Throws(IOException::class)
private val Path.isNotBinary: Boolean
    get() =
        !isBinary

@Throws(IOException::class)
internal fun Path.hasCharset(charset: Charset): Boolean =
    try {
        /*
         * We don't want an empty file to test positive for GB2312 or GB18030,
         * as ASCII or UTF-8 is a better alternative in this case.
         */
        useLines(charset) { lines ->
            lines.count()
        } != 0
    } catch (_: CharacterCodingException) {
        /*
         * May well be either `MalformedInputException` or `UnmappableCharacterException`.
         */
        false
    }

/**
 * Returns the charset of the receiver's content,
 * or `null` if the charset can't be determined
 * or if the receiver is a binary file.
 *
 * @receiver the file for which the charset is to be returned.
 * @return the charset of the receiver's content,
 *   or `null` if the charset can't be determined
 *   or if the receiver is a binary file.
 */
@JvmOverloads
internal fun Path.charsetOrNull(readContent: Boolean = false): Charset? {
    /*
     * UTF-8 detector must run *after* the UTF-16 family, as any UTF-16-encoded
     * BASIC_LATIN and LATIN_1_SUPPLEMENT text is also a valid UTF-8 text.
     */
    val detectors = sequenceOf(
        ::ModeLineDetector,
        ::AsciiDetector,
        ::BigEndianDetector,
        ::LittleEndianDetector,
        ::Utf8Detector,
        ::ChineseDetector,
    )
        .map { ctor ->
            ctor(readContent)
        }

    val charset = detectors.firstNotNullOfOrNull { detector ->
        detector(this)
    }

    return when {
        charset != null -> charset

        /*
         * The 2nd pass, we've read file content but still failed to detect
         * the charset.
         */
        readContent -> null

        /*
         * We didn't detect anything by just looking for the BOM, re-try by
         * reading file content.
         */
        else -> charsetOrNull(readContent = true)
    }
}

/**
 * Returns the charset of the receiver's content,
 * or `null` if the charset can't be determined
 * or if the receiver is a binary file.
 *
 * @receiver the file for which the charset is to be returned.
 * @return the charset of the receiver's content,
 *   or `null` if the charset can't be determined
 *   or if the receiver is a binary file.
 */
val Path.charsetOrNull: Charset?
    get() =
        charsetOrNull()

/**
 * Returns the charset of the receiver's content,
 * or `null` if the receiver is a binary file.
 *
 * @receiver the file for which the charset is to be returned.
 * @return the charset of the receiver's content,
 *   or `null` if the receiver is a binary file.
 * @throws IOException if a read error occurs.
 */
@get:Throws(IOException::class)
val Path.charsetOrDefault: Charset?
    get() =
        charsetOrNull
            ?: when {
                isNotBinary -> ISO_8859_1
                else -> null
            }
