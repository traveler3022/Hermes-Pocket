package com.hermes.android.ui.component

/**
 * Typed content blocks parsed from a raw assistant markdown message.
 *
 * Hermes sends assistant content as plain markdown text, but a real agent's
 * output is multi-format: prose, fenced code, mermaid diagrams, images the
 * agent wrote to disk, HTML/video/file artifacts referenced by bare paths.
 * Instead of asking one markdown library to render everything, the message is
 * split ONCE into typed blocks and each block gets its own native renderer
 * (see MessageBubble in ChatScreen):
 *
 *   Text    → markdown renderer          Image → AsyncImage (tap = fullscreen)
 *   Code    → monospace card + copy      Mermaid → WebView + mermaid.js
 *   Html    → open-in-browser card       Video → open-with-player card
 *   FileRef → download card
 *
 * The parser is pure Kotlin (no Android imports) so it is unit-testable.
 */
sealed class ContentBlock {
    data class Text(val markdown: String) : ContentBlock()
    data class Image(val alt: String, val url: String) : ContentBlock()
    data class Code(val language: String, val code: String) : ContentBlock()
    data class Mermaid(val code: String) : ContentBlock()
    data class Html(val url: String, val name: String) : ContentBlock()
    data class Video(val url: String, val name: String) : ContentBlock()
    data class FileRef(val url: String, val name: String) : ContentBlock()
}

private val fenceRegex = Regex("```([A-Za-z0-9+_.-]*)[ \\t]*\\n([\\s\\S]*?)(?:```|$)")
private val mdImageRegex = Regex("""!\[([^\]]*)]\(([^)\s]+)\)""")
private val mdLinkRegex = Regex("""(?<!!)\[([^\]]*)]\(([^)\s]+)\)""")
private val inlineCodeRegex = Regex("`[^`\n]+`")

// Bare path/URL to a media artifact the agent wrote or linked, e.g.
// "Saved to ~/.hermes/images/plot.png" or "file:///.../page.html".
// Fix: this used to require the extension to be in a small hardcoded
// whitelist (png/jpg/pdf/zip/...), so anything else (docx, tar.gz, apk,
// heic, mp3, ...) was left as plain text with no download card at all.
// Now any plausible extension (1-10 word chars) matches "universally";
// the small denylist below excludes bare domain-like endings (a URL with
// no path, e.g. "https://example.com") that would otherwise false-positive
// as a "file" named "com". Unrecognized (non-image/video/html) extensions
// still get a generic download card via classifyUrl's `else` branch.
// The path has to start a token: without the lookbehind, "app/src/main/Foo.kt" matched
// from its first slash and "4/5.0" from its only one, and the sentence around them was
// cut in two by a download card for a file that doesn't exist.
private val bareMediaRegex = Regex(
    """(?<![\w/.~:-])(?:file://|https?://|content://|~/|/)[^\s"'`<>\[\]()]+\.[A-Za-z0-9]{1,10}\b""",
)

// How Hermes' desktop chat delivers a file: "MEDIA:/root/out.pdf", often wrapped in quotes,
// backticks or emphasis (gateway/platforms/base.py MEDIA_TAG_CLEANUP_RE). Any file type, with or
// without an extension; the tag itself never shows as text.
private val mediaTagRegex = Regex(
    """[`"'*_]{0,3}MEDIA:\s*(?:`([^`\n]+)`|"([^"\n]+)"|'([^'\n]+)'|((?:~/|/)[^\s`"'<>*]+?))""" +
        """(?=[\s`"'*,;:)\]}]|MEDIA:|\.(?:\s|$)|$)[`"'*_]{0,3}""",
)

private val nonFileExtensions = setOf(
    "com", "org", "net", "io", "dev", "app", "co", "gov", "edu", "info", "biz", "me", "ai",
)

private val imageExts = setOf(
    "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic", "heif", "tiff", "ico",
)
private val videoExts = setOf("mp4", "webm", "mov", "m4v", "mkv", "avi", "3gp")
private val htmlExts = setOf("html", "htm")

private fun classifyUrl(url: String, alt: String = ""): ContentBlock {
    val ext = url.substringAfterLast('.', "").lowercase()
    val name = alt.ifBlank { url.substringAfterLast('/').substringBefore('?') }
    return when (ext) {
        in imageExts -> ContentBlock.Image(alt = name, url = url)
        in videoExts -> ContentBlock.Video(url = url, name = name)
        in htmlExts -> ContentBlock.Html(url = url, name = name)
        else -> ContentBlock.FileRef(url = url, name = name)
    }
}

/** A link target that is a file the agent made, rather than a web page. */
private fun isLocalArtifact(target: String): Boolean =
    (target.startsWith("/") || target.startsWith("~/") || target.startsWith("file://")) &&
        bareMediaRegex.matchEntire(target) != null

/**
 * Extract media blocks from prose: markdown images, markdown links to files the agent
 * made, and bare artifact paths. Markdown syntax owns the paths inside it — a link to a
 * web page stays a link for the markdown renderer (it used to be split around an inline
 * web preview, leaving "[the docs](" and ")" behind), and a path inside a longer inline
 * command stays code. A path that is a whole inline code span is the file, backticks and all.
 */
private fun parseProse(segment: String, out: MutableList<ContentBlock>) {
    val images = mdImageRegex.findAll(segment).toList()
    val links = mdLinkRegex.findAll(segment).toList()
    val codeSpans = inlineCodeRegex.findAll(segment).toList()
    fun inside(range: IntRange, outer: MatchResult) = range.first >= outer.range.first && range.last <= outer.range.last

    val candidates = mutableListOf<Pair<IntRange, ContentBlock>>()
    for (match in images) {
        // Markdown image syntax is ALWAYS an image, even when the URL has
        // no file extension (common for web images, e.g. picsum.photos/200
        // or a query-only CDN link). Only bare paths are classified by ext.
        val alt = match.groupValues[1]
        val url = match.groupValues[2]
        candidates += match.range to ContentBlock.Image(alt = alt.ifBlank { url.substringAfterLast('/').substringBefore('?') }, url = url)
    }
    for (match in links) {
        val target = match.groupValues[2]
        if (isLocalArtifact(target)) candidates += match.range to classifyUrl(target, alt = match.groupValues[1])
    }
    for (tag in mediaTagRegex.findAll(segment)) {
        if (codeSpans.any { inside(tag.range, it) }) continue
        // A closing `_` of _emphasis_ can't be told from the path by the regex; no file ends in one.
        val path = tag.groupValues.drop(1).first { it.isNotEmpty() }.trim().trimEnd('_')
        candidates += tag.range to classifyUrl(path)
    }
    for (bare in bareMediaRegex.findAll(segment)) {
        val ext = bare.value.substringAfterLast('.', "").lowercase()
        if (ext in nonFileExtensions) continue
        if (images.any { inside(bare.range, it) } || links.any { inside(bare.range, it) }) continue
        val span = codeSpans.firstOrNull { inside(bare.range, it) }
        when {
            span == null -> candidates += bare.range to classifyUrl(bare.value)
            span.value == "`${bare.value}`" -> candidates += span.range to classifyUrl(bare.value)
        }
    }

    var cursor = 0
    for ((range, block) in candidates.sortedBy { it.first.first }) {
        if (range.first < cursor) continue // inside one already taken
        val before = segment.substring(cursor, range.first)
        if (before.isNotBlank()) out.add(ContentBlock.Text(before.trim()))
        out.add(block)
        cursor = range.last + 1
    }
    val tail = segment.substring(cursor)
    if (tail.isNotBlank()) out.add(ContentBlock.Text(tail.trim()))
}

/**
 * Split raw assistant markdown into an ordered list of typed [ContentBlock]s.
 * Fenced code blocks are isolated first (their contents are never scanned for
 * media paths), then prose segments are scanned for images/artifacts.
 * An unterminated trailing fence (mid-stream) is still treated as code so a
 * streaming message doesn't flash half-parsed markdown.
 */
fun parseContentBlocks(text: String): List<ContentBlock> {
    val out = mutableListOf<ContentBlock>()
    var cursor = 0
    for (fence in fenceRegex.findAll(text)) {
        val before = text.substring(cursor, fence.range.first)
        if (before.isNotBlank()) parseProse(before, out)
        val lang = fence.groupValues[1].lowercase()
        val body = fence.groupValues[2].trimEnd()
        if (body.isNotBlank()) {
            out.add(
                if (lang == "mermaid") ContentBlock.Mermaid(body)
                else ContentBlock.Code(language = lang, code = body),
            )
        }
        cursor = fence.range.last + 1
    }
    val tail = text.substring(cursor.coerceAtMost(text.length))
    if (tail.isNotBlank()) parseProse(tail, out)
    if (out.isEmpty() && text.isNotBlank()) out.add(ContentBlock.Text(text))
    return out
}
