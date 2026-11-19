package com.example.sketch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.audio.SoundEffect
import com.example.data.SketchDocument
import com.example.data.SketchPoint
import com.example.data.SketchStroke
import com.example.i18n.LocalStrings
import com.example.ui.theme.*
import java.util.UUID

private val SketchPalette = listOf(
    0xFF64F0FFL, // Navi Cyan
    0xFFF26A8DL, // Signal Coral
    0xFFF6BD60L, // Desk Amber
    0xFF5EEAD4L, // Harmony Jade
    0xFFA78BFAL, // Wired Violet
    0xFFFFFFFFL, // Pure White
    0xFFF6E2D6L, // Porcelain
    0xFFC5BCB2L, // Lain Sweater Beige
    0xFF573139L, // Espresso Mahogany
    0xFF3A1F24L, // Dark Hair
    0xFF4ADE80L, // Terminal Green
    0xFF1E293BL  // Deep Slate
)

private val CanvasPaperColor = Color(0xFF0D1322)

@Composable
fun SketchbookScreen(
    savedSketches: List<SketchDocument>,
    onPlaySfx: (SoundEffect) -> Unit,
    onSaveSketch: (SketchDocument) -> Unit,
    onDeleteSketch: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val strings = LocalStrings.current
    var currentDocId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var currentTitle by remember { mutableStateOf("") }
    var strokes by remember { mutableStateOf<List<SketchStroke>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<SketchStroke>>(emptyList()) }
    var activePoints by remember { mutableStateOf<List<SketchPoint>>(emptyList()) }

    var selectedColorArgb by remember { mutableLongStateOf(SketchPalette.first()) }
    var brushSize by remember { mutableFloatStateOf(9f) }
    var isEraser by remember { mutableStateOf(false) }
    var canvasPixelSize by remember { mutableStateOf(IntSize(1000, 700)) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var showGalleryDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        snackbarMessage = null
    }

    Scaffold(
        containerColor = WiredVoid,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("sketch_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.back,
                            tint = TextPrimary
                        )
                    }
                    Text(
                        text = strings.sketchbookTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = NaviCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = {
                            if (strokes.isNotEmpty()) {
                                val last = strokes.last()
                                strokes = strokes.dropLast(1)
                                redoStack = redoStack + last
                                onPlaySfx(SoundEffect.UI_TAP)
                            }
                        },
                        enabled = strokes.isNotEmpty(),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("sketch_undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = strings.toolUndo,
                            tint = if (strokes.isNotEmpty()) TextPrimary else TextMuted
                        )
                    }
                    IconButton(
                        onClick = {
                            if (redoStack.isNotEmpty()) {
                                val next = redoStack.last()
                                redoStack = redoStack.dropLast(1)
                                strokes = strokes + next
                                onPlaySfx(SoundEffect.UI_TAP)
                            }
                        },
                        enabled = redoStack.isNotEmpty(),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("sketch_redo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = strings.toolRedo,
                            tint = if (redoStack.isNotEmpty()) TextPrimary else TextMuted
                        )
                    }
                    IconButton(
                        onClick = { showGalleryDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("sketch_gallery_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = strings.toolGallery,
                            tint = DeskAmber
                        )
                    }
                    IconButton(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("sketch_save_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = strings.toolSave,
                            tint = HarmonyJade
                        )
                    }
                }
            }

            // Drawing Canvas Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CanvasPaperColor)
                    .border(1.5.dp, WiredBorder, RoundedCornerShape(16.dp))
                    .onSizeChanged { canvasPixelSize = it }
                    .pointerInput(selectedColorArgb, brushSize, isEraser) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                activePoints = listOf(SketchPoint(offset.x, offset.y))
                                redoStack = emptyList()
                            },
                            onDrag = { change, _ ->
                                val pos = change.position
                                activePoints = activePoints + SketchPoint(pos.x, pos.y)
                            },
                            onDragEnd = {
                                if (activePoints.isNotEmpty()) {
                                    strokes = strokes + SketchStroke(
                                        points = activePoints,
                                        colorArgb = selectedColorArgb,
                                        strokeWidth = brushSize,
                                        isEraser = isEraser
                                    )
                                    activePoints = emptyList()
                                    onPlaySfx(SoundEffect.INK_DRAW)
                                }
                            },
                            onDragCancel = {
                                activePoints = emptyList()
                            }
                        )
                    }
                    .testTag("sketch_canvas")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Subtle studio dot grid
                    val step = 42f
                    var gx = step
                    while (gx < size.width) {
                        var gy = step
                        while (gy < size.height) {
                            drawCircle(
                                color = WiredBorder.copy(alpha = 0.25f),
                                radius = 1.3f,
                                center = Offset(gx, gy)
                            )
                            gy += step
                        }
                        gx += step
                    }

                    // Committed strokes
                    strokes.forEach { stroke ->
                        drawSmoothStroke(stroke, scaleX = 1f, scaleY = 1f)
                    }

                    // Currently dragged stroke
                    if (activePoints.isNotEmpty()) {
                        drawSmoothStroke(
                            SketchStroke(
                                points = activePoints,
                                colorArgb = selectedColorArgb,
                                strokeWidth = brushSize,
                                isEraser = isEraser
                            ),
                            scaleX = 1f,
                            scaleY = 1f
                        )
                    }
                }
            }

            // Tool Controls Card
            Surface(
                color = WiredSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, WiredBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Mode toggles + Size slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !isEraser,
                            onClick = { isEraser = false },
                            label = { Text(strings.toolBrush) },
                            leadingIcon = {
                                Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.testTag("sketch_brush_chip")
                        )
                        FilterChip(
                            selected = isEraser,
                            onClick = { isEraser = true },
                            label = { Text(strings.toolEraser) },
                            leadingIcon = {
                                Icon(Icons.Default.AutoFixNormal, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.testTag("sketch_eraser_chip")
                        )
                        Spacer(Modifier.weight(1f))
                        // Brush size preview dot
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(WiredAbyss),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size((brushSize * 0.7f).coerceIn(4f, 22f).dp)
                                    .clip(CircleShape)
                                    .background(if (isEraser) TextSecondary else Color(selectedColorArgb))
                            )
                        }
                        IconButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("sketch_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = strings.toolClear,
                                tint = SignalCoral
                            )
                        }
                    }

                    // Brush size slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${strings.brushSizeLabel}: ${brushSize.toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Slider(
                            value = brushSize,
                            onValueChange = { brushSize = it },
                            valueRange = 2f..32f,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sketch_size_slider")
                        )
                    }

                    // Color Palette Row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(SketchPalette) { argb ->
                            val selected = !isEraser && selectedColorArgb == argb
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) Color.White else WiredBorder,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        selectedColorArgb = argb
                                        isEraser = false
                                    }
                            )
                        }
                    }
                }
            }
        }
    }

    // Save Drawing Dialog
    if (showSaveDialog) {
        var titleInput by remember {
            mutableStateOf(
                currentTitle.ifBlank { "Wired Sketch #${savedSketches.size + 1}" }
            )
        }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = WiredSurface,
            title = { Text(strings.saveSketchTitle, color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    placeholder = { Text(strings.sketchNamePlaceholder) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sketch_title_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalTitle = titleInput.trim().ifBlank { "Sketch #${savedSketches.size + 1}" }
                        currentTitle = finalTitle
                        val doc = SketchDocument(
                            id = currentDocId,
                            title = finalTitle,
                            updatedAtMillis = System.currentTimeMillis(),
                            canvasWidth = canvasPixelSize.width.coerceAtLeast(1),
                            canvasHeight = canvasPixelSize.height.coerceAtLeast(1),
                            strokes = strokes
                        )
                        onSaveSketch(doc)
                        onPlaySfx(SoundEffect.CHECKPOINT)
                        showSaveDialog = false
                        snackbarMessage = strings.savedToast
                    },
                    modifier = Modifier.testTag("sketch_save_confirm")
                ) {
                    Text(strings.toolSave)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // Clear Confirmation Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = WiredSurface,
            title = { Text(strings.clearConfirmTitle, color = TextPrimary) },
            text = { Text(strings.clearConfirmBody, color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        strokes = emptyList()
                        redoStack = emptyList()
                        showClearDialog = false
                        onPlaySfx(SoundEffect.UI_TAP)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SignalCoral)
                ) {
                    Text(strings.toolClear)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // Saved Gallery Dialog
    if (showGalleryDialog) {
        AlertDialog(
            onDismissRequest = { showGalleryDialog = false },
            containerColor = WiredSurface,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.galleryTitle, color = NaviCyan)
                    TextButton(
                        onClick = {
                            currentDocId = UUID.randomUUID().toString()
                            currentTitle = ""
                            strokes = emptyList()
                            redoStack = emptyList()
                            showGalleryDialog = false
                        }
                    ) {
                        Text(strings.newCanvas)
                    }
                }
            },
            text = {
                if (savedSketches.isEmpty()) {
                    Text(
                        text = strings.galleryEmpty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.heightIn(max = 340.dp)
                    ) {
                        items(savedSketches, key = { it.id }) { doc ->
                            Surface(
                                color = WiredAbyss,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, WiredBorder, RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Live vector thumbnail preview
                                    Canvas(
                                        modifier = Modifier
                                            .size(64.dp, 48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(CanvasPaperColor)
                                            .border(1.dp, WiredBorder, RoundedCornerShape(8.dp))
                                    ) {
                                        val sx = size.width / doc.canvasWidth.coerceAtLeast(1)
                                        val sy = size.height / doc.canvasHeight.coerceAtLeast(1)
                                        doc.strokes.forEach { st ->
                                            drawSmoothStroke(st, scaleX = sx, scaleY = sy)
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = doc.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${doc.strokes.size} strokes",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            // Scale loaded strokes cleanly to current canvas size
                                            val sx = canvasPixelSize.width.toFloat() / doc.canvasWidth.coerceAtLeast(1)
                                            val sy = canvasPixelSize.height.toFloat() / doc.canvasHeight.coerceAtLeast(1)
                                            currentDocId = doc.id
                                            currentTitle = doc.title
                                            strokes = doc.strokes.map { st ->
                                                st.copy(points = st.points.map { p -> SketchPoint(p.x * sx, p.y * sy) })
                                            }
                                            redoStack = emptyList()
                                            showGalleryDialog = false
                                            onPlaySfx(SoundEffect.UI_TAP)
                                        }
                                    ) {
                                        Text(strings.open, color = NaviCyan)
                                    }
                                    IconButton(
                                        onClick = { onDeleteSketch(doc.id) },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = strings.delete,
                                            tint = SignalCoral,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGalleryDialog = false }) {
                    Text(strings.back)
                }
            }
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSmoothStroke(
    stroke: SketchStroke,
    scaleX: Float,
    scaleY: Float
) {
    if (stroke.points.isEmpty()) return
    val strokeColor = if (stroke.isEraser) CanvasPaperColor else Color(stroke.colorArgb)
    val scaledWidth = (stroke.strokeWidth * ((scaleX + scaleY) * 0.5f)).coerceAtLeast(1.2f)
    if (stroke.points.size == 1) {
        val p = stroke.points.first()
        drawCircle(
            color = strokeColor,
            radius = scaledWidth * 0.5f,
            center = Offset(p.x * scaleX, p.y * scaleY)
        )
        return
    }
    val path = Path().apply {
        val first = stroke.points.first()
        moveTo(first.x * scaleX, first.y * scaleY)
        for (i in 1 until stroke.points.size) {
            val prev = stroke.points[i - 1]
            val curr = stroke.points[i]
            val midX = (prev.x + curr.x) * 0.5f * scaleX
            val midY = (prev.y + curr.y) * 0.5f * scaleY
            quadraticTo(prev.x * scaleX, prev.y * scaleY, midX, midY)
        }
        val last = stroke.points.last()
        lineTo(last.x * scaleX, last.y * scaleY)
    }
    drawPath(
        path = path,
        color = strokeColor,
        style = Stroke(
            width = scaledWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}
