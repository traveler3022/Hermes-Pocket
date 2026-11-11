package com.hermes.android.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import com.hermes.android.ui.component.syntax.Prism
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** What a coloured stretch of code is. [Inserted] and [Deleted] are the lines of a diff. */
enum class CodeToken {
    Keyword, Literal, Str, Comment, Number, Type, Annotation, Function, Tag, Attr, Variable, Inserted, Deleted,
}

/** One coloured stretch: [start] inclusive, [end] exclusive. */
data class CodeSpan(val start: Int, val end: Int, val token: CodeToken)

/**
 * The syntax colourer for code cards. Languages Prism has a grammar for (see
 * [prismId]) are tokenized by [Prism]; `diff`/`patch` colour their `+`/`-` lines;
 * everything else, or a Prism failure, falls back to [ruleSpans].
 */
object CodeHighlighter {

    /** Longer code is shown plain: colouring it is not worth the work while it streams. */
    const val MAX_LENGTH = 30_000

    /** The coloured stretches of [code], in order and never overlapping. Blocks on Prism's thread. */
    fun spans(code: String, language: String): List<CodeSpan> {
        if (code.isEmpty() || code.length > MAX_LENGTH) return emptyList()
        if (isDiff(language)) return diffSpans(code)
        return prismSpans(code, language) ?: ruleSpans(code, language)
    }

    /**
     * True when [language] is coloured by [Prism]. Reads [PRISM_IDS], not Prism's table,
     * so asking on the main thread never parses the grammars there.
     */
    fun usesPrism(language: String): Boolean = !isDiff(language) && prismName(language) in PRISM_IDS

    /** The grammar ids in resources/prism/prism-grammars.txt (a test checks they match). */
    internal val PRISM_IDS = setOf(
        "javascript", "jsx", "typescript", "tsx", "json", "python", "ruby", "go", "rust", "java", "kotlin",
        "swift", "objectivec", "c", "cpp", "csharp", "php", "bash", "shell-session", "powershell", "sql",
        "css", "scss", "less", "markup", "markdown", "yaml", "toml", "ini", "docker", "makefile", "cmake",
        "graphql", "protobuf", "hcl", "dart", "lua", "elixir", "scala", "haskell", "perl", "r", "zig", "nim",
        "erlang",
    )

    private fun prismName(language: String): String {
        val name = language.trim().lowercase()
        return PRISM_ALIASES[name] ?: name
    }

    private fun isDiff(language: String) = language.trim().lowercase() in DIFF_NAMES

    private val DIFF_NAMES = setOf("diff", "patch", "udiff")

    /** Fence names that are not Prism's own ids. A Prism id itself is accepted as is. */
    private val PRISM_ALIASES = mapOf(
        "kt" to "kotlin", "kts" to "kotlin", "gradle" to "kotlin",
        "js" to "javascript", "mjs" to "javascript", "cjs" to "javascript", "node" to "javascript",
        "ts" to "typescript", "mts" to "typescript", "cts" to "typescript",
        "jsonc" to "json", "json5" to "json",
        "py" to "python", "python3" to "python", "py3" to "python", "pyi" to "python",
        "rb" to "ruby", "golang" to "go", "rs" to "rust",
        "cs" to "csharp", "c#" to "csharp", "dotnet" to "csharp",
        "h" to "c", "c++" to "cpp", "cc" to "cpp", "cxx" to "cpp", "hpp" to "cpp",
        "objc" to "objectivec", "objective-c" to "objectivec", "mm" to "objectivec",
        "sh" to "bash", "shell" to "bash", "zsh" to "bash", "shellscript" to "bash", "env" to "bash",
        "console" to "shell-session", "sh-session" to "shell-session", "shellsession" to "shell-session",
        "ps1" to "powershell", "pwsh" to "powershell",
        "postgres" to "sql", "postgresql" to "sql", "mysql" to "sql", "sqlite" to "sql", "plsql" to "sql",
        "sass" to "scss",
        "html" to "markup", "htm" to "markup", "xml" to "markup", "svg" to "markup", "vue" to "markup",
        "xhtml" to "markup", "plist" to "markup",
        "md" to "markdown", "mdx" to "markdown", "yml" to "yaml",
        "cfg" to "ini", "conf" to "ini", "properties" to "ini",
        "dockerfile" to "docker", "make" to "makefile", "mk" to "makefile",
        "gql" to "graphql", "proto" to "protobuf", "tf" to "hcl", "terraform" to "hcl",
        "ex" to "elixir", "exs" to "elixir", "erl" to "erlang", "hs" to "haskell", "pl" to "perl",
    )

    /** The Prism grammar id for a fence's language name, or null when Prism has none. */
    internal fun prismId(language: String): String? {
        val id = prismName(language)
        if (id !in PRISM_IDS) return null
        return id.takeIf { runCatching { Prism.hasGrammar(it) }.getOrDefault(false) }
    }

    /** Prism's colouring of [code], or null when Prism has no grammar or fails on it. */
    internal fun prismSpans(code: String, language: String): List<CodeSpan>? {
        val id = prismId(language) ?: return null
        // Throwable: a StackOverflowError on a pathological block must only fall back.
        val nodes = try {
            Prism.tokenize(code, id)
        } catch (t: Throwable) {
            null
        } ?: return null
        val out = ArrayList<CodeSpan>()
        fun add(start: Int, end: Int, token: CodeToken?) {
            if (token == null || end <= start) return
            val last = out.lastOrNull()
            if (last != null && last.token == token && last.end == start) {
                out[out.lastIndex] = CodeSpan(last.start, end, token)
            } else {
                out += CodeSpan(start, end, token)
            }
        }
        fun walk(list: List<Prism.Node>, inherited: CodeToken?) {
            for (node in list) when (node) {
                is Prism.Node.Text -> add(node.location, node.location + node.length, inherited)
                // A token with no colour of its own (an interpolation) keeps its parent's.
                is Prism.Node.Token -> walk(node.content, prismToken(node.name) ?: inherited)
            }
        }
        walk(nodes, null)
        return out
    }

    /** Prism's token names collapsed to this palette (happy's PrismHighlighter.classify, regrouped). */
    internal fun prismToken(name: String): CodeToken? = when (name) {
        "keyword", "important", "atrule", "rule", "directive", "entity" -> CodeToken.Keyword
        "boolean", "null", "nil", "constant" -> CodeToken.Literal
        "string", "char", "regex", "template-string", "attr-value", "url" -> CodeToken.Str
        "comment", "prolog", "doctype", "cdata", "docstring" -> CodeToken.Comment
        "number", "hexcode", "unit" -> CodeToken.Number
        "function", "method", "function-variable", "macro" -> CodeToken.Function
        "class-name", "builtin", "symbol", "namespace", "selector" -> CodeToken.Type
        "annotation", "decorator" -> CodeToken.Annotation
        "tag", "script" -> CodeToken.Tag
        "attr-name", "property", "parameter" -> CodeToken.Attr
        "variable" -> CodeToken.Variable
        "inserted" -> CodeToken.Inserted
        "deleted" -> CodeToken.Deleted
        else -> null
    }

    /**
     * A unified diff, line by line: `+` added, `-` removed, `@@` hunk headers, and the
     * `diff`/`index`/`---`/`+++` file headers. Context lines stay plain.
     */
    internal fun diffSpans(code: String): List<CodeSpan> {
        val out = ArrayList<CodeSpan>()
        var start = 0
        while (start <= code.length) {
            val newline = code.indexOf('\n', start)
            val end = if (newline < 0) code.length else newline
            val line = code.substring(start, end)
            val token = when {
                line.startsWith("+++ ") || line.startsWith("--- ") || line == "+++" || line == "---" -> CodeToken.Keyword
                line.startsWith("diff ") || line.startsWith("index ") -> CodeToken.Keyword
                line.startsWith("@@") -> CodeToken.Annotation
                line.startsWith("+") -> CodeToken.Inserted
                line.startsWith("-") -> CodeToken.Deleted
                line.startsWith("\\") -> CodeToken.Comment
                else -> null
            }
            if (token != null && end > start) out += CodeSpan(start, end, token)
            if (newline < 0) break
            start = newline + 1
        }
        return out
    }

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

    /**
     * The hand-written fallback: one left-to-right pass, so a `//` inside a string stays
     * string and a quote inside a comment stays comment. It only knows each language's
     * comments, strings and keywords; unknown languages get the C-like rules. Cheap, so
     * it also colours a card at once while Prism works.
     */
    internal fun ruleSpans(code: String, language: String): List<CodeSpan> {
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
    val function: Color,
    val tag: Color,
    val attr: Color,
    val variable: Color,
    val inserted: Color,
    val deleted: Color,
) {
    fun style(token: CodeToken): SpanStyle = when (token) {
        CodeToken.Keyword -> SpanStyle(color = keyword)
        CodeToken.Literal -> SpanStyle(color = literal)
        CodeToken.Str -> SpanStyle(color = string)
        CodeToken.Comment -> SpanStyle(color = comment, fontStyle = FontStyle.Italic)
        CodeToken.Number -> SpanStyle(color = number)
        CodeToken.Type -> SpanStyle(color = type)
        CodeToken.Annotation -> SpanStyle(color = annotation)
        CodeToken.Function -> SpanStyle(color = function)
        CodeToken.Tag -> SpanStyle(color = tag)
        CodeToken.Attr -> SpanStyle(color = attr)
        CodeToken.Variable -> SpanStyle(color = variable)
        CodeToken.Inserted -> SpanStyle(color = inserted)
        CodeToken.Deleted -> SpanStyle(color = deleted)
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
            function = Color(0xFF8250DF),
            tag = Color(0xFF116329),
            attr = Color(0xFF0550AE),
            variable = Color(0xFF953800),
            inserted = Color(0xFF116329),
            deleted = Color(0xFF82071E),
        )
        val Dark = CodePalette(
            keyword = Color(0xFFFF7B72),
            literal = Color(0xFF79C0FF),
            string = Color(0xFFA5D6FF),
            comment = Color(0xFF8B949E),
            number = Color(0xFF79C0FF),
            type = Color(0xFFFFA657),
            annotation = Color(0xFFD2A8FF),
            function = Color(0xFFD2A8FF),
            tag = Color(0xFF7EE787),
            attr = Color(0xFF79C0FF),
            variable = Color(0xFFFFA657),
            inserted = Color(0xFF7EE787),
            deleted = Color(0xFFFFA198),
        )

        fun forBackground(background: Color): CodePalette =
            if (background.luminance() < 0.5f) Dark else Light
    }
}

private fun styled(code: String, spans: List<CodeSpan>, palette: CodePalette): AnnotatedString =
    if (spans.isEmpty()) {
        AnnotatedString(code)
    } else {
        buildAnnotatedString {
            append(code)
            spans.forEach { addStyle(palette.style(it.token), it.start, it.end) }
        }
    }

/** A Prism result and the exact input it was made for. */
private class PrismResult(val code: String, val language: String, val palette: CodePalette, val text: AnnotatedString)

/**
 * [code] with its tokens coloured for a card painted [background].
 *
 * Prism runs off the main thread: until its result for exactly this text arrives, the
 * card shows the cheap rule-based colouring. While a reply streams, each new text
 * cancels the wait for the previous one, so at most one stale tokenize is in flight.
 */
@Composable
fun rememberHighlightedCode(code: String, language: String, background: Color): AnnotatedString {
    val palette = CodePalette.forBackground(background)
    val usesPrism = remember(language) { CodeHighlighter.usesPrism(language) }
    var prism by remember { mutableStateOf<PrismResult?>(null) }
    if (usesPrism) {
        LaunchedEffect(code, language, palette) {
            if (code.isEmpty() || code.length > CodeHighlighter.MAX_LENGTH) return@LaunchedEffect
            // Throwable: even starting Prism's big-stack thread can fail on a starved device;
            // the card then keeps the rule colouring. Cancellation still propagates.
            val text = try {
                withContext(Prism.dispatcher) {
                    styled(code, CodeHighlighter.spans(code, language), palette)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                return@LaunchedEffect
            }
            prism = PrismResult(code, language, palette, text)
        }
    }
    val quick = remember(code, language, palette) {
        val spans = if (usesPrism) CodeHighlighter.ruleSpans(code, language) else CodeHighlighter.spans(code, language)
        styled(code, spans, palette)
    }
    val ready = prism?.takeIf { usesPrism && it.code == code && it.language == language && it.palette === palette }
    return ready?.text ?: quick
}
