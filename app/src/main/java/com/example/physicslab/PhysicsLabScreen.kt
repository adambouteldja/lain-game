package com.example.physicslab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.i18n.LocalStrings
import com.example.sandbox.SandboxPreset
import com.example.ui.theme.*

private enum class LabDetailTab {
    LESSONS,
    FORMULAS,
    EXERCISES,
    QUIZ
}

/**
 * Independent Educational University Physics Lab Screen (Evolution 2.0):
 * Structured for Algerian University Sciences de la Matière (SM) Year 1 (L1) & Year 2 (L2),
 * ready for user-provided official course PDFs/JSON packs, with Formula Book, Exercises,
 * Quizzes with persistent completion tracking, and direct links to live 2D Sandbox simulations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhysicsLabScreen(
    isArabic: Boolean,
    completedQuizIds: Set<String>,
    onQuizCompleted: (String) -> Unit,
    onLaunchSandboxPreset: (SandboxPreset) -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalStrings.current
    var modules by remember { mutableStateOf(PhysicsLabCatalog.defaultModules) }
    var selectedYear by remember { mutableStateOf(AcademicYear.YEAR_1_L1) }
    var selectedModuleId by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableStateOf(LabDetailTab.LESSONS) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember {
        mutableStateOf("""{"title":"L1 SM Chapitre 1 — Cinématique","summary":"Notes importées depuis le PDF officiel universitaire."}""")
    }
    var importFeedback by remember { mutableStateOf<String?>(null) }

    val selectedModule = modules.find { it.id == selectedModuleId }

    BackHandler {
        if (selectedModule != null) {
            selectedModuleId = null
        } else {
            onBack()
        }
    }

    Scaffold(
        containerColor = VoidBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VoidSurface),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedModule != null) selectedModuleId = null else onBack()
                        },
                        modifier = Modifier.testTag("lab_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = TextPrimary)
                    }
                },
                title = {
                    Column {
                        Text(
                            selectedModule?.let { if (isArabic) it.titleAr else it.titleEn } ?: strings.physicsLabTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = WiredViolet,
                            maxLines = 1
                        )
                        Text(
                            strings.physicsLabSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                },
                actions = {
                    if (selectedModule != null) {
                        IconButton(
                            onClick = { showImportDialog = true },
                            modifier = Modifier.testTag("lab_import_pdf_button")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = strings.labImportPack, tint = NaviCyan)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (selectedModule == null) {
            // Year 1 (L1) / Year 2 (L2) Module Directory
            val filtered = modules.filter { it.year == selectedYear }
            val totalQuizzes = modules.sumOf { it.quiz.size }.coerceAtLeast(1)
            val completedCount = modules.flatMap { it.quiz }.count { it.id in completedQuizIds }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Honest Curriculum Provenance Banner
                Surface(
                    color = DeskAmber.copy(alpha = 0.13f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DeskAmber.copy(alpha = 0.65f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = DeskAmber)
                        Text(
                            strings.labHonestyNotice,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }

                // Year 1 / Year 2 Segmented Selector + Progress
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedYear == AcademicYear.YEAR_1_L1,
                        onClick = { selectedYear = AcademicYear.YEAR_1_L1 },
                        label = { Text(strings.labYear1) },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("lab_year1_chip")
                    )
                    FilterChip(
                        selected = selectedYear == AcademicYear.YEAR_2_L2,
                        onClick = { selectedYear = AcademicYear.YEAR_2_L2 },
                        label = { Text(strings.labYear2) },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("lab_year2_chip")
                    )
                }

                // Progress indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(strings.labQuizProgress, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Text("$completedCount / $totalQuizzes", style = MaterialTheme.typography.labelMedium, color = HarmonyJade)
                }
                LinearProgressIndicator(
                    progress = { completedCount.toFloat() / totalQuizzes.toFloat() },
                    color = HarmonyJade,
                    trackColor = VoidSurfaceElevated,
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered, key = { it.id }) { mod ->
                        val isImported = mod.sourceStatus == CurriculumSourceStatus.OFFICIAL_PDF_IMPORTED
                        Surface(
                            color = VoidSurface,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, WiredBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedModuleId = mod.id
                                    activeTab = LabDetailTab.LESSONS
                                }
                                .testTag("lab_module_${mod.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${mod.code} • ${mod.semester}", style = MaterialTheme.typography.labelSmall, color = NaviCyan)
                                    Surface(
                                        color = if (isImported) HarmonyJade.copy(alpha = 0.2f) else DeskAmber.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            if (isImported) strings.labSourceImported else strings.labSourceAwaitingPdf,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isImported) HarmonyJade else DeskAmber,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    if (isArabic) mod.titleAr else mod.titleEn,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${mod.lessons.size} ${strings.labLessons} • ${mod.formulas.size} ${strings.labFormulas} • ${mod.exercises.size} ${strings.labExercises}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Module Detail View: Lessons / Formula Book / Exercises / Quizzes + Sandbox Simulation Launch
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Direct Simulation Link Bar
                Surface(color = VoidSurfaceElevated, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (selectedModule.sourceStatus == CurriculumSourceStatus.OFFICIAL_PDF_IMPORTED)
                                strings.labSourceImported else strings.labSourceAwaitingPdf,
                            style = MaterialTheme.typography.labelSmall,
                            color = DeskAmber
                        )
                        FilledTonalButton(
                            onClick = { onLaunchSandboxPreset(selectedModule.linkedSandboxPreset) },
                            modifier = Modifier.height(42.dp).testTag("lab_run_sim_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.labRunSimulation)
                        }
                    }
                }

                // 4 Educational Tabs
                TabRow(selectedTabIndex = activeTab.ordinal, containerColor = VoidSurface) {
                    Tab(selected = activeTab == LabDetailTab.LESSONS, onClick = { activeTab = LabDetailTab.LESSONS }, text = { Text(strings.labLessons) })
                    Tab(selected = activeTab == LabDetailTab.FORMULAS, onClick = { activeTab = LabDetailTab.FORMULAS }, text = { Text(strings.labFormulas) })
                    Tab(selected = activeTab == LabDetailTab.EXERCISES, onClick = { activeTab = LabDetailTab.EXERCISES }, text = { Text(strings.labExercises) })
                    Tab(selected = activeTab == LabDetailTab.QUIZ, onClick = { activeTab = LabDetailTab.QUIZ }, text = { Text(strings.labQuiz) })
                }

                if (importFeedback != null) {
                    Text(
                        importFeedback ?: "",
                        color = HarmonyJade,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (activeTab) {
                        LabDetailTab.LESSONS -> {
                            selectedModule.lessons.forEach { lesson ->
                                Surface(
                                    color = VoidSurface,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, WiredBorder)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            if (lesson.sourceStatus == CurriculumSourceStatus.OFFICIAL_PDF_IMPORTED)
                                                strings.labSourceImported else strings.labSourceAwaitingPdf,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (lesson.sourceStatus == CurriculumSourceStatus.OFFICIAL_PDF_IMPORTED) HarmonyJade else DeskAmber
                                        )
                                        Text(if (isArabic) lesson.titleAr else lesson.titleEn, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                        Text(if (isArabic) lesson.summaryAr else lesson.summaryEn, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                    }
                                }
                            }
                        }

                        LabDetailTab.FORMULAS -> {
                            selectedModule.formulas.forEach { f ->
                                Surface(
                                    color = VoidSurface,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NaviCyan.copy(alpha = 0.45f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(f.expression, style = MaterialTheme.typography.titleMedium, color = NaviCyan, fontWeight = FontWeight.Bold)
                                            Text(f.unit, style = MaterialTheme.typography.labelSmall, color = HarmonyJade)
                                        }
                                        Text(if (isArabic) f.noteAr else f.noteEn, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    }
                                }
                            }
                        }

                        LabDetailTab.EXERCISES -> {
                            selectedModule.exercises.forEach { ex ->
                                var reveal by remember(ex.id) { mutableStateOf(false) }
                                Surface(
                                    color = VoidSurface,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, WiredBorder)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            if (ex.origin == ExerciseOrigin.SOURCE_PDF) strings.labOriginPdf else strings.labOriginPractice,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = WiredViolet
                                        )
                                        Text(if (isArabic) ex.promptAr else ex.promptEn, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                        OutlinedButton(onClick = { reveal = !reveal }) {
                                            Text(if (reveal) strings.labHideSolution else strings.labShowSolution)
                                        }
                                        if (reveal) {
                                            Text(if (isArabic) ex.solutionAr else ex.solutionEn, style = MaterialTheme.typography.bodySmall, color = HarmonyJade)
                                        }
                                    }
                                }
                            }
                        }

                        LabDetailTab.QUIZ -> {
                            selectedModule.quiz.forEach { q ->
                                var chosenIdx by remember(q.id) { mutableStateOf<Int?>(null) }
                                val alreadyPassed = q.id in completedQuizIds
                                Surface(
                                    color = VoidSurface,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (alreadyPassed) HarmonyJade else WiredBorder)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(if (isArabic) q.questionAr else q.questionEn, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                        val opts = if (isArabic) q.optionsAr else q.optionsEn
                                        opts.forEachIndexed { idx, opt ->
                                            val isSelected = chosenIdx == idx
                                            val isCorrect = idx == q.correctIndex
                                            val bg = when {
                                                chosenIdx != null && isCorrect -> HarmonyJade.copy(alpha = 0.22f)
                                                isSelected && !isCorrect -> SignalCoral.copy(alpha = 0.22f)
                                                else -> VoidSurfaceElevated
                                            }
                                            Surface(
                                                color = bg,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        chosenIdx = idx
                                                        if (idx == q.correctIndex) onQuizCompleted(q.id)
                                                    }
                                            ) {
                                                Text(opt, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.padding(12.dp))
                                            }
                                        }
                                        if (chosenIdx != null) {
                                            Text(
                                                if (isArabic) q.explanationAr else q.explanationEn,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (chosenIdx == q.correctIndex) HarmonyJade else DeskAmber
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showImportDialog && selectedModule != null) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = VoidSurfaceElevated,
            title = { Text(strings.labImportPack, color = NaviCyan) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.labImportHint, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = { importJsonText = it },
                        minLines = 4,
                        maxLines = 6
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val parsed = PhysicsLabCatalog.parseImportedCourseJson(importJsonText)
                    if (parsed != null) {
                        modules = modules.map { m ->
                            if (m.id == selectedModule.id) {
                                m.copy(
                                    sourceStatus = CurriculumSourceStatus.OFFICIAL_PDF_IMPORTED,
                                    lessons = listOf(parsed) + m.lessons
                                )
                            } else m
                        }
                        importFeedback = strings.labImportSuccess
                        showImportDialog = false
                    } else {
                        importFeedback = strings.labImportInvalid
                    }
                }) { Text(strings.labImportConfirm) }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text(strings.cancel) }
            }
        )
    }
}
