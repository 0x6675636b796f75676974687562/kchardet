package com.saveourtool.chardet

import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory

import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.useLines
import kotlin.text.Charsets.ISO_8859_1

/**
 * [Mode Line](https://www.gnu.org/software/emacs/manual/html_node/emacs/Specifying-File-Variables.html)
 * based detector.
 */
class ModeLineDetector(private val readContent: Boolean) : CharsetDetector {
    override fun invoke(input: Path): Charset? {
        return try {
            when {
                readContent -> {
                    val charsetHints = charsetsOf(input)

                    if (charsetHints.size > 1) {
                        LOGGER.warn("$input: specifies more than one charset: $charsetHints")
                    }

                    val detectedCharset = charsetHints.firstOrNull(input::hasCharset)
                    if (charsetHints.isNotEmpty() && detectedCharset == null) {
                        LOGGER.warn("$input: none of the specified charsets is applicable: $charsetHints")
                    }

                    detectedCharset
                }

                else -> null
            }
        } catch (ioe: IOException) {
            LOGGER.warn("When reading file: $input", ioe)
            null
        }
    }

    /**
     * @return an empty list.
     */
    override val supportedCharsets: List<Charset>
        get() =
            emptyList()

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ModeLineDetector::class.java)

        @Language("RegExp")
        private const val COMMENT_TEXT = "commentText"

        @Language("RegExp")
        private const val MODE_LINE_CONTENT = "modeLineContent"

        @Language("RegExp")
        private const val KEY = "key"

        @Language("RegExp")
        private const val VALUE = "value"

        private const val MODE_LINE_BEGIN = "-*-"

        private const val MODE_LINE_END = MODE_LINE_BEGIN

        /**
         * Extensions (case-sensitive) mapped to strings which start a line comment.
         *
         * If the comment starter is a single character, it's allowed to repeat
         * in files (e.g. `# Comment` and `## Comment` will both be treated as
         * a comment).
         */
        private val LANGUAGES: Map<String, Regex> = languagesOf(
            "py" to "#",
            "pl" to "#",
            "PL" to "#",
            "rb" to "#",
            "el" to ";",
            "vim" to "\"",
        )

        private val MODE_LINE = Regex("""^\h*${MODE_LINE_BEGIN.quoted()}\h*(?<$MODE_LINE_CONTENT>.+?)\h*${MODE_LINE_END.quoted()}\h*$""")

        private val MODE_LINE_ENTRY = Regex("""^\h*(?<$KEY>[^:;\s]+)\h*:\h*(?<$VALUE>[^:;\s]*)\h*$""")

        private val SUPPORTED_KEYS: Array<out String> = arrayOf("coding", "encoding")

        private fun charsetsOf(input: Path): List<Charset> {
            val commentRegex = LANGUAGES[input.extension] ?: return emptyList()

            /*
             * ISO-8859-1 is the only charset that won't result
             * in character coding errors, and it's fully sufficient for
             * the task of parsing a mode line.
             */
            return input.useLines(ISO_8859_1) { lines ->
                lines.takeWhile { line ->
                    line.isBlank() || line.matches(commentRegex)
                }
                    .filter(String::isNotBlank)
                    .map { comment ->
                        commentRegex.matchEntire(comment)
                    }
                    .filterNotNull()
                    .map { result ->
                        result.groups[COMMENT_TEXT]?.value
                    }
                    .filterNotNull()
                    .flatMap { commentLine ->
                        parseModeLine(commentLine)
                    }
                    .filter { (key, _) ->
                        key in SUPPORTED_KEYS
                    }
                    .map(Pair<String, String>::second)
                    .filter(Charset::isSupported)
                    .map(Charset::forName)
                    .distinct()
                    .toList()
            }
        }

        /**
         * @param pairs file extensions mapped to line comment starters.
         */
        private fun languagesOf(vararg pairs: Pair<String, String>): Map<String, Regex> =
            pairs.asSequence()
                .map { (extension, commentPrefix) ->
                    check(commentPrefix.isNotEmpty()) {
                        "Comment prefix for .$extension extension is empty"
                    }

                    @Language("RegExp")
                    val commentRegex = when (commentPrefix.length) {
                        /*
                         * Repeating.
                         */
                        1 -> """^\h*(?:${commentPrefix.quoted()})++(?<$COMMENT_TEXT>.*?)$"""

                        /*
                         * Non-repeating.
                         */
                        else -> """^\h*${commentPrefix.quoted()}(?<$COMMENT_TEXT>.*?)$"""
                    }

                    extension to Regex(commentRegex)
                }
                .toMap()

        /**
         * @param commentLine the comment line w/o the leading comment starter
         *   (`#`, `;`, or `"`).
         * @return the sequence of key-value pairs if [commentLine] matches the
         *   _Mode Line_ format (`-*- var1: value1; var2: value2; ... -*-`), or
         *   an empty sequence otherwise.
         */
        private fun parseModeLine(commentLine: String): Sequence<Pair<String, String>> {
            val result = MODE_LINE.matchEntire(commentLine)
                ?: return emptySequence()
            val modeLineContent = result.groups[MODE_LINE_CONTENT]?.value
                ?: return emptySequence()
            return parseModeLineContent(modeLineContent)
        }

        private fun parseModeLineContent(modeLineContent: String): Sequence<Pair<String, String>> =
            modeLineContent.splitToSequence(';')
                .map { entry ->
                    parseModeLineEntry(entry)
                }
                .filterNotNull()

        private fun parseModeLineEntry(entry: String): Pair<String, String>? {
            val result = MODE_LINE_ENTRY.matchEntire(entry)
                ?: return null
            val key = result.groups[KEY]?.value
                ?: return null
            val value = result.groups[VALUE]?.value
                ?: return null
            return key to value
        }

        @Language("RegExp")
        private fun String.quoted(): String =
            """\Q$this\E"""
    }
}
