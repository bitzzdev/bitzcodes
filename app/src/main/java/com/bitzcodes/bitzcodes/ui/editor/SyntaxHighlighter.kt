package com.bitzcodes.bitzcodes.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

object SyntaxHighlighter {

    private val KeywordColor = Color(0xFF7F5AF0)    // Indigo/purple
    private val StringColor = Color(0xFF2CB67D)     // Vibrant green
    private val CommentColor = Color(0xFF72757E)    // Muted grey
    private val AnnotationColor = Color(0xFFFF8E3C) // Warm orange
    private val NumberColor = Color(0xFF3DA9FC)     // Sky blue

    fun highlight(code: String, fileName: String): AnnotatedString {
        val builder = AnnotatedString.Builder(code)
        val ext = fileName.substringAfterLast('.', "").lowercase()

        // 1. Identify Keywords list based on file extension
        val keywords = when (ext) {
            "kt", "kts" -> listOf(
                "package", "import", "class", "interface", "object", "fun", "val", "var",
                "return", "if", "else", "when", "for", "while", "do", "try", "catch", "finally",
                "throw", "null", "true", "false", "this", "super", "private", "protected",
                "public", "internal", "companion", "init", "constructor", "open", "override",
                "abstract", "final", "data", "sealed", "lateinit", "inline", "suspend", "as", "is"
            )
            "java" -> listOf(
                "package", "import", "class", "interface", "enum", "public", "protected", "private",
                "static", "final", "void", "int", "double", "float", "long", "boolean", "char",
                "byte", "short", "if", "else", "switch", "case", "default", "for", "while", "do",
                "break", "continue", "return", "try", "catch", "finally", "throw", "throws", "new",
                "null", "true", "false", "this", "super", "extends", "implements", "instanceof"
            )
            "js", "ts", "json" -> listOf(
                "const", "let", "var", "function", "return", "if", "else", "for", "while", "do",
                "switch", "case", "default", "break", "continue", "class", "export", "import", "from",
                "new", "this", "super", "null", "true", "false", "undefined", "try", "catch", "throw"
            )
            else -> listOf("val", "var", "fun", "class", "return", "if", "else")
        }

        // Apply keywords
        if (keywords.isNotEmpty()) {
            val keywordPattern = Regex("\\b(${keywords.joinToString("|")})\\b")
            keywordPattern.findAll(code).forEach { match ->
                builder.addStyle(
                    SpanStyle(color = KeywordColor, fontWeight = FontWeight.Bold),
                    match.range.first,
                    match.range.last + 1
                )
            }
        }

        // Apply comments (single line // and multi-line /* */)
        val commentPattern = Regex("//.*|/\\*[\\s\\S]*?\\*/")
        commentPattern.findAll(code).forEach { match ->
            builder.addStyle(
                SpanStyle(color = CommentColor, fontStyle = FontStyle.Italic),
                match.range.first,
                match.range.last + 1
            )
        }

        // Apply strings ("..." and '...')
        val stringPattern = Regex("\".*?\"|'.*?'")
        stringPattern.findAll(code).forEach { match ->
            builder.addStyle(
                SpanStyle(color = StringColor),
                match.range.first,
                match.range.last + 1
            )
        }

        // Apply numbers
        val numberPattern = Regex("\\b\\d+(\\.\\d+)?\\b")
        numberPattern.findAll(code).forEach { match ->
            builder.addStyle(
                SpanStyle(color = NumberColor),
                match.range.first,
                match.range.last + 1
            )
        }

        // Apply annotations (like @Composable, @Override)
        val annotationPattern = Regex("@[a-zA-Z]+")
        annotationPattern.findAll(code).forEach { match ->
            builder.addStyle(
                SpanStyle(color = AnnotationColor, fontWeight = FontWeight.SemiBold),
                match.range.first,
                match.range.last + 1
            )
        }

        // XML/HTML specific styling
        if (ext == "xml" || ext == "html") {
            // Tags: <tag> and </tag>
            val tagPattern = Regex("<[^>]+>")
            tagPattern.findAll(code).forEach { match ->
                builder.addStyle(
                    SpanStyle(color = KeywordColor),
                    match.range.first,
                    match.range.last + 1
                )
            }
            // XML attribute values
            val xmlAttrPattern = Regex("\"[^\"]*\"")
            xmlAttrPattern.findAll(code).forEach { match ->
                builder.addStyle(
                    SpanStyle(color = StringColor),
                    match.range.first,
                    match.range.last + 1
                )
            }
        }

        return builder.toAnnotatedString()
    }
}
