package com.example.sketch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.i18n.AppStrings
import com.example.ui.theme.*
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Modular Drawing Studio modals:
 * - Brush & Interactive HSV Color Wheel dialog
 * - Independent Multi-Layer Manager sheet
 * - Project Gallery & Autosave Recovery dialog
 */
@Composable
fun BrushAndColorDialog(
    strings: AppStrings,
    selectedBrush: BrushType,
    selectedColorArgb: Long,
    brushSize: Float,
    brushOpacity: Float,
    brushHardness: Float,
    stabilization: Float,
    onBrushChanged: (BrushType) -> Unit,
    onColorChanged: (Long) -> Unit,
    onSizeChanged: (Float) -> Unit,
    onOpacityChanged: (Float) -> Unit,
    onHardnessChanged: (Float) -> Unit,
    onStabilizationChanged: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHsv = remember(selectedColorArgb) {
        val arr = FloatArray(3)
        android.graphics.Color.colorToHSV(selectedColorArgb.toInt(), arr)
        arr
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2].coerceAtLeast(0.15f)) }

    fun pushColor(h: Float, s: Float, v: Float) {
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
        onColorChanged((argb.toLong() and 0xFFFFFFFFL) or 0xFF000000L)
    }

    val presets = listOf(
        0xFF64F0FFL, 0xFF5EEAD4L, 0xFFA78BFAL, 0xFFF26A8DL,
        0xFFF6BD60L, 0xFFF8FAFC, 0xFFD9C7B8L, 0xFF424854L,
        0xFF2B181CL, 0xFF141822L
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VoidSurfaceElevated,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.studioBrushAndColor, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(selectedColorArgb))
                        .border(2.dp, Color.White, CircleShape)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 8 Brush selector strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BrushType.entries.filter { it != BrushType.ERASER }.forEach { bt ->
                        val active = bt == selectedBrush
                        FilterChip(
                            selected = active,
                            onClick = { onBrushChanged(bt) },
                            label = { Text(strings.brushName(bt), style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("brush_chip_${bt.name.lowercase()}")
                        )
                    }
                }

                // Interactive HSV Color Wheel + Quick Swatches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(116.dp)
                            .clip(CircleShape)
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val cx = size.width * 0.5f
                                    val cy = size.height * 0.5f
                                    val r = minOf(cx, cy).coerceAtLeast(1f)
                                    val dx = offset.x - cx
                                    val dy = offset.y - cy
                                    val dist = (hypot(dx, dy) / r).coerceIn(0f, 1f)
                                    val deg = ((atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI) + 360.0) % 360.0
                                    hue = deg.toFloat()
                                    sat = dist
                                    pushColor(hue, sat, value)
                                }
                            }
                    ) {
                        val cx = size.width * 0.5f
                        val cy = size.height * 0.5f
                        val r = minOf(cx, cy)
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color.Red, Color.Yellow, Color.Green,
                                    Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                ),
                                center = Offset(cx, cy)
                            ),
                            radius = r,
                            center = Offset(cx, cy)
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Color.White, Color.Transparent),
                                center = Offset(cx, cy),
                                radius = r
                            ),
                            radius = r,
                            center = Offset(cx, cy)
                        )
                        // Active indicator dot
                        val rad = Math.toRadians(hue.toDouble())
                        val ix = cx + cos(rad).toFloat() * sat * r
                        val iy = cy + sin(rad).toFloat() * sat * r
                        drawCircle(Color.Black, 7f, Offset(ix, iy))
                        drawCircle(Color.White, 5f, Offset(ix, iy), style = Stroke(2f))
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${strings.studioBrightness}: ${(value * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Slider(
                            value = value,
                            onValueChange = {
                                value = it
                                pushColor(hue, sat, value)
                            },
                            valueRange = 0.05f..1f
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presets.forEach { col ->
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color(col))
                                        .border(
                                            if (col == selectedColorArgb) 2.dp else 1.dp,
                                            if (col == selectedColorArgb) NaviCyan else WiredBorder,
                                            CircleShape
                                        )
                                        .clickable {
                                            val arr = FloatArray(3)
                                            android.graphics.Color.colorToHSV(col.toInt(), arr)
                                            hue = arr[0]
                                            sat = arr[1]
                                            value = arr[2]
                                            onColorChanged(col)
                                        }
                                )
                            }
                        }
                    }
                }

                // Size / Opacity / Hardness / Stabilization
                Text("${strings.studioBrushSize}: ${brushSize.toInt()} px", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Slider(value = brushSize, onValueChange = onSizeChanged, valueRange = 2f..46f)

                Text("${strings.studioOpacity}: ${(brushOpacity * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Slider(value = brushOpacity, onValueChange = onOpacityChanged, valueRange = 0.1f..1f)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${strings.studioHardness}: ${(brushHardness * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Slider(value = brushHardness, onValueChange = onHardnessChanged, valueRange = 0.1f..1f)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${strings.studioStabilizer}: ${(stabilization * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Slider(value = stabilization, onValueChange = onStabilizationChanged, valueRange = 0f..0.85f)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(strings.done)
            }
        }
    )
}

@Composable
fun LayersManagerDialog(
    strings: AppStrings,
    project: StudioProject,
    onSelectLayer: (String) -> Unit,
    onAddLayer: () -> Unit,
    onDuplicateLayer: (String) -> Unit,
    onDeleteLayer: (String) -> Unit,
    onRenameLayer: (String, String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onOpacityChange: (String, Float) -> Unit,
    onMoveLayer: (String, Int) -> Unit, // +1 up, -1 down
    onMergeDown: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var renamingLayerId by remember { mutableStateOf<String?>(null) }
    var renameDraft by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VoidSurfaceElevated,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${strings.studioLayers} (${project.layers.size})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                FilledTonalButton(
                    onClick = onAddLayer,
                    modifier = Modifier.testTag("studio_add_layer_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.studioAddLayer)
                }
            }
        },
        text = {
            // Topmost layer shown first (standard digital art convention)
            val displayLayers = remember(project.layers) { project.layers.reversed() }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 330.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(displayLayers, key = { _, l -> l.id }) { revIdx, layer ->
                    val actualIndex = project.layers.lastIndex - revIdx
                    val isActive = layer.id == project.activeLayerId
                    Surface(
                        color = if (isActive) NaviCyan.copy(alpha = 0.14f) else VoidSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (isActive) NaviCyan else WiredBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectLayer(layer.id) }
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Live miniature layer preview
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(project.backgroundColorArgb))
                                        .border(1.dp, WiredBorder, RoundedCornerShape(6.dp))
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        layer.fillColorArgb?.let { drawRect(Color(it)) }
                                        layer.strokes.forEach { st ->
                                            if (st.brushType != BrushType.ERASER && st.points.size >= 2) {
                                                val p = Path().apply {
                                                    moveTo(st.points.first().x * size.width, st.points.first().y * size.height)
                                                    st.points.drop(1).forEach { pt ->
                                                        lineTo(pt.x * size.width, pt.y * size.height)
                                                    }
                                                }
                                                drawPath(
                                                    p,
                                                    color = Color(st.colorArgb),
                                                    style = Stroke(width = (st.size * 0.15f).coerceAtLeast(1.2f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                                )
                                            }
                                        }
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        layer.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        "${(layer.opacity * 100).toInt()}% • ${layer.strokes.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }

                                IconButton(
                                    onClick = { onToggleVisibility(layer.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = strings.studioToggleVisibility,
                                        tint = if (layer.visible) NaviCyan else TextMuted
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        renamingLayerId = layer.id
                                        renameDraft = layer.name
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = strings.rename, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }

                            // Opacity + Reorder / Duplicate / Merge / Delete controls for active layer
                            if (isActive) {
                                Slider(
                                    value = layer.opacity,
                                    onValueChange = { onOpacityChange(layer.id, it) },
                                    valueRange = 0.05f..1f,
                                    modifier = Modifier.height(28.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        IconButton(
                                            onClick = { onMoveLayer(layer.id, +1) },
                                            enabled = actualIndex < project.layers.lastIndex,
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = strings.studioLayerUp, modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(
                                            onClick = { onMoveLayer(layer.id, -1) },
                                            enabled = actualIndex > 0,
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.ArrowDownward, contentDescription = strings.studioLayerDown, modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(
                                            onClick = { onDuplicateLayer(layer.id) },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = strings.duplicate, modifier = Modifier.size(17.dp))
                                        }
                                        IconButton(
                                            onClick = { onMergeDown(layer.id) },
                                            enabled = actualIndex > 0,
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(Icons.Default.MergeType, contentDescription = strings.studioMergeDown, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    IconButton(
                                        onClick = { onDeleteLayer(layer.id) },
                                        enabled = project.layers.size > 1,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = strings.delete, tint = SignalCoral, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(strings.done) }
        }
    )

    if (renamingLayerId != null) {
        AlertDialog(
            onDismissRequest = { renamingLayerId = null },
            containerColor = VoidSurfaceElevated,
            title = { Text(strings.rename, color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val id = renamingLayerId
                    if (id != null && renameDraft.isNotBlank()) {
                        onRenameLayer(id, renameDraft.trim())
                    }
                    renamingLayerId = null
                }) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { renamingLayerId = null }) { Text(strings.cancel) }
            }
        )
    }
}

@Composable
fun StudioProjectsDialog(
    strings: AppStrings,
    projects: List<StudioProject>,
    autosaveDraft: StudioProject?,
    onCreateNew: () -> Unit,
    onRecoverDraft: (StudioProject) -> Unit,
    onOpen: (StudioProject) -> Unit,
    onRename: (StudioProject, String) -> Unit,
    onDuplicate: (StudioProject) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var renameTarget by remember { mutableStateOf<StudioProject?>(null) }
    var renameText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VoidSurfaceElevated,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.studioProjects, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                FilledTonalButton(
                    onClick = {
                        onCreateNew()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("studio_new_project_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.newSketch)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (autosaveDraft != null) {
                    Surface(
                        color = DeskAmber.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DeskAmber)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.studioRecoverDraft, style = MaterialTheme.typography.labelLarge, color = DeskAmber)
                                Text(autosaveDraft.title, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                            }
                            TextButton(onClick = {
                                onRecoverDraft(autosaveDraft)
                                onDismiss()
                            }) {
                                Text(strings.open)
                            }
                        }
                    }
                }

                if (projects.isEmpty()) {
                    Text(strings.noSavedSketches, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(projects, key = { _, p -> p.id }) { _, proj ->
                            Surface(
                                color = VoidSurface,
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, WiredBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onOpen(proj)
                                            onDismiss()
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(proj.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                        Text(
                                            "${proj.layers.size} ${strings.studioLayers} • ${proj.layers.sumOf { it.strokes.size }} ${strings.strokes}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                    IconButton(onClick = {
                                        renameTarget = proj
                                        renameText = proj.title
                                    }, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Default.Edit, contentDescription = strings.rename, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { onDuplicate(proj) }, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = strings.duplicate, tint = NaviCyan, modifier = Modifier.size(17.dp))
                                    }
                                    IconButton(onClick = { onDelete(proj.id) }, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = strings.delete, tint = SignalCoral, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.close) }
        }
    )

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = VoidSurfaceElevated,
            title = { Text(strings.rename, color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val target = renameTarget
                    if (target != null && renameText.isNotBlank()) {
                        onRename(target, renameText.trim())
                    }
                    renameTarget = null
                }) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(strings.cancel) }
            }
        )
    }
}
