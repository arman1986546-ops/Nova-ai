package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CodeBlockBorder
import com.example.ui.theme.CodeBlockDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class MarkdownPart {
    data class TextBlock(val content: String) : MarkdownPart()
    data class CodeBlock(val language: String, val code: String) : MarkdownPart()
}

fun parseMarkdownBlocks(rawText: String): List<MarkdownPart> {
    val parts = mutableListOf<MarkdownPart>()
    val codeBlockRegex = Regex("```([a-zA-Z0-9_-]*)\\n?([\\s\\S]*?)```")

    var lastIndex = 0
    val matches = codeBlockRegex.findAll(rawText).toList()

    for (match in matches) {
        val startIndex = match.range.first
        if (startIndex > lastIndex) {
            val textBefore = rawText.substring(lastIndex, startIndex)
            if (textBefore.isNotBlank()) {
                parts.add(MarkdownPart.TextBlock(textBefore))
            }
        }

        val lang = match.groupValues[1].ifBlank { "code" }
        val code = match.groupValues[2].trimEnd()
        parts.add(MarkdownPart.CodeBlock(lang, code))
        lastIndex = match.range.last + 1
    }

    if (lastIndex < rawText.length) {
        val remaining = rawText.substring(lastIndex)
        if (remaining.isNotBlank()) {
            parts.add(MarkdownPart.TextBlock(remaining))
        }
    }

    if (parts.isEmpty() && rawText.isNotBlank()) {
        parts.add(MarkdownPart.TextBlock(rawText))
    }

    return parts
}

@Composable
fun FormattedAssistantMessage(
    text: String,
    messageId: Long,
    isSpeaking: Boolean,
    hasError: Boolean,
    onSpeakClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownPart.TextBlock -> {
                    RichFormattedText(content = block.content)
                }
                is MarkdownPart.CodeBlock -> {
                    CodeSnippetCard(
                        language = block.language,
                        code = block.code,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Message bottom action toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            if (hasError) {
                IconButton(
                    onClick = onRetryClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("retry_message_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry message",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Assistant Response", text))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("copy_message_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy response",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onSpeakClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("tts_message_button")
            ) {
                Icon(
                    imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isSpeaking) "Stop reading" else "Read aloud",
                    tint = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun RichFormattedText(
    content: String,
    modifier: Modifier = Modifier
) {
    val lines = content.split("\n")
    Column(modifier = modifier) {
        lines.forEach { line ->
            if (line.trim().startsWith("#")) {
                // Header line
                val level = line.takeWhile { it == '#' }.length
                val headerText = line.drop(level).trim()
                Text(
                    text = headerText,
                    style = when (level) {
                        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else if (line.trim().startsWith("- ") || line.trim().startsWith("* ") || line.trim().startsWith("• ")) {
                // Bullet item
                val bulletContent = line.trim().substring(2).trim()
                Row(
                    modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = parseInlineFormatting(bulletContent),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else if (line.isNotBlank()) {
                Text(
                    text = parseInlineFormatting(line),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

fun parseInlineFormatting(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
        var lastIdx = 0
        val matches = boldRegex.findAll(text).toList()

        for (m in matches) {
            val start = m.range.first
            if (start > lastIdx) {
                append(text.substring(lastIdx, start))
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(m.groupValues[1])
            }
            lastIdx = m.range.last + 1
        }
        if (lastIdx < text.length) {
            append(text.substring(lastIdx))
        }
    }
}

@Composable
fun CodeSnippetCard(
    language: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, CodeBlockBorder, RoundedCornerShape(10.dp)),
        color = CodeBlockDark,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111827))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = language.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF9CA3AF)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .testTag("copy_code_button")
                        .padding(horizontal = 4.dp)
                ) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Code snippet", code))
                            isCopied = true
                            scope.launch {
                                delay(2000)
                                isCopied = false
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy code",
                            tint = if (isCopied) Color(0xFF10B981) else Color(0xFF9CA3AF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = if (isCopied) "Copied!" else "Copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCopied) Color(0xFF10B981) else Color(0xFF9CA3AF)
                    )
                }
            }

            // Code content with horizontal scroll
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    ),
                    color = Color(0xFFE5E7EB)
                )
            }
        }
    }
}
