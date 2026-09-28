package com.hermes.android.ui.component

import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.parser.Parser
import org.commonmark.parser.SourceLine
import org.commonmark.parser.beta.InlineContentParser
import org.commonmark.parser.beta.InlineContentParserFactory
import org.commonmark.parser.beta.InlineParserState
import org.commonmark.parser.beta.ParsedInline
import org.commonmark.parser.beta.Scanner
import org.commonmark.parser.block.AbstractBlockParser
import org.commonmark.parser.block.AbstractBlockParserFactory
import org.commonmark.parser.block.BlockContinue
import org.commonmark.parser.block.BlockStart
import org.commonmark.parser.block.MatchedBlockParser
import org.commonmark.parser.block.ParserState

/**
 * LaTeX math in markdown, with the rules Telegram's markdown messages use
 * (`MarkdownParser.java`: Markwon's `JLatexMathPlugin` with inlines on, plus its
 * `SingleDollarLatexInlineProcessor`):
 *
 * - `$$ … $$` opening a line is a formula block, on one line or across several.
 * - `$$ … $$` inside a sentence and `$ … $` are inline formulas. A single-dollar
 *   formula must not start or end with a space and must not be followed by a
 *   digit, so prices (`$5 and $10`) and shell variables (`$HOME and $PATH`) stay
 *   text.
 */
internal class MathInline(val latex: String) : CustomNode()

internal class MathBlock : CustomBlock() {
    var latex: String = ""

    /** False while a streaming reply has opened the block but not closed it yet. */
    var closed: Boolean = false
}

internal object MathExtension : Parser.ParserExtension {
    override fun extend(parserBuilder: Parser.Builder) {
        parserBuilder.customBlockParserFactory(MathBlockParser.Factory)
        parserBuilder.customInlineContentParserFactory(MathInlineParser.Factory)
    }
}

private class MathInlineParser : InlineContentParser {
    override fun tryParse(inlineParserState: InlineParserState): ParsedInline {
        val scanner = inlineParserState.scanner()
        scanner.next() // the '$' that brought us here
        val display = scanner.next('$')
        val latex = StringBuilder()
        while (true) {
            val c = scanner.peek()
            if (c == Scanner.END) return ParsedInline.none()
            if (c == '$') break
            latex.append(c)
            scanner.next()
        }
        scanner.next() // closing '$'
        if (display) {
            if (!scanner.next('$') || latex.isBlank()) return ParsedInline.none()
        } else {
            if (latex.isEmpty() || latex.first().isWhitespace() || latex.last().isWhitespace()) {
                return ParsedInline.none()
            }
            if (scanner.peek().isDigit()) return ParsedInline.none()
        }
        return ParsedInline.of(MathInline(latex.toString().trim()), scanner.position())
    }

    object Factory : InlineContentParserFactory {
        override fun getTriggerCharacters(): Set<Char> = setOf('$')
        override fun create(): InlineContentParser = MathInlineParser()
    }
}

private class MathBlockParser(firstLine: CharSequence) : AbstractBlockParser() {
    private val block = MathBlock()
    private val lines = StringBuilder()

    init {
        // "$$" then either the whole formula and "$$" on this line, or the start of it.
        val rest = firstLine.toString().trim().removePrefix("$$")
        if (rest.trimEnd().endsWith("$$")) {
            lines.append(rest.trimEnd().removeSuffix("$$"))
            block.closed = true
        } else {
            lines.append(rest)
        }
    }

    override fun getBlock() = block

    override fun tryContinue(state: ParserState): BlockContinue {
        if (block.closed) return BlockContinue.none()
        val line = state.line.content
        val trimmed = line.toString().trim()
        if (trimmed.endsWith("$$")) {
            if (lines.isNotEmpty()) lines.append('\n')
            lines.append(trimmed.removeSuffix("$$"))
            block.closed = true
            // Like a closing code fence: the line is used up and the block ends.
            return BlockContinue.finished()
        }
        return BlockContinue.atIndex(state.index)
    }

    override fun addLine(line: SourceLine) {
        if (lines.isNotEmpty()) lines.append('\n')
        lines.append(line.content)
    }

    override fun closeBlock() {
        block.latex = lines.toString().trim()
    }

    object Factory : AbstractBlockParserFactory() {
        override fun tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): BlockStart {
            if (state.indent >= 4) return BlockStart.none()
            val line = state.line.content
            val start = state.nextNonSpaceIndex
            if (!line.subSequence(start, line.length).startsWith("$$")) return BlockStart.none()
            // "$$x$$ is the answer" is an inline formula opening a sentence, not a block.
            val rest = line.subSequence(start + 2, line.length).toString().trimEnd()
            val close = rest.indexOf("$$")
            if (close >= 0 && close + 2 < rest.length) return BlockStart.none()
            return BlockStart.of(MathBlockParser(line.subSequence(start, line.length))).atIndex(line.length)
        }
    }
}
