package com.example.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.camera.core.ImageProxy
import com.example.intelligence.IntelligenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.max

enum class VisionTaskMode(val label: String, val defaultPrompt: String) {
    REALTIME_SCENE("Scene Analysis", "Analyze this camera view concisely. Identify key objects, context, text, and actionable observations."),
    OCR_EXTRACT("OCR Extraction", "Perform exact OCR on this image. Extract all visible text faithfully, preserving structure and key numbers."),
    SCREENSHOT_QA("Screenshot Intel", "Analyze this screenshot. Explain the UI state, errors, settings, or key content shown."),
    DOCUMENT_INTEL("Document Intel", "Summarize this document, extract key facts, entities, risks, and action items."),
    VISUAL_QA("Visual Q&A", "Answer the user's question accurately based strictly on the visual evidence.")
}

data class DocumentLoadResult(
    val fileName: String,
    val extractedText: String?,
    val renderedPageBitmap: Bitmap?
)

class VisionEngine(
    private val context: Context,
    private val intelligenceEngine: IntelligenceEngine
) {

    fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val rawBitmap = image.toBitmap()
        val rotation = image.imageInfo.rotationDegrees
        val rotated = if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        } else {
            rawBitmap
        }
        return downscaleForGemini(rotated)
    }

    suspend fun loadBitmapFromUri(uri: Uri): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val maxDim = max(boundsOpts.outWidth, boundsOpts.outHeight).coerceAtLeast(1)
            var sampleSize = 1
            while (maxDim / sampleSize > 1600) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: error("Unable to decode image from URI.")
            downscaleForGemini(bitmap)
        }
    }

    suspend fun loadDocumentFromUri(uri: Uri): Result<DocumentLoadResult> = withContext(Dispatchers.IO) {
        runCatching {
            val name = queryDisplayName(uri) ?: "document"
            val mime = context.contentResolver.getType(uri).orEmpty()
            if (mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)) {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: error("Cannot open PDF file descriptor.")
                pfd.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        if (renderer.pageCount <= 0) error("PDF has no pages.")
                        val page = renderer.openPage(0)
                        val bitmap = Bitmap.createBitmap(
                            (page.width * 2).coerceAtMost(1400),
                            (page.height * 2).coerceAtMost(1800),
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        DocumentLoadResult(
                            fileName = name,
                            extractedText = "PDF document (${renderer.pageCount} page(s)). Page 1 rendered for multimodal analysis.",
                            renderedPageBitmap = downscaleForGemini(bitmap)
                        )
                    }
                }
            } else {
                val text = context.contentResolver.openInputStream(uri)?.use { input ->
                    BufferedReader(InputStreamReader(input)).readText()
                }?.take(24_000) ?: error("Unable to read document text.")
                DocumentLoadResult(
                    fileName = name,
                    extractedText = text,
                    renderedPageBitmap = null
                )
            }
        }
    }

    suspend fun analyze(
        mode: VisionTaskMode,
        customQuestion: String,
        bitmap: Bitmap?,
        documentText: String? = null
    ): Result<String> {
        val prompt = buildString {
            append("[MODE: ${mode.label}]\n")
            append(mode.defaultPrompt)
            if (customQuestion.isNotBlank()) {
                append("\n\nUser Question / Instruction: $customQuestion")
            }
        }
        return intelligenceEngine.analyzeMultimodal(
            prompt = prompt,
            bitmap = bitmap,
            documentText = documentText
        )
    }

    private fun downscaleForGemini(bitmap: Bitmap, maxEdge: Int = 1280): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxEdge && height <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / max(width, height).toFloat()
        val newW = (width * scale).toInt().coerceAtLeast(1)
        val newH = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }
}
