package com.example.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class CodeSyntaxHighlighter(private val extension: String) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = highlightCode(text.text, extension)
        return TransformedText(highlighted, OffsetMapping.Identity)
    }

    companion object {
        // Highlighting theme colors
        private val ColorKeyword = Color(0xFFF06292) // Soft pink or neon purple
        private val ColorString = Color(0xFF81C784)  // Pale emerald green
        private val ColorNumber = Color(0xFFFFB74D)  // Vibrant orange/amber
        private val ColorComment = Color(0xFF90A4AE) // Slate grayish blue-green
        private val ColorSpecials = Color(0xFF4DD0E1) // Refreshing cyan/teal for web tags
        private val ColorAnnotations = Color(0xFFBA68C8) // Lavender purple

        fun highlightCode(code: String, extension: String): AnnotatedString {
            val builder = AnnotatedString.Builder(code)
            
            val ext = extension.lowercase()
            if (ext == "kt" || ext == "kts" || ext == "java" || ext == "js" || ext == "ts" || ext == "py" || ext == "c" || ext == "cpp") {
                highlightCodeSyntax(builder, code, ext)
            } else if (ext == "html" || ext == "xml") {
                highlightHtmlSyntax(builder, code)
            } else if (ext == "json") {
                highlightJsonSyntax(builder, code)
            } else if (ext == "md" || ext == "markdown") {
                highlightMarkdownSyntax(builder, code)
            }
            
            return builder.toAnnotatedString()
        }

        private fun highlightCodeSyntax(builder: AnnotatedString.Builder, code: String, ext: String) {
            // General keywords depending on target code language
            val keywords = when(ext) {
                "py" -> setOf(
                    "def", "class", "import", "from", "as", "return", "if", "elif", "else", 
                    "while", "for", "in", "and", "or", "not", "is", "None", "True", "False", "try", "except", "pass", "with"
                )
                else -> setOf(
                    "class", "fun", "val", "var", "import", "package", "return", "if", "else", "while", "for", "in", "when",
                    "interface", "object", "private", "public", "protected", "override", "internal", "null", "true", "false",
                    "this", "super", "throw", "try", "catch", "finally", "static", "void", "new", "const", "let", "function", "break", "continue"
                )
            }

            // Highlighting single-line comments // (or # for python)
            val commentRegex = if (ext == "py") Regex("#.*") else Regex("//.*|/\\*[\\s\\S]*?\\*/")
            commentRegex.findAll(code).forEach { match ->
                builder.addStyle(SpanStyle(color = ColorComment, fontWeight = FontWeight.Normal), match.range.first, match.range.last + 1)
            }

            // Highlighting strings "..."
            val stringRegex = Regex("\".*?\"|'.*?'")
            stringRegex.findAll(code).forEach { match ->
                builder.addStyle(SpanStyle(color = ColorString), match.range.first, match.range.last + 1)
            }

            // Highlighting keywords
            val wordRegex = Regex("\\b[a-zA-Z_][a-zA-Z0-9_]*\\b")
            wordRegex.findAll(code).forEach { match ->
                if (keywords.contains(match.value)) {
                    builder.addStyle(
                        SpanStyle(color = ColorKeyword, fontWeight = FontWeight.Bold),
                        match.range.first,
                        match.range.last + 1
                    )
                }
            }

            // Highlighting annotation parameters e.g. @Composable
            val annotationRegex = Regex("@[a-zA-Z_][a-zA-Z0-9_]*")
            annotationRegex.findAll(code).forEach { match ->
                builder.addStyle(SpanStyle(color = ColorAnnotations, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
            }

            // Highlighting numeric values
            val numberRegex = Regex("\\b\\d+(\\.\\d+)?\\b")
            numberRegex.findAll(code).forEach { match ->
                builder.addStyle(SpanStyle(color = ColorNumber), match.range.first, match.range.last + 1)
            }
        }

        private fun highlightHtmlSyntax(builder: AnnotatedString.Builder, code: String) {
            // Tags inside < ... >
            val htmlTagRegex = Regex("<[^>]*>")
            htmlTagRegex.findAll(code).forEach { match ->
                val start = match.range.first
                val end = match.range.last + 1
                builder.addStyle(SpanStyle(color = ColorSpecials, fontWeight = FontWeight.Bold), start, end)

                // Highlight quotes separately inside the tags
                val attrValRegex = Regex("\".*?\"|'.*?'")
                attrValRegex.findAll(match.value).forEach { valMatch ->
                    builder.addStyle(
                        SpanStyle(color = ColorString),
                        start + valMatch.range.first,
                        start + valMatch.range.last + 1
                    )
                }
            }

            // Comments <!-- ... -->
            val htmlCommentRegex = Regex("<!--[\\s\\S]*?-->")
            htmlCommentRegex.findAll(code).forEach { match ->
                builder.addStyle(SpanStyle(color = ColorComment), match.range.first, match.range.last + 1)
            }
        }

        private fun highlightJsonSyntax(builder: AnnotatedString.Builder, code: String) {
            // Key properties in quotes
            val keyColor = Color(0xFF64B5F6) // Bright blue
            val keyRegex = Regex("\".*?\"\\s*:")
            keyRegex.findAll(code).forEach { match ->
                builder.addStyle(SpanStyle(color = keyColor, fontWeight = FontWeight.SemiBold), match.range.first, match.range.last + 1)
            }

            // String values "value" (excluding key strings)
            val valRegex = Regex(":\\s*\"(.*?)\"")
            valRegex.findAll(code).forEach { match ->
                val endOffset = match.range.last + 1
                val startOffset = match.range.first + match.value.indexOf('"')
                builder.addStyle(SpanStyle(color = ColorString), startOffset, endOffset)
            }

            // Number/Boolean values in values
            val literalRegex = Regex(":\\s*\\b(true|false|null|\\d+(\\.\\d+)?)\\b")
            literalRegex.findAll(code).forEach { match ->
                val valuePart = match.value.substringAfter(":").trim()
                val valueStartInSource = match.range.first + match.value.indexOf(valuePart)
                val valueEndInSource = valueStartInSource + valuePart.length
                
                val styleColor = if (valuePart == "true" || valuePart == "false" || valuePart == "null") ColorSpecials else ColorNumber
                builder.addStyle(SpanStyle(color = styleColor, fontWeight = FontWeight.Bold), valueStartInSource, valueEndInSource)
            }
        }

        private fun highlightMarkdownSyntax(builder: AnnotatedString.Builder, code: String) {
            // Headers: lines Starting with #
            val headerRegex = Regex("(?m)^#+.*$")
            headerRegex.findAll(code).forEach { match ->
                builder.addStyle(SpanStyle(color = ColorKeyword, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
            }

            // Links: [text](url)
            val linkRegex = Regex("\\[.*?\\]\\(.*?\\)")
            linkRegex.findAll(code).forEach { match ->
                val textPartEnd = match.value.indexOf(']')
                val start = match.range.first
                builder.addStyle(SpanStyle(color = ColorSpecials, fontWeight = FontWeight.Bold), start + 1, start + textPartEnd)
                builder.addStyle(SpanStyle(color = ColorString), start + textPartEnd + 2, start + match.value.length - 1)
            }

            // Code blocks: ``` ... ```
            val codeBlockRegex = Regex("`{3}[\\s\\S]*?`{3}|`[^`\\n]+`")
            codeBlockRegex.findAll(code).forEach { match ->
                builder.addStyle(SpanStyle(color = ColorNumber, fontFamily = FontFamily.Monospace), match.range.first, match.range.last + 1)
            }

            // Lists starting with * or - or 1.
            val listRegex = Regex("(?m)^\\s*([-*]|\\d+\\.)\\s")
            listRegex.findAll(code).forEach { match ->
                builder.addStyle(SpanStyle(color = ColorAnnotations, fontWeight = FontWeight.Bold), match.range.first, match.range.last)
            }
        }
    }
}
