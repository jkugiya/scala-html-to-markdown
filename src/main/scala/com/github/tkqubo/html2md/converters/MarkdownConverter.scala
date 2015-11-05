package com.github.tkqubo.html2md.converters

import com.github.tkqubo.html2md.ConversionRule
import com.github.tkqubo.html2md.helpers.NodeOps._
import org.jsoup.nodes._
import collection.JavaConversions._

class MarkdownConverter private (val rules: Seq[ConversionRule]) {
  def convert(node: Node): String = {
    node match {
      case element: Element =>
        rules
          .find(_.shouldConvert(element))
          .map(rule => rule.convert(node.toMarkdown, element))
          .getOrElse(node.toMarkdown)
      case _ => node.toMarkdown
    }
  }

  //noinspection ScalaStyle
  def ++(that: MarkdownConverter): MarkdownConverter =
    new MarkdownConverter(this.rules ++ that.rules)

  //noinspection ScalaStyle
  def +(rule: ConversionRule): MarkdownConverter =
    new MarkdownConverter(this.rules :+ rule)

  //noinspection ScalaStyle
  def +:(rule: ConversionRule): MarkdownConverter =
    new MarkdownConverter(rule +: this.rules)
}

object MarkdownConverter {
  private def createInstance(rules: ConversionRule*) = new MarkdownConverter(rules)

  val Default = createInstance(
    'p -> { content: String => s"\n\n$content\n\n" },

    'br -> "  \n",

    Seq('h1, 'h2, 'h3, 'h4, 'h5, 'h6) -> { (content: String, element: Element) =>
      val level = element.tagName().charAt(1).toString.toInt
      val prefix = 1.to(level).map(_ => "#").reduce(_ + _)
      s"\n\n$prefix $content\n\n"
    },

    'hr -> "\n\n* * *\n\n",

    Seq('em, 'i) -> { content: String => s"_${content}_" },

    Seq('strong, 'b) -> { content: String => s"**$content**" },

    // inline code
    { e: Element =>
      //noinspection ScalaStyle
      val hasSiblings = e.nextSibling != null || e.previousSibling != null
      val isCodeBlock = e.parent.tagName == "pre" && !hasSiblings
      e.tagName == "code" && !isCodeBlock
    } -> { code: String => s"`$code`" },

    // <a> with href attr
    { e: Element =>
      e.tagName() == "a" && e.hasAttr("href")
    } -> { (text: String, e: Element) =>
      val titlePart = if (e.hasAttr("title")) s""" "${e.attr("title")}"""" else ""
      s"""[$text](${e.attr("href")}$titlePart)"""
    },

    'img -> { e: Element =>
      val titlePart = if (e.hasAttr("title")) s""" "${e.attr("title")}"""" else ""
      if (e.hasAttr("src")) {
        s"""![${e.attr("alt")}](${e.attr("src")}$titlePart)"""
      } else {
        ""
      }
    },

    // code blocks
    { e: Element =>
      e.tagName() == "pre" && e.children().headOption.exists(_.tagName() == "code")
    } -> { e: Element => s"\n\n    ${e.children().head.text.replaceAll("\n", "\n    ")}\n\n" },

    'blockquote -> { blockquote: String =>
      val replacement = blockquote
        .trim
        .replaceAll("\n{3,}", "\n\n")
        .replaceAll("(?m)^", "> ")
      s"\n\n$replacement\n\n"
    },

    'li -> { (content: String, e: Element) =>
      val replacement = content.trim.replaceAll("\n", "\n    ")
      val index = e.parent.children.indexOf(e) + 1
      val prefix = if (e.parentNode().nodeName() == "ol") s"$index." else "*"
      s"$prefix  $replacement"
    },

    Seq('ul, 'ol) -> { (content: String, e: Element) =>
      val children = e
        .children
        .map(_.toMarkdown)
        .mkString("\n")
      if (e.parentNode.nodeName == "li") {
        s"\n\n$children"
      } else {
        s"\n\n$children\n\n"
      }
    },

    // block element
    { e: Element => e.isBlock } -> { (content: String, e: Element) =>
      s"\n\n${e.clone.html(content).outerHtml}\n\n"
    },

    // anything else
    { _: Element => true } -> { (content: String, e: Element) =>
      e.clone.html(content).outerHtml
    }
  )
}
