package com.hermes.android.domain.syntax

import kotlinx.coroutines.CoroutineDispatcher

/** Splits source code into syntax tokens for the code cards' colouring. */
interface CodeTokenizer {
    /** A tokenize result: plain text, or a named token over its content. Ranges are into the tokenized text. */
    sealed interface Node {
        data class Text(val location: Int, val length: Int) : Node
        data class Token(val name: String, val content: List<Node>) : Node
    }

    /** Where tokenizing runs. [tokenize] called on it runs inline; from anywhere else it blocks until done. */
    val dispatcher: CoroutineDispatcher

    /** Whether [language] (a grammar id) has a grammar. */
    fun hasGrammar(language: String?): Boolean

    /** The tokens of [text] in [language]'s grammar; null without one. May throw: callers fall back. */
    fun tokenize(text: String, language: String): List<Node>?
}
