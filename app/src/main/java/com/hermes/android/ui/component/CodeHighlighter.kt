package com.hermes.android.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle

/** What a coloured stretch of code is. */
enum class CodeToken { Keyword, Literal, Str, Comment, Number, Type, Annotation }

/** One coloured stretch: [start] inclusive, [end] exclusive. */
data class CodeSpan(val start: Int, val end: Int, val token: CodeToken)

/**
 * A small syntax colourer for code cards. One left-to-right pass, so a `//` inside
 * a string stays string and a quote inside a comment stays comment. It does not
 * parse anything; it only knows each language's comments, strings and keywords.
 * Unknown languages get the C-like rules with no keywords.
 */
object CodeHighlighter {

    /** Longer code is shown plain: colouring it is not worth the work while it streams. */
    const val MAX_LENGTH = 30_000

    private class Lang(
        val lineComments: List<String>,
        val blockComment: Pair<String, String>? = null,
        val quotes: String = "\"'",
        val backtickStrings: Boolean = false,
        val tripleQuotes: Boolean = false,
        val keywords: Set<String> = emptySet(),
        val literals: Set<String> = setOf("true", "false", "null"),
        val annotations: Char? = null,
        val dollarVars: Boolean = false,
        val capitalTypes: Boolean = true,
        val caseInsensitive: Boolean = false,
    )

    private fun words(s: String) = s.split(' ').filter { it.isNotEmpty() }.toSet()

    private val C_LIKE = Lang(listOf("//"), "/*" to "*/")

    private val KOTLIN = Lang(
        listOf("//"), "/*" to "*/", tripleQuotes = true, annotations = '@',
        keywords = words(
            "package import class interface object fun val var if else when for while do return " +
                "break continue throw try catch finally in is as typealias this super by constructor " +
                "init companion data sealed enum open abstract override private protected public " +
                "internal inline suspend operator infix tailrec external lateinit const vararg " +
                "reified crossinline noinline annotation inner value expect actual where get set",
        ),
    )
    private val JAVA = Lang(
        listOf("//"), "/*" to "*/", annotations = '@',
        keywords = words(
            "package import class interface enum extends implements new return if else for while do " +
                "switch case default break continue throw throws try catch finally this super static " +
                "final abstract public private protected void int long short byte char boolean float " +
                "double var record sealed permits synchronized volatile transient native instanceof yield",
        ),
    )
    private val JS = Lang(
        listOf("//"), "/*" to "*/", backtickStrings = true, annotations = '@',
        literals = words("true false null undefined NaN Infinity"),
        keywords = words(
            "var let const function return if else for while do switch case default break continue " +
                "new delete typeof instanceof in of class extends super this import export from as " +
                "async await yield try catch finally throw static get set void type interface enum " +
                "implements public private protected readonly declare namespace keyof",
        ),
    )
    private val PYTHON = Lang(
        listOf("#"), quotes = "\"'", tripleQuotes = true, annotations = '@',
        literals = words("True False None"),
        keywords = words(
            "def class return if elif else for while in not and or is import from as with try except " +
                "finally raise pass break continue lambda yield global nonlocal assert del async await " +
                "match case self print",
        ),
    )
    private val SHELL = Lang(
        listOf("#"), dollarVars = true, capitalTypes = false, backtickStrings = true,
        literals = emptySet(),
        keywords = words(
            "if then else elif fi for while until do done case esac in function return export local " +
                "readonly unset set source echo exit cd sudo",
        ),
    )
    private val GO = Lang(
        listOf("//"), "/*" to "*/", backtickStrings = true,
        literals = words("true false nil iota"),
        keywords = words(
            "package import func var const type struct interface map chan go defer return if else for " +
                "range switch case default break continue select fallthrough goto",
        ),
    )
    private val RUST = Lang(
        listOf("//"), "/*" to "*/", quotes = "\"",
        literals = words("true false None Some Ok Err"),
        keywords = words(
            "fn let mut const static struct enum impl trait pub use mod crate self Self super where " +
                "match if else for while loop in return break continue as ref move async await dyn " +
                "unsafe extern type",
        ),
    )
    private val C_FAMILY = Lang(
        listOf("//"), "/*" to "*/",
        literals = words("true false NULL nullptr"),
        keywords = words(
            "int long short char float double void unsigned signed const static struct union enum " +
                "typedef return if else for while do switch case default break continue sizeof " +
                "include define ifdef ifndef endif class public private protected virtual namespace " +
                "using template typename new delete auto",
        ),
    )
    private val SWIFT = Lang(
        listOf("//"), "/*" to "*/", tripleQuotes = true, quotes = "\"", annotations = '@',
        literals = words("true false nil"),
        keywords = words(
            "import func let var class struct enum protocol extension return if else guard for while " +
                "in switch case default break continue throw throws try catch self init public private " +
                "internal static override async await",
        ),
    )
    private val SQL = Lang(
        listOf("--"), "/*" to "*/", quotes = "'", capitalTypes = false, caseInsensitive = true,
        literals = words("true false null"),
        keywords = words(
            "select from where and or not insert into values update set delete create table drop alter " +
                "index join left right inner outer on group by order having limit offset as distinct " +
                "union all primary key foreign references default is in like between case when then " +
                "else end",
        ),
    )
    private val YAML = Lang(
        listOf("#"), quotes = "\"'", capitalTypes = false,
        literals = words("true false null yes no on off"),
    )
    private val JSON = Lang(emptyList(), quotes = "\"", capitalTypes = false)

    private fun langFor(name: String): Lang = when (name.trim().lowercase()) {
        "kotlin", "kt", "kts", "gradle" -> KOTLIN
        "java", "scala", "groovy", "dart", "cs", "csharp", "c#" -> JAVA
        "js", "javascript", "jsx", "ts", "typescript", "tsx", "mjs", "cjs" -> JS
        "py", "python", "python3" -> PYTHON
        "sh", "bash", "shell", "zsh", "console", "shellscript", "dockerfile", "ini", "toml", "conf", "env", "makefile" -> SHELL
        "go", "golang" -> GO
        "rs", "rust" -> RUST
        "c", "h", "cpp", "c++", "cc", "hpp", "objc" -> C_FAMILY
        "swift" -> SWIFT
        "sql", "postgres", "postgresql", "mysql", "sqlite" -> SQL
        "yaml", "yml" -> YAML
        "json", "jsonc", "json5" -> JSON
        else -> C_LIKE
    }

    /** The coloured stretches of [code], in order and never overlapping. */
    fun spans(code: String, language: String): List<CodeSpan> {
        if (code.isEmpty() || code.length > MAX_LENGTH) return emptyList()
        val lang = langFor(language)
        val out = ArrayList<CodeSpan>()
        val n = code.length
        var i = 0
        while (i < n) {
            val c = code[i]
            val lineComment = lang.lineComments.firstOrNull { code.startsWith(it, i) }
            // `#` starts a shell/yaml/python comment only at a word boundary (not `a#b`, not `$#`).
            if (lineComment != null && (lineComment != "#" || i == 0 || code[i - 1].isWhitespace())) {
                val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                out += CodeSpan(i, end, CodeToken.Comment)
                i = end
                continue
            }
            val block = lang.blockComment
            if (block != null && code.startsWith(block.first, i)) {
                val close = code.indexOf(block.second, i + block.first.length)
                val end = if (close < 0) n else close + block.second.length
                out += CodeSpan(i, end, CodeToken.Comment)
                i = end
                continue
            }
            if (lang.tripleQuotes && (code.startsWith("\"\"\"", i) || code.startsWith("'''", i))) {
                val fence = code.substring(i, i + 3)
                val close = code.indexOf(fence, i + 3)
                val end = if (close < 0) n else close + 3
                out += CodeSpan(i, end, CodeToken.Str)
                i = end
                continue
            }
            if (c in lang.quotes || (c == '`' && lang.backtickStrings)) {
                val end = stringEnd(code, i, c)
                // A JSON string followed by `:` is a key: colour it like a keyword.
                val token = if (lang === JSON && isKey(code, end)) CodeToken.Keyword else CodeToken.Str
                out += CodeSpan(i, end, token)
                i = end
                continue
            }
            if (lang.dollarVars && c == '$' && i + 1 < n) {
                var j = i + 1
                if (code[j] == '{') {
                    j = code.indexOf('}', j).let { if (it < 0) n else it + 1 }
                } else {
                    while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                    if (j == i + 1 && j < n && code[j] in "#?@*!$0-") j++
                }
                if (j > i + 1) {
                    out += CodeSpan(i, j, CodeToken.Type)
                    i = j
                    continue
                }
            }
            if (lang.annotations == c && i + 1 < n && code[i + 1].isJavaIdentifierStart() &&
                (i == 0 || !code[i - 1].isLetterOrDigit())
            ) {
                var j = i + 1
                while (j < n && (code[j].isJavaIdentifierPart() || code[j] == '.')) j++
                out += CodeSpan(i, j, CodeToken.Annotation)
                i = j
                continue
            }
            if (c.isDigit() && (i == 0 || !isWordChar(code[i - 1]))) {
                var j = i + 1
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) {
                    // Stop at `1..10` and at a method call on a number.
                    if (code[j] == '.' && (j + 1 >= n || !code[j + 1].isDigit())) break
                    j++
                }
                out += CodeSpan(i, j, CodeToken.Number)
                i = j
                continue
            }
            if (isWordStart(c)) {
                var j = i + 1
                while (j < n && isWordChar(code[j])) j++
                val word = code.substring(i, j)
                val key = if (lang.caseInsensitive) word.lowercase() else word
                val token = when {
                    key in lang.literals -> CodeToken.Literal
                    key in lang.keywords -> CodeToken.Keyword
                    lang.capitalTypes && word[0].isUpperCase() && word.any { it.isLowerCase() } -> CodeToken.Type
                    else -> null
                }
                if (token != null) out += CodeSpan(i, j, token)
                i = j
                continue
            }
            i++
        }
        return out
    }

    private fun isWordStart(c: Char) = c.isLetter() || c == '_'
    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_'

    /** Index just past the string opened by [quote] at [start]; ends at the line end if unclosed. */
    private fun stringEnd(code: String, start: Int, quote: Char): Int {
        var j = start + 1
        while (j < code.length) {
            val ch = code[j]
            if (ch == '\\') { j += 2; continue }
            if (ch == quote) return j + 1
            if (ch == '\n' && quote != '`') return j
            j++
        }
        return code.length
    }

    private fun isKey(code: String, from: Int): Boolean {
        var j = from
        while (j < code.length && (code[j] == ' ' || code[j] == '\t')) j++
        return j < code.length && code[j] == ':'
    }
}

/** Colours for [CodeHighlighter] tokens; one set for light cards, one for dark. */
class CodePalette(
    val keyword: Color,
    val literal: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val type: Color,
    val annotation: Color,
) {
    fun style(token: CodeToken): SpanStyle = when (token) {
        CodeToken.Keyword -> SpanStyle(color = keyword)
        CodeToken.Literal -> SpanStyle(color = literal)
        CodeToken.Str -> SpanStyle(color = string)
        CodeToken.Comment -> SpanStyle(color = comment, fontStyle = FontStyle.Italic)
        CodeToken.Number -> SpanStyle(color = number)
        CodeToken.Type -> SpanStyle(color = type)
        CodeToken.Annotation -> SpanStyle(color = annotation)
    }

    companion object {
        // GitHub's light and dark code colours.
        val Light = CodePalette(
            keyword = Color(0xFFCF222E),
            literal = Color(0xFF0550AE),
            string = Color(0xFF0A3069),
            comment = Color(0xFF6E7781),
            number = Color(0xFF0550AE),
            type = Color(0xFF953800),
            annotation = Color(0xFF8250DF),
        )
        val Dark = CodePalette(
            keyword = Color(0xFFFF7B72),
            literal = Color(0xFF79C0FF),
            string = Color(0xFFA5D6FF),
            comment = Color(0xFF8B949E),
            number = Color(0xFF79C0FF),
            type = Color(0xFFFFA657),
            annotation = Color(0xFFD2A8FF),
        )

        fun forBackground(background: Color): CodePalette =
            if (background.luminance() < 0.5f) Dark else Light
    }
}

/** [code] with its tokens coloured for a card painted [background]. */
@Composable
fun rememberHighlightedCode(code: String, language: String, background: Color): AnnotatedString {
    val palette = CodePalette.forBackground(background)
    return remember(code, language, palette) {
        val spans = CodeHighlighter.spans(code, language)
        if (spans.isEmpty()) {
            AnnotatedString(code)
        } else {
            buildAnnotatedString {
                append(code)
                spans.forEach { addStyle(palette.style(it.token), it.start, it.end) }
            }
        }
    }
}
