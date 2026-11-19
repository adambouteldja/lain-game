package com.example.sketch

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

data class ExportResult(
    val success: Boolean,
    val displayPath: String = "",
    val errorMessage: String? = null
)

/**
 * High-fidelity offscreen Bitmap compositor & MediaStore PNG exporter for LAIN Drawing Studio.
 * Renders every visible layer independently (supporting true per-layer erasing, opacity,
 * transforms, flips, all 8 brush types, and optional cropping) and writes PNGs to Android Gallery.
 */
object StudioExporter {

    fun renderProjectToBitmap(
        project: StudioProject,
        maxDimension: Int = 1080,
        applyCrop: Boolean = true
    ): Bitmap {
        val baseW = project.canvasWidth.coerceIn(256, maxDimension)
        val baseH = project.canvasHeight.coerceIn(256, maxDimension)
        val composite = Bitmap.createBitmap(baseW, baseH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(composite)

        // Canvas paper background
        canvas.drawColor(project.backgroundColorArgb.toInt())

        val layerBitmap = Bitmap.createBitmap(baseW, baseH, Bitmap.Config.ARGB_8888)
        val layerCanvas = Canvas(layerBitmap)
        val blitPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        for (layer in project.layers) {
            if (!layer.visible || layer.opacity <= 0.005f) continue
            layerCanvas.drawColor(0, PorterDuff.Mode.CLEAR)
            layerCanvas.save()

            // Apply layer transform around canvas center
            val cx = baseW * 0.5f
            val cy = baseH * 0.5f
            layerCanvas.translate(layer.offsetX * baseW, layer.offsetY * baseH)
            layerCanvas.translate(cx, cy)
            if (layer.rotationDeg != 0f) {
                layerCanvas.rotate(layer.rotationDeg)
            }
            layerCanvas.scale(
                if (layer.flipH) -1f else 1f,
                if (layer.flipV) -1f else 1f
            )
            layerCanvas.translate(-cx, -cy)

            // Optional bucket fill on this layer
            layer.fillColorArgb?.let { fillCol ->
                layerCanvas.drawColor(fillCol.toInt())
            }

            // Render every stroke on this layer
            for (stroke in layer.strokes) {
                drawStudioStrokeOnAndroidCanvas(layerCanvas, stroke, baseW.toFloat(), baseH.toFloat())
            }

            layerCanvas.restore()

            blitPaint.alpha = (layer.opacity.coerceIn(0f, 1f) * 255f).toInt()
            canvas.drawBitmap(layerBitmap, 0f, 0f, blitPaint)
        }
        layerBitmap.recycle()

        if (!applyCrop) return composite
        val c = project.cropRect
        val left = (c.leftFrac.coerceIn(0f, 0.9f) * baseW).toInt()
        val top = (c.topFrac.coerceIn(0f, 0.9f) * baseH).toInt()
        val right = (c.rightFrac.coerceIn(0.1f, 1f) * baseW).toInt()
        val bottom = (c.bottomFrac.coerceIn(0.1f, 1f) * baseH).toInt()
        val cropW = max(16, right - left)
        val cropH = max(16, bottom - top)
        if (left == 0 && top == 0 && cropW == baseW && cropH == baseH) {
            return composite
        }
        val cropped = Bitmap.createBitmap(
            composite,
            left.coerceIn(0, baseW - 1),
            top.coerceIn(0, baseH - 1),
            min(cropW, baseW - left),
            min(cropH, baseH - top)
        )
        composite.recycle()
        return cropped
    }

    fun sampleCompositeColor(project: StudioProject, normX: Float, normY: Float): Long {
        val preview = renderProjectToBitmap(project, maxDimension = 256, applyCrop = false)
        return try {
            val px = (normX.coerceIn(0f, 0.999f) * preview.width).toInt().coerceIn(0, preview.width - 1)
            val py = (normY.coerceIn(0f, 0.999f) * preview.height).toInt().coerceIn(0, preview.height - 1)
            val argb = preview.getPixel(px, py)
            (argb.toLong() and 0xFFFFFFFFL) or 0xFF000000L
        } catch (_: Exception) {
            0xFF64F0FFL
        } finally {
            preview.recycle()
        }
    }

    fun exportProjectToGallery(context: Context, project: StudioProject): ExportResult {
        val safeTitle = project.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "LAIN_Art" }
        val fileName = "${safeTitle}_${System.currentTimeMillis()}.png"
        val bitmap = try {
            renderProjectToBitmap(project, maxDimension = 1080, applyCrop = true)
        } catch (e: Exception) {
            return ExportResult(false, errorMessage = e.message ?: "Render failed")
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/LAIN")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return ExportResult(false, errorMessage = "MediaStore insert returned null")
                resolver.openOutputStream(uri)?.use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        return ExportResult(false, errorMessage = "PNG compression failed")
                    }
                } ?: return ExportResult(false, errorMessage = "Unable to open output stream")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                ExportResult(true, displayPath = "Pictures/LAIN/$fileName")
            } else {
                val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    ?: File(context.filesDir, "exports")
                val outDir = File(picturesDir, "LAIN").apply { if (!exists()) mkdirs() }
                val outFile = File(outDir, fileName)
                FileOutputStream(outFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                ExportResult(true, displayPath = outFile.absolutePath)
            }
        } catch (e: Exception) {
            ExportResult(false, errorMessage = e.message ?: "Export error")
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawStudioStrokeOnAndroidCanvas(
        canvas: Canvas,
        stroke: StudioStroke,
        width: Float,
        height: Float
    ) {
        if (stroke.points.isEmpty()) return
        val scaleFactor = min(width, height) / 420f
        val avgPressure = stroke.points.map { it.pressure }.average().toFloat().coerceIn(0.25f, 1.35f)
        val baseWidth = (stroke.size * scaleFactor * (0.65f + 0.5f * avgPressure)).coerceAtLeast(1.2f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = if (stroke.brushType == BrushType.MARKER) Paint.Cap.SQUARE else Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = baseWidth
        }

        if (stroke.brushType == BrushType.ERASER) {
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            paint.alpha = 255
        } else {
            paint.color = stroke.colorArgb.toInt()
            val brushAlpha = when (stroke.brushType) {
                BrushType.PENCIL -> 0.78f
                BrushType.PEN -> 1.0f
                BrushType.SOFT_BRUSH -> 0.65f
                BrushType.HARD_BRUSH -> 0.96f
                BrushType.AIRBRUSH -> 0.34f
                BrushType.MARKER -> 0.58f
                BrushType.WATERCOLOR -> 0.42f
                BrushType.ERASER -> 1.0f
            }
            paint.alpha = (stroke.opacity.coerceIn(0.05f, 1f) * brushAlpha * 255f).toInt().coerceIn(8, 255)
            val blurRadius = when (stroke.brushType) {
                BrushType.SOFT_BRUSH -> max(1.5f, baseWidth * (1f - stroke.hardness * 0.7f) * 0.45f)
                BrushType.AIRBRUSH -> max(3f, baseWidth * 0.65f)
                BrushType.WATERCOLOR -> max(2f, baseWidth * 0.35f)
                else -> 0f
            }
            if (blurRadius > 0.5f) {
                paint.maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
            }
        }

        if (stroke.points.size == 1) {
            val pt = stroke.points.first()
            paint.style = Paint.Style.FILL
            canvas.drawCircle(pt.x * width, pt.y * height, baseWidth * 0.5f, paint)
            return
        }

        val path = Path().apply {
            val first = stroke.points.first()
            moveTo(first.x * width, first.y * height)
            for (i in 1 until stroke.points.size) {
                val prev = stroke.points[i - 1]
                val curr = stroke.points[i]
                val mx = (prev.x + curr.x) * 0.5f * width
                val my = (prev.y + curr.y) * 0.5f * height
                quadTo(prev.x * width, prev.y * height, mx, my)
            }
            val last = stroke.points.last()
            lineTo(last.x * width, last.y * height)
        }
        canvas.drawPath(path, paint)
    }
}
