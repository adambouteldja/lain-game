package com.example.sketch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.i18n.AppStrings
import com.example.ui.theme.*
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/**
 * LAIN Drawing Studio (Evolution 2.0) — ibisPaint-inspired mobile digital painting workspace:
 * - 8 brushes with pressure sensitivity, opacity, hardness, and stroke stabilization
 * - Independent multi-layer system (create, rename, duplicate, visibility, opacity, reorder, merge, delete)
 * - Tools: Brush, Layer Eraser, Bucket Fill, Eyedropper, Pan/Zoom/Rotate Canvas, Layer Transform/Flip/Crop
 * - Non-flattened project save/gallery + autosave draft recovery + one-tap Export PNG to Android Gallery
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SketchbookScreen(
    strings: AppStrings,
    savedProjects: List<StudioProject>,
    autosaveDraft: StudioProject?,
    onSaveProject: (StudioProject) -> Unit,
    onAutosaveProject: (StudioProject) -> Unit,
    onDuplicateProject: (StudioProject) -> Unit,
    onDeleteProject: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    fun createFreshProject(index: Int = savedProjects.size + 1): StudioProject {
        val now = System.currentTimeMillis()
        val l1 = DrawingLayer(id = "layer_${UUID.randomUUID()}", name = "${strings.studioLayer} 1")
        return StudioProject(
            id = UUID.randomUUID().toString(),
            title = "${strings.sketchDefaultTitle} #$index",
            createdAtMillis = now,
            updatedAtMillis = now,
            layers = listOf(l1),
            activeLayerId = l1.id
        )
    }

    var project by remember { mutableStateOf(savedProjects.firstOrNull() ?: createFreshProject(1)) }
    var undoStack by remember { mutableStateOf<List<StudioProject>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<StudioProject>>(emptyList()) }

    var activeTool by remember { mutableStateOf(StudioTool.BRUSH) }
    var selectedBrush by remember { mutableStateOf(BrushType.PEN) }
    var selectedColorArgb by remember { mutableLongStateOf(0xFF64F0FFL) }
    var brushSize by remember { mutableFloatStateOf(9f) }
    var brushOpacity by remember { mutableFloatStateOf(1f) }
    var brushHardness by remember { mutableFloatStateOf(0.85f) }
    var stabilization by remember { mutableFloatStateOf(0.35f) }

    // Canvas viewport transform (Zoom / Pan / Rotate)
    var canvasScale by remember { mutableFloatStateOf(1f) }
    var canvasOffset by remember { mutableStateOf(Offset.Zero) }
    var canvasRotationDeg by remember { mutableFloatStateOf(0f) }
    var canvasPxSize by remember { mutableStateOf(IntSize(1, 1)) }

    // Active stroke in progress
    var currentStroke by remember { mutableStateOf<StudioStroke?>(null) }

    // Modals & notifications
    var showBrushDialog by remember { mutableStateOf(false) }
    var showLayersDialog by remember { mutableStateOf(false) }
    var showProjectsDialog by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    fun pushMutation(mutated: StudioProject) {
        undoStack = (undoStack + project).takeLast(35)
        redoStack = emptyList()
        val stamped = mutated.copy(updatedAtMillis = System.currentTimeMillis())
        project = stamped
        onAutosaveProject(stamped)
    }

    fun updateActiveLayer(transform: (DrawingLayer) -> DrawingLayer) {
        val updatedLayers = project.layers.map {
            if (it.id == project.activeLayerId) transform(it) else it
        }
        pushMutation(project.copy(layers = updatedLayers))
    }

    // Convert screen pointer coordinates into normalized (0..1) canvas space, accounting for Zoom/Pan/Rotation
    fun screenToNorm(screenPos: Offset): Offset {
        val w = canvasPxSize.width.coerceAtLeast(1).toFloat()
        val h = canvasPxSize.height.coerceAtLeast(1).toFloat()
        val cx = w * 0.5f
        val cy = h * 0.5f
        val dx = (screenPos.x - cx - canvasOffset.x) / canvasScale
        val dy = (screenPos.y - cy - canvasOffset.y) / canvasScale
        val rad = Math.toRadians(-canvasRotationDeg.toDouble())
        val rx = (dx * cos(rad) - dy * sin(rad)).toFloat() + cx
        val ry = (dx * sin(rad) + dy * cos(rad)).toFloat() + cy
        return Offset((rx / w).coerceIn(0f, 1f), (ry / h).coerceIn(0f, 1f))
    }

    Scaffold(
        containerColor = VoidBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VoidSurface),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("studio_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = TextPrimary)
                    }
                },
                title = {
                    Column {
                        Text(
                            project.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        val activeLayer = project.layers.find { it.id == project.activeLayerId }
                        Text(
                            "${activeLayer?.name ?: ""} • ${(canvasScale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = NaviCyan
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (undoStack.isNotEmpty()) {
                                redoStack = redoStack + project
                                project = undoStack.last()
                                undoStack = undoStack.dropLast(1)
                            }
                        },
                        enabled = undoStack.isNotEmpty(),
                        modifier = Modifier.testTag("studio_undo_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = strings.undo, tint = if (undoStack.isNotEmpty()) TextPrimary else TextMuted)
                    }
                    IconButton(
                        onClick = {
                            if (redoStack.isNotEmpty()) {
                                undoStack = undoStack + project
                                project = redoStack.last()
                                redoStack = redoStack.dropLast(1)
                            }
                        },
                        enabled = redoStack.isNotEmpty(),
                        modifier = Modifier.testTag("studio_redo_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = strings.redo, tint = if (redoStack.isNotEmpty()) TextPrimary else TextMuted)
                    }
                    IconButton(
                        onClick = { showProjectsDialog = true },
                        modifier = Modifier.testTag("studio_projects_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = strings.studioProjects, tint = DeskAmber)
                    }
                    IconButton(
                        onClick = {
                            val res = StudioExporter.exportProjectToGallery(context, project)
                            snackbarMessage = if (res.success) {
                                "${strings.studioExportSuccess}: ${res.displayPath}"
                            } else {
                                "${strings.studioExportError}: ${res.errorMessage ?: ""}"
                            }
                        },
                        modifier = Modifier.testTag("studio_export_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = strings.studioExportPng, tint = HarmonyJade)
                    }
                    IconButton(
                        onClick = {
                            val saved = project.copy(updatedAtMillis = System.currentTimeMillis())
                            project = saved
                            onSaveProject(saved)
                            snackbarMessage = strings.sketchSavedToast
                        },
                        modifier = Modifier.testTag("studio_save_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = strings.save, tint = NaviCyan)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Optional status toast strip
            if (snackbarMessage != null) {
                Surface(
                    color = HarmonyJade.copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(snackbarMessage ?: "", style = MaterialTheme.typography.labelMedium, color = HarmonyJade, modifier = Modifier.weight(1f))
                        TextButton(onClick = { snackbarMessage = null }) { Text(strings.ok, color = TextPrimary) }
                    }
                }
            }

            // Secondary Transform / Crop quick bar when TRANSFORM or PAN_ZOOM is active
            if (activeTool == StudioTool.TRANSFORM || activeTool == StudioTool.PAN_ZOOM) {
                Surface(color = VoidSurfaceElevated, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (activeTool == StudioTool.PAN_ZOOM) {
                            AssistChip(
                                onClick = {
                                    canvasScale = 1f
                                    canvasOffset = Offset.Zero
                                    canvasRotationDeg = 0f
                                },
                                label = { Text(strings.studioResetView) },
                                leadingIcon = { Icon(Icons.Default.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            AssistChip(
                                onClick = { canvasScale = (canvasScale * 1.25f).coerceAtMost(4f) },
                                label = { Text("+25%") }
                            )
                            AssistChip(
                                onClick = { canvasScale = (canvasScale / 1.25f).coerceAtLeast(0.5f) },
                                label = { Text("-25%") }
                            )
                            AssistChip(
                                onClick = { canvasRotationDeg = (canvasRotationDeg + 15f) % 360f },
                                label = { Text("+15°") }
                            )
                        } else {
                            AssistChip(
                                onClick = { updateActiveLayer { it.copy(flipH = !it.flipH) } },
                                label = { Text(strings.studioFlipH) }
                            )
                            AssistChip(
                                onClick = { updateActiveLayer { it.copy(flipV = !it.flipV) } },
                                label = { Text(strings.studioFlipV) }
                            )
                            AssistChip(
                                onClick = { updateActiveLayer { it.copy(rotationDeg = (it.rotationDeg + 15f) % 360f) } },
                                label = { Text("${strings.rotate} +15°") }
                            )
                            AssistChip(
                                onClick = { updateActiveLayer { it.copy(offsetX = 0f, offsetY = 0f, rotationDeg = 0f, flipH = false, flipV = false) } },
                                label = { Text(strings.reset) }
                            )
                            val isCropped = project.cropRect != StudioCropRect()
                            AssistChip(
                                onClick = {
                                    val nextCrop = if (isCropped) StudioCropRect() else StudioCropRect(0.1f, 0.1f, 0.9f, 0.9f)
                                    pushMutation(project.copy(cropRect = nextCrop))
                                },
                                label = { Text(if (isCropped) strings.studioUncrop else strings.studioCropCenter) }
                            )
                        }
                    }
                }
            }

            // Main Interactive Drawing Viewport
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF070B12))
                    .clipToBounds()
                    .onSizeChanged { canvasPxSize = it }
            ) {
                val canvasModifier = when (activeTool) {
                    StudioTool.PAN_ZOOM -> Modifier.pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, rotation ->
                            canvasScale = (canvasScale * zoom).coerceIn(0.45f, 4.5f)
                            canvasOffset += pan
                            canvasRotationDeg = (canvasRotationDeg + rotation) % 360f
                        }
                    }
                    StudioTool.TRANSFORM -> Modifier.pointerInput(project.activeLayerId) {
                        detectTransformGestures { _, pan, _, rotation ->
                            val w = canvasPxSize.width.coerceAtLeast(1).toFloat()
                            val h = canvasPxSize.height.coerceAtLeast(1).toFloat()
                            val dx = pan.x / (w * canvasScale)
                            val dy = pan.y / (h * canvasScale)
                            val updatedLayers = project.layers.map {
                                if (it.id == project.activeLayerId) {
                                    it.copy(
                                        offsetX = (it.offsetX + dx).coerceIn(-0.8f, 0.8f),
                                        offsetY = (it.offsetY + dy).coerceIn(-0.8f, 0.8f),
                                        rotationDeg = (it.rotationDeg + rotation) % 360f
                                    )
                                } else it
                            }
                            project = project.copy(layers = updatedLayers)
                        }
                    }
                    else -> Modifier.pointerInput(activeTool, selectedBrush, selectedColorArgb, brushSize, brushOpacity, brushHardness, stabilization, project.activeLayerId) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startNorm = screenToNorm(down.position)
                            val pressure = down.pressure.takeIf { it > 0.01f }?.coerceIn(0.2f, 1.3f) ?: 1f

                            when (activeTool) {
                                StudioTool.BUCKET_FILL -> {
                                    updateActiveLayer { it.copy(fillColorArgb = selectedColorArgb) }
                                    down.consume()
                                    return@awaitEachGesture
                                }
                                StudioTool.EYEDROPPER -> {
                                    val sampled = StudioExporter.sampleCompositeColor(project, startNorm.x, startNorm.y)
                                    selectedColorArgb = sampled
                                    activeTool = StudioTool.BRUSH
                                    snackbarMessage = strings.studioColorPicked
                                    down.consume()
                                    return@awaitEachGesture
                                }
                                else -> Unit
                            }

                            val effectiveBrush = if (activeTool == StudioTool.ERASER) BrushType.ERASER else selectedBrush
                            var stroke = StudioStroke(
                                id = UUID.randomUUID().toString(),
                                points = listOf(StudioPoint(startNorm.x, startNorm.y, pressure)),
                                brushType = effectiveBrush,
                                colorArgb = selectedColorArgb,
                                size = brushSize,
                                opacity = brushOpacity,
                                hardness = brushHardness
                            )
                            currentStroke = stroke
                            var smoothX = startNorm.x
                            var smoothY = startNorm.y

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) break
                                val raw = screenToNorm(change.position)
                                val p = change.pressure.takeIf { it > 0.01f }?.coerceIn(0.2f, 1.3f) ?: 1f
                                // Real stroke stabilization filter
                                val alpha = (1f - stabilization).coerceIn(0.15f, 1f)
                                smoothX += (raw.x - smoothX) * alpha
                                smoothY += (raw.y - smoothY) * alpha
                                stroke = stroke.copy(points = stroke.points + StudioPoint(smoothX, smoothY, p))
                                currentStroke = stroke
                                change.consume()
                            }

                            val finished = currentStroke
                            currentStroke = null
                            if (finished != null) {
                                updateActiveLayer { layer ->
                                    layer.copy(strokes = layer.strokes + finished)
                                }
                            }
                        }
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(canvasModifier)
                        .testTag("studio_canvas")
                ) {
                    val w = size.width
                    val h = size.height
                    val cx = w * 0.5f
                    val cy = h * 0.5f

                    withTransform({
                        translate(left = canvasOffset.x, top = canvasOffset.y)
                        translate(left = cx, top = cy)
                        scale(scaleX = canvasScale, scaleY = canvasScale, pivot = Offset.Zero)
                        rotate(degrees = canvasRotationDeg, pivot = Offset.Zero)
                        translate(left = -cx, top = -cy)
                    }) {
                        // Canvas paper backdrop
                        drawRect(color = Color(project.backgroundColorArgb), size = Size(w, h))
                        drawRect(color = WiredBorder, size = Size(w, h), style = Stroke(width = 2f))

                        // Render every visible layer independently via saveLayer so Eraser is strictly layer-local!
                        drawIntoCanvas { nativeCanvas ->
                            val layerBounds = Rect(0f, 0f, w, h)
                            project.layers.forEach { layer ->
                                if (!layer.visible || layer.opacity <= 0.005f) return@forEach
                                val layerPaint = Paint().apply { alpha = layer.opacity.coerceIn(0f, 1f) }
                                nativeCanvas.saveLayer(layerBounds, layerPaint)

                                withTransform({
                                    translate(left = layer.offsetX * w, top = layer.offsetY * h)
                                    translate(left = cx, top = cy)
                                    if (layer.rotationDeg != 0f) rotate(degrees = layer.rotationDeg, pivot = Offset.Zero)
                                    scale(if (layer.flipH) -1f else 1f, if (layer.flipV) -1f else 1f, pivot = Offset.Zero)
                                    translate(left = -cx, top = -cy)
                                }) {
                                    layer.fillColorArgb?.let { drawRect(Color(it), size = Size(w, h)) }
                                    layer.strokes.forEach { st -> drawStudioStrokeCompose(st, w, h) }
                                    if (layer.id == project.activeLayerId) {
                                        currentStroke?.let { live -> drawStudioStrokeCompose(live, w, h) }
                                    }
                                }
                                nativeCanvas.restore()
                            }
                        }

                        // Crop overlay guide if active
                        val c = project.cropRect
                        if (c != StudioCropRect()) {
                            drawRect(
                                color = DeskAmber,
                                topLeft = Offset(c.leftFrac * w, c.topFrac * h),
                                size = Size((c.rightFrac - c.leftFrac) * w, (c.bottomFrac - c.topFrac) * h),
                                style = Stroke(
                                    width = 2.5f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                                )
                            )
                        }
                    }
                }
            }

            // Bottom Studio Tool Dock (Touch targets >= 48dp)
            Surface(color = VoidSurface, tonalElevation = 6.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Color & Brush Button
                    Surface(
                        color = VoidSurfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, NaviCyan),
                        modifier = Modifier
                            .height(48.dp)
                            .clickable { showBrushDialog = true }
                            .testTag("studio_brush_color_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(selectedColorArgb))
                                    .border(1.5.dp, Color.White, CircleShape)
                            )
                            Text(strings.brushName(selectedBrush), style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                        }
                    }

                    // Tool buttons
                    val tools = listOf(
                        Triple(StudioTool.BRUSH, Icons.Default.Brush, strings.studioToolBrush),
                        Triple(StudioTool.ERASER, Icons.Default.AutoFixNormal, strings.eraser),
                        Triple(StudioTool.BUCKET_FILL, Icons.Default.FormatColorFill, strings.studioToolFill),
                        Triple(StudioTool.EYEDROPPER, Icons.Default.Colorize, strings.studioToolEyedropper),
                        Triple(StudioTool.PAN_ZOOM, Icons.Default.ZoomIn, strings.studioToolPanZoom),
                        Triple(StudioTool.TRANSFORM, Icons.Default.Transform, strings.studioToolTransform)
                    )
                    tools.forEach { (tool, icon, label) ->
                        val selected = activeTool == tool
                        FilterChip(
                            selected = selected,
                            onClick = { activeTool = tool },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("studio_tool_${tool.name.lowercase()}")
                        )
                    }

                    // Independent Layer Manager Button
                    FilledTonalButton(
                        onClick = { showLayersDialog = true },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("studio_layers_button")
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = strings.studioLayers, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${strings.studioLayers} (${project.layers.size})")
                    }
                }
            }
        }
    }

    if (showBrushDialog) {
        BrushAndColorDialog(
            strings = strings,
            selectedBrush = selectedBrush,
            selectedColorArgb = selectedColorArgb,
            brushSize = brushSize,
            brushOpacity = brushOpacity,
            brushHardness = brushHardness,
            stabilization = stabilization,
            onBrushChanged = {
                selectedBrush = it
                activeTool = StudioTool.BRUSH
            },
            onColorChanged = { selectedColorArgb = it },
            onSizeChanged = { brushSize = it },
            onOpacityChanged = { brushOpacity = it },
            onHardnessChanged = { brushHardness = it },
            onStabilizationChanged = { stabilization = it },
            onDismiss = { showBrushDialog = false }
        )
    }

    if (showLayersDialog) {
        LayersManagerDialog(
            strings = strings,
            project = project,
            onSelectLayer = { id -> project = project.copy(activeLayerId = id) },
            onAddLayer = {
                val nl = DrawingLayer(
                    id = "layer_${UUID.randomUUID()}",
                    name = "${strings.studioLayer} ${project.layers.size + 1}"
                )
                pushMutation(project.copy(layers = project.layers + nl, activeLayerId = nl.id))
            },
            onDuplicateLayer = { id ->
                val src = project.layers.find { it.id == id } ?: return@LayersManagerDialog
                val dup = src.copy(id = "layer_${UUID.randomUUID()}", name = "${src.name} Copy")
                val idx = project.layers.indexOfFirst { it.id == id }
                val updated = project.layers.toMutableList().apply { add(idx + 1, dup) }
                pushMutation(project.copy(layers = updated, activeLayerId = dup.id))
            },
            onDeleteLayer = { id ->
                if (project.layers.size <= 1) return@LayersManagerDialog
                val remaining = project.layers.filterNot { it.id == id }
                val nextActive = if (project.activeLayerId == id) remaining.last().id else project.activeLayerId
                pushMutation(project.copy(layers = remaining, activeLayerId = nextActive))
            },
            onRenameLayer = { id, name ->
                pushMutation(project.copy(layers = project.layers.map { if (it.id == id) it.copy(name = name) else it }))
            },
            onToggleVisibility = { id ->
                pushMutation(project.copy(layers = project.layers.map { if (it.id == id) it.copy(visible = !it.visible) else it }))
            },
            onOpacityChange = { id, op ->
                project = project.copy(layers = project.layers.map { if (it.id == id) it.copy(opacity = op) else it })
            },
            onMoveLayer = { id, dir ->
                val idx = project.layers.indexOfFirst { it.id == id }
                val target = idx + dir
                if (idx in project.layers.indices && target in project.layers.indices) {
                    val list = project.layers.toMutableList()
                    val item = list.removeAt(idx)
                    list.add(target, item)
                    pushMutation(project.copy(layers = list))
                }
            },
            onMergeDown = { id ->
                val idx = project.layers.indexOfFirst { it.id == id }
                if (idx > 0) {
                    val top = project.layers[idx]
                    val below = project.layers[idx - 1]
                    val merged = below.copy(
                        fillColorArgb = top.fillColorArgb ?: below.fillColorArgb,
                        strokes = below.strokes + top.strokes.map { it.copy(opacity = it.opacity * top.opacity) }
                    )
                    val list = project.layers.toMutableList()
                    list[idx - 1] = merged
                    list.removeAt(idx)
                    pushMutation(project.copy(layers = list, activeLayerId = merged.id))
                }
            },
            onDismiss = { showLayersDialog = false }
        )
    }

    if (showProjectsDialog) {
        StudioProjectsDialog(
            strings = strings,
            projects = savedProjects,
            autosaveDraft = autosaveDraft,
            onCreateNew = {
                project = createFreshProject()
                undoStack = emptyList()
                redoStack = emptyList()
            },
            onRecoverDraft = { draft ->
                project = draft
                undoStack = emptyList()
                redoStack = emptyList()
            },
            onOpen = { chosen ->
                project = chosen
                undoStack = emptyList()
                redoStack = emptyList()
            },
            onRename = { target, newName ->
                val updated = target.copy(title = newName, updatedAtMillis = System.currentTimeMillis())
                onSaveProject(updated)
                if (project.id == target.id) project = updated
            },
            onDuplicate = onDuplicateProject,
            onDelete = { id ->
                onDeleteProject(id)
                if (project.id == id) {
                    project = savedProjects.firstOrNull { it.id != id } ?: createFreshProject()
                }
            },
            onDismiss = { showProjectsDialog = false }
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStudioStrokeCompose(
    stroke: StudioStroke,
    w: Float,
    h: Float
) {
    if (stroke.points.isEmpty()) return
    val avgPressure = stroke.points.map { it.pressure }.average().toFloat().coerceIn(0.25f, 1.35f)
    val widthPx = (stroke.size * (0.65f + 0.5f * avgPressure)).coerceAtLeast(1.5f)
    val isEraser = stroke.brushType == BrushType.ERASER
    val blend = if (isEraser) BlendMode.Clear else BlendMode.SrcOver
    val brushAlphaFactor = when (stroke.brushType) {
        BrushType.PENCIL -> 0.78f
        BrushType.PEN -> 1.0f
        BrushType.SOFT_BRUSH -> 0.62f
        BrushType.HARD_BRUSH -> 0.96f
        BrushType.AIRBRUSH -> 0.30f
        BrushType.MARKER -> 0.58f
        BrushType.WATERCOLOR -> 0.42f
        BrushType.ERASER -> 1.0f
    }
    val drawColor = if (isEraser) Color.Transparent else Color(stroke.colorArgb).copy(
        alpha = (stroke.opacity * brushAlphaFactor).coerceIn(0.05f, 1f)
    )

    if (stroke.points.size == 1) {
        val pt = stroke.points.first()
        drawCircle(
            color = drawColor,
            radius = widthPx * 0.5f,
            center = Offset(pt.x * w, pt.y * h),
            blendMode = blend
        )
        return
    }

    val path = Path().apply {
        val first = stroke.points.first()
        moveTo(first.x * w, first.y * h)
        for (i in 1 until stroke.points.size) {
            val prev = stroke.points[i - 1]
            val curr = stroke.points[i]
            val mx = (prev.x + curr.x) * 0.5f * w
            val my = (prev.y + curr.y) * 0.5f * h
            quadraticTo(prev.x * w, prev.y * h, mx, my)
        }
        val last = stroke.points.last()
        lineTo(last.x * w, last.y * h)
    }

    // Soft halo pass for soft brush, airbrush, and watercolor
    if (!isEraser && (stroke.brushType == BrushType.AIRBRUSH || stroke.brushType == BrushType.SOFT_BRUSH || stroke.brushType == BrushType.WATERCOLOR)) {
        drawPath(
            path = path,
            color = drawColor.copy(alpha = drawColor.alpha * 0.38f),
            style = Stroke(width = widthPx * 1.65f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
    drawPath(
        path = path,
        color = drawColor,
        style = Stroke(
            width = widthPx,
            cap = if (stroke.brushType == BrushType.MARKER) StrokeCap.Square else StrokeCap.Round,
            join = StrokeJoin.Round
        ),
        blendMode = blend
    )
}
