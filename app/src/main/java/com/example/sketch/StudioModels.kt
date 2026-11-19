package com.example.sketch

enum class BrushType {
    PENCIL,
    PEN,
    SOFT_BRUSH,
    HARD_BRUSH,
    AIRBRUSH,
    MARKER,
    WATERCOLOR,
    ERASER
}

enum class StudioTool {
    BRUSH,
    ERASER,
    BUCKET_FILL,
    EYEDROPPER,
    PAN_ZOOM,
    TRANSFORM
}

data class StudioPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1.0f
)

data class StudioStroke(
    val id: String,
    val points: List<StudioPoint>,
    val brushType: BrushType = BrushType.PEN,
    val colorArgb: Long = 0xFF64F0FFL,
    val size: Float = 8f,
    val opacity: Float = 1f,
    val hardness: Float = 0.85f
)

data class DrawingLayer(
    val id: String,
    val name: String,
    val visible: Boolean = true,
    val opacity: Float = 1f,
    val fillColorArgb: Long? = null,
    val strokes: List<StudioStroke> = emptyList(),
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotationDeg: Float = 0f,
    val flipH: Boolean = false,
    val flipV: Boolean = false
)

data class StudioCropRect(
    val leftFrac: Float = 0f,
    val topFrac: Float = 0f,
    val rightFrac: Float = 1f,
    val bottomFrac: Float = 1f
)

data class StudioProject(
    val id: String,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val canvasWidth: Int = 1080,
    val canvasHeight: Int = 1080,
    val backgroundColorArgb: Long = 0xFF0D1322L,
    val layers: List<DrawingLayer>,
    val activeLayerId: String,
    val cropRect: StudioCropRect = StudioCropRect()
)
