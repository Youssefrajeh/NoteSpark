package com.group15.notemate.data

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.group15.notemate.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** MVVM: Model helper. Formats a note as plain text for sharing or renders it to a PDF file. */
object NoteExporter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f

    /** Builds a clean plain-text representation of the note for the system share sheet. */
    fun formatForShare(
        context: Context,
        title: String,
        course: String,
        category: String,
        content: String,
        summary: String
    ): String = buildString {
        val header = title.trim().ifBlank { context.getString(R.string.untitled_note) }
        appendLine(header)
        val meta = listOf(course.trim(), category.trim()).filter { it.isNotEmpty() }.joinToString(" • ")
        if (meta.isNotEmpty()) appendLine(meta)
        if (summary.isNotBlank()) {
            appendLine()
            appendLine("${context.getString(R.string.summary_title)}:")
            appendLine(summary.trim())
        }
        if (content.isNotBlank()) {
            appendLine()
            append(content.trim())
        }
    }

    /** Renders the note into an A4 PDF document at [uri] on Dispatchers.IO. */
    suspend fun writePdf(
        context: Context,
        uri: Uri,
        title: String,
        course: String,
        category: String,
        content: String,
        summary: String
    ) = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        try {
            val contentWidth = (PAGE_WIDTH - MARGIN * 2).toInt()
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(26, 27, 32)
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(46, 91, 186)
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(34, 36, 42)
                textSize = 12f
            }

            val bodyText = buildString {
                if (summary.isNotBlank()) {
                    appendLine("${context.getString(R.string.summary_title)}:")
                    appendLine(summary.trim())
                    appendLine()
                }
                append(content.trim())
            }

            var pageNumber = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            var canvas = page.canvas
            var y = MARGIN

            val displayTitle = title.trim().ifBlank { context.getString(R.string.untitled_note) }
            val titleLayout = StaticLayout.Builder
                .obtain(displayTitle, 0, displayTitle.length, titlePaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
            canvas.save()
            canvas.translate(MARGIN, y)
            titleLayout.draw(canvas)
            canvas.restore()
            y += titleLayout.height + 8f

            val meta = listOf(course.trim(), category.trim()).filter { it.isNotEmpty() }.joinToString("  •  ")
            if (meta.isNotEmpty()) {
                canvas.drawText(meta, MARGIN, y + metaPaint.textSize, metaPaint)
                y += metaPaint.textSize + 14f
            } else {
                y += 8f
            }

            val bodyLayout = StaticLayout.Builder
                .obtain(bodyText, 0, bodyText.length, bodyPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.25f)
                .build()

            for (line in 0 until bodyLayout.lineCount) {
                val lineTop = bodyLayout.getLineTop(line).toFloat()
                val lineBottom = bodyLayout.getLineBottom(line).toFloat()
                val lineHeight = lineBottom - lineTop
                if (y + lineHeight > PAGE_HEIGHT - MARGIN) {
                    document.finishPage(page)
                    pageNumber++
                    page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                    canvas = page.canvas
                    y = MARGIN
                }
                val start = bodyLayout.getLineStart(line)
                val end = bodyLayout.getLineVisibleEnd(line)
                val baseline = y + (bodyLayout.getLineBaseline(line) - lineTop)
                canvas.drawText(bodyText, start, end, MARGIN, baseline, bodyPaint)
                y += lineHeight
            }

            document.finishPage(page)
            val stream = context.contentResolver.openOutputStream(uri)
                ?: throw IOException("Could not open PDF destination")
            stream.use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }
}

