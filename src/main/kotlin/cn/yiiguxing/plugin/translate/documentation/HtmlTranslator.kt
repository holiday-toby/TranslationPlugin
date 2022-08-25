package cn.yiiguxing.plugin.translate.documentation

import cn.yiiguxing.plugin.translate.message
import cn.yiiguxing.plugin.translate.provider.IgnoredDocumentationElementsProvider
import cn.yiiguxing.plugin.translate.trans.*
import com.intellij.codeInsight.documentation.DocumentationComponent
import com.intellij.lang.Language
import com.intellij.ui.ColorUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.StringReader
import javax.swing.text.html.HTMLDocument
import javax.swing.text.html.HTMLEditorKit

private const val CSS_QUERY_DEFINITION = ".definition"
private const val CSS_QUERY_CONTENT = ".content"
private const val TAG_PRE = "pre"

private const val TRANSLATED_ATTR = "translated"

private val HTML_HEAD_REGEX = Regex("""<(?<tag>.+?) class="(?<class>.+?)">""")
private const val HTML_HEAD_REPLACEMENT = "<${'$'}{tag} class='${'$'}{class}'>"

private val HTML_KIT = HTMLEditorKit()


fun Translator.getTranslatedDocumentation(documentation: String, language: Language?): String {
    val document: Document = Jsoup.parse(documentation)
    if (document.body().hasAttr(TRANSLATED_ATTR)) {
        return documentation
    }

    val translatedDocumentation = try {
        if (this is DocumentationTranslator) {
            getTranslatedDocumentation(document, language)
        } else {
            getTranslatedDocumentation(document)
        }
    } catch (e: ContentLengthLimitException) {
        document.addLimitHint()
    } catch (e: TranslateException) {
        if (e.cause is ContentLengthLimitException) {
            document.addLimitHint()
        } else {
            throw e
        }
    }

    translatedDocumentation.body().attributes().remove(TRANSLATED_ATTR)

    return translatedDocumentation.outerHtml().fixHtml()
}

private fun Document.addLimitHint(): Document {
    val hintColor = ColorUtil.toHex(JBUI.CurrentTheme.Label.disabledForeground())
    @Suppress("UNNECESSARY_SAFE_CALL")
    body().selectFirst(CSS_QUERY_CONTENT)?.insertChildren(
        0,
        Element("div")
            .attr(
                "style",
                "color: $hintColor;" +
                        "margin-bottom: ${JBUIScale.scale(4)}px;" +
                        "padding: ${JBUIScale.scale(1)}px ${JBUIScale.scale(6)}px;" +
                        "border-left: ${JBUIScale.scale(3)}px $hintColor solid;"
            )
            .text(message("translate.documentation.limitHint"))
    )
    return this
}

/**
 * 修复HTML格式。[DocumentationComponent]识别不了`attr="val"`的属性表达形式，只识别`attr='val'`的表达形式，导致样式显示异常。
 */
private fun String.fixHtml(): String = replace(
    HTML_HEAD_REGEX,
    HTML_HEAD_REPLACEMENT
)

private fun DocumentationTranslator.getTranslatedDocumentation(document: Document, language: Language?): Document {
    val body = document.body()

    @Suppress("UNNECESSARY_SAFE_CALL")
    val definition = body.selectFirst(CSS_QUERY_DEFINITION)?.apply { remove() }

    @Suppress("UNNECESSARY_SAFE_CALL")
    // 删除多余的 `p` 标签。
    body.selectFirst(CSS_QUERY_CONTENT)
        ?.nextElementSibling()
        ?.takeIf { it.isEmptyParagraph() }
        ?.remove()

    val preElements = body.select(TAG_PRE)
    preElements.forEachIndexed { index, element ->
        element.replaceWith(Element(TAG_PRE).attr("id", index.toString()))
    }

    val ignoredElementsProvider = language?.let { IgnoredDocumentationElementsProvider.forLanguage(it) }
    val ignoredElements = ignoredElementsProvider?.ignoreElements(body)

    val translatedDocument = translateDocumentation(document, Lang.AUTO, (this as Translator).primaryLanguage)
    val translatedBody = translatedDocument.body()

    @Suppress("UNNECESSARY_SAFE_CALL")
    preElements.forEachIndexed { index, element ->
        translatedBody.selectFirst("""${TAG_PRE}[id="$index"]""")?.replaceWith(element)
    }
    ignoredElements?.let { ignoredElementsProvider.restoreIgnoredElements(translatedBody, it) }
    definition?.let { translatedBody.prependChild(it) }

    return translatedDocument
}

private fun Element.isEmptyParagraph(): Boolean = "p".equals(tagName(), true) && html().isBlank()


private fun Translator.getTranslatedDocumentation(document: Document): Document {
    val body = document.body()

    @Suppress("UNNECESSARY_SAFE_CALL")
    val definition = body.selectFirst(CSS_QUERY_DEFINITION)?.apply { remove() }

    val htmlDocument = HTMLDocument().also { HTML_KIT.read(StringReader(body.html()), it, 0) }
    val formatted = try {
        val content = htmlDocument.getText(0, htmlDocument.length).trim()
        checkContentLength(content, contentLengthLimit)
    } catch (e: ContentLengthLimitException) {
        definition?.let { body.insertChildren(0, it) }
        throw e
    }
    val translation =
        if (formatted.isEmpty()) ""
        else translate(formatted, Lang.AUTO, primaryLanguage).translation ?: ""

    val newBody = Element("body")
    definition?.let { newBody.appendChild(it) }
    Element("div")
        .addClass("content")
        .append(translation.replace("\n", "<br/>"))
        .let { newBody.appendChild(it) }

    body.replaceWith(newBody)

    return document
}
