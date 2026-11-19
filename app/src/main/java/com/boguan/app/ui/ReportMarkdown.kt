package com.boguan.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.model.DefaultMarkdownColors
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Markdown 报告渲染（ADR-0014）。
 *
 * 渲染器用 mikepenz 的 multiplatform-markdown-renderer（0.41.0，对应 Compose 1.11 / compileSdk 36），
 * 颜色与文字样式全部取自 Miuix 主题，避免出现「HyperOS 外观 + Material 默认排版」的割裂感。
 */
@Composable
fun ReportMarkdown(markdown: String, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val textStyles = MiuixTheme.textStyles
    val markdownColors = remember(colors) {
        DefaultMarkdownColors(
            text = colors.onBackground,
            codeBackground = colors.surfaceContainerHigh,
            inlineCodeBackground = colors.surfaceContainer,
            dividerColor = colors.dividerLine,
            tableBackground = colors.surfaceContainer,
            highlightText = colors.onBackground,
            highlightBackground = colors.secondaryContainer,
        )
    }
    val codeStyle = remember {
        TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
    val markdownTypography = remember(textStyles) {
        DefaultMarkdownTypography(
            // 报告本身已有页面标题，Markdown 的 H1 用 title2 免得压过整个页面
            h1 = textStyles.title2,
            h2 = textStyles.title3,
            h3 = textStyles.subtitle,
            h4 = textStyles.body1,
            h5 = textStyles.body1,
            h6 = textStyles.body1,
            text = textStyles.main,
            code = codeStyle,
            inlineCode = codeStyle,
            quote = textStyles.paragraph,
            paragraph = textStyles.main,
            ordered = textStyles.main,
            bullet = textStyles.main,
            list = textStyles.main,
            textLink = TextLinkStyles(style = SpanStyle(color = colors.primary)),
            table = textStyles.body2,
        )
    }
    Markdown(
        content = markdown,
        colors = markdownColors,
        typography = markdownTypography,
        modifier = modifier.fillMaxWidth(),
    )
}
