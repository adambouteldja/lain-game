package com.example.desktop

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.DesktopThemeTint
import com.example.data.GameSettings
import com.example.data.LainOutfit
import com.example.game.model.LainExpression
import com.example.game.render.LainSpriteRenderer.drawLainPortrait
import com.example.i18n.LocalStrings
import com.example.ui.theme.*

private enum class DesktopWindow {
    NONE,
    FILES,
    MESSAGES,
    SYSTEM
}

private data class DesktopMessage(
    val id: String,
    val senderEn: String,
    val senderAr: String,
    val subjectEn: String,
    val subjectAr: String,
    val bodyEn: String,
    val bodyAr: String,
    val isCanon: Boolean
)

private data class DesktopArchiveFile(
    val id: String,
    val fileName: String,
    val isCanon: Boolean,
    val titleEn: String,
    val titleAr: String,
    val contentEn: String,
    val contentAr: String
)

/**
 * Interactive LAIN Navi OS Desktop (Evolution 2.0):
 * Accessible from the Main Menu and directly from Lain's Desk in Level 1-1.
 * Every icon opens a functional module: Files Archive, Messages Inbox, Drawing Studio,
 * University SM Physics Lab, Physics Sandbox, and System / Wardrobe Customization.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LainDesktopScreen(
    settings: GameSettings,
    readMessageIds: Set<String>,
    onMarkMessageRead: (String) -> Unit,
    onUpdateSettings: (GameSettings) -> Unit,
    onOpenStudio: () -> Unit,
    onOpenSandbox: () -> Unit,
    onOpenPhysicsLab: () -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalStrings.current
    val isArabic = settings.languageCode == "ar"
    var activeWindow by remember { mutableStateOf(DesktopWindow.NONE) }
    var selectedMessageId by remember { mutableStateOf<String?>(null) }
    var selectedFileId by remember { mutableStateOf<String?>(null) }

    BackHandler {
        if (activeWindow != DesktopWindow.NONE) {
            activeWindow = DesktopWindow.NONE
        } else {
            onBack()
        }
    }

    val accent = when (settings.desktopTheme) {
        DesktopThemeTint.NAVI_CYAN -> NaviCyan
        DesktopThemeTint.CYBERIA_MAGENTA -> Color(0xFFE072F0)
        DesktopThemeTint.AMBER_CRT -> DeskAmber
    }

    val infinite = rememberInfiniteTransition(label = "desktop_time")
    val animTime by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing)),
        label = "dt"
    )

    val messages = remember {
        listOf(
            DesktopMessage(
                id = "msg_chisa_01",
                senderEn = "Chisa Yomoda",
                senderAr = "تشيسا يومودا",
                subjectEn = "Re: Why I left my body behind",
                subjectAr = "رد: لماذا تركت جسدي خلفي",
                bodyEn = "Lain, it did not hurt. You do not need a body inside the Wired. God is here—can you hear the hum of the powerlines tonight?",
                bodyAr = "لاين، لم يكن الأمر مؤلماً. لا تحتاجين إلى جسد داخل الشبكة (Wired). هل تسمعين همهمة أسلاك الكهرباء هذه الليلة؟",
                isCanon = true
            ),
            DesktopMessage(
                id = "msg_alice_02",
                senderEn = "Alice Mizuki",
                senderAr = "أليس ميزوكي",
                subjectEn = "Are you coming to Cyberia?",
                subjectAr = "هل ستأتين إلى سايبيـريا؟",
                bodyEn = "Reika and Juri said they saw someone who looked just like you at Cyberia last night. Let's walk home together after class, okay?",
                bodyAr = "قالت ريكا وجوري إنهما رأتا فتاة تشبهك تماماً في نادي سايبيريا الليلة الماضية. لنعد معاً بعد المدرسة، حسناً؟",
                isCanon = true
            ),
            DesktopMessage(
                id = "msg_yasuo_03",
                senderEn = "Yasuo Iwakura",
                senderAr = "ياسو إواكورا",
                subjectEn = "Navi Cooling & Hardware Manual",
                subjectAr = "دليل التبريد وترقية جهاز Navi",
                bodyEn = "I left the new Psyche processor documentation near your desk. Remember: the Wired is only a medium for communication—do not confuse it with the real world.",
                bodyAr = "تركت لكِ كتيب ترقية جهاز Navi بجانب مكتبك. تذكري أن الشبكة مجرد وسيلة للتواصل، لا تخلطي بينها وبين الواقع.",
                isCanon = true
            ),
            DesktopMessage(
                id = "msg_knights_04",
                senderEn = "Knights // Protocol 7",
                senderAr = "الفرسان // البروتوكول 7",
                subjectEn = "Layer 07 Boundary Resonance",
                subjectAr = "رنين الحدود — الطبقة 07",
                bodyEn = "[ORIGINAL GAME LOG] Physical anomalies detected across Gravity, Creation, Energy, and Balance sectors. Use the Physics Sandbox and SM Lab to analyze the laws before crossing.",
                bodyAr = "[سجل أصلي للعبة] تم رصد شذوذ فيزيائي في قطاعات الجاذبية والإبداع والطاقة والتوازن. استخدمي مختبر الفيزياء ومحاكاة التجارب لتحليل القوانين.",
                isCanon = false
            )
        )
    }

    val archiveFiles = remember {
        listOf(
            DesktopArchiveFile(
                id = "file_navi",
                fileName = "NAVI_OS_SPEC.TXT",
                isCanon = true,
                titleEn = "Copland OS & Navi Workstation",
                titleAr = "نظام Copland وجهاز Navi",
                contentEn = "Canon Reference: Lain's bedroom transforms from a quiet suburban room into a dark liquid-cooled computing cluster with dome-vented towers, ribbed ceiling cooling hoses, and glowing CRTs.",
                contentAr = "مرجع أصلي من الأنمي: تتحول غرفة لاين تدريجياً من غرفة هادئة إلى محطة حوسبة كثيفة مبردة بالسائل مع أنابيب سقفية وشاشات CRT متوهجة."
            ),
            DesktopArchiveFile(
                id = "file_cyberia",
                fileName = "CYBERIA_CLUB.LOG",
                isCanon = true,
                titleEn = "Cyberia Cafe & Club (Layer 02)",
                titleAr = "مقهى ونادي سايبيريا (الطبقة 02)",
                contentEn = "Canon Reference: Underground Shibuya club marked by an ice-blue cloud neon sign reading 'CYBERIA Cafe & Club', magenta walls, dark bar stools, and pulsing techno bass.",
                contentAr = "مرجع أصلي من الأنمي: نادٍ ليلي في شيبويا يتميز بلافتة نيون زرقاء سحابية تحمل اسم CYBERIA وجدران بنفسجية داكنة."
            ),
            DesktopArchiveFile(
                id = "file_judo",
                fileName = "BALANCE_DOJO.NOTE",
                isCanon = false,
                titleEn = "Judo Balance & Ukemi Dojo (Original)",
                titleAr = "دوجو الجيدو والتوازن (محتوى أصلي للعبة)",
                contentEn = "Original Game Design Note: Lain's black-belt Judo Gi and Tatami balance trials (Center of Gravity, Kuzushi redirection, Ukemi breakfall timing) are an original game interpretation for interactive balance play, not anime canon or real-world Judo instruction.",
                contentAr = "ملاحظة تصميمية: بدلة الجيدو بالحزام الأسود وتحديات التوازن والـ Ukemi هي إضافة أصلية مخصصة للّعب التفاعلي وليست جزءاً من قصة الأنمي الأصلية ولا بديلاً عن التدريب الحقيقي."
            ),
            DesktopArchiveFile(
                id = "file_sm_lab",
                fileName = "SM_LAB_README.MD",
                isCanon = false,
                titleEn = "Algerian University SM Physics Lab",
                titleAr = "مختبر فيزياء علوم المادة (SM)",
                contentEn = "Architecture Ready for Official PDFs: Organized for L1 (Year 1) and L2 (Year 2) Sciences de la Matière. Modules clearly separate verified structure from user-imported official course PDFs/JSON packs.",
                contentAr = "هيكلة جاهزة لملفات PDF الرسمية: منظمة للسنة الأولى والثانية جامعي علوم المادة (L1 / L2 SM)، وتفصل بوضوح بين التمارين التوليدية والمناهج المستوردة من ملفاتك الرسمية."
            )
        )
    }

    val unreadCount = messages.count { it.id !in readMessageIds }

    Scaffold(
        containerColor = Color(0xFF060A12),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VoidSurface),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("desktop_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back, tint = TextPrimary)
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Text(
                            "NAVI OS // COPLAND 2.0",
                            style = MaterialTheme.typography.titleSmall,
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    Text(
                        "LAYER 07",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        modifier = Modifier.padding(end = 14.dp)
                    )
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Animated Navi OS wallpaper + optional CRT scanlines
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.14f), Color(0xFF070C17), Color(0xFF04070E)),
                        center = Offset(size.width * 0.5f, size.height * 0.35f),
                        radius = size.maxDimension * 0.75f
                    )
                )
                // Subtle circuit grid
                val step = 56f
                var x = step
                while (x < size.width) {
                    drawLine(accent.copy(alpha = 0.05f), Offset(x, 0f), Offset(x, size.height), 1f)
                    x += step
                }
                if (settings.desktopScanlines) {
                    var sy = 0f
                    while (sy < size.height) {
                        drawLine(Color.Black.copy(alpha = 0.18f), Offset(0f, sy), Offset(size.width, sy), 1.2f)
                        sy += 5f
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header card with live Lain portrait in her selected outfit
                Surface(
                    color = VoidSurface.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Canvas(modifier = Modifier.size(64.dp)) {
                            drawLainPortrait(
                                center = Offset(size.width * 0.5f, size.height * 0.5f),
                                radius = size.minDimension * 0.48f,
                                timeSec = animTime,
                                outfit = settings.preferredOutfit,
                                expression = LainExpression.CURIOUS
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.desktopWelcome, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(
                                "${strings.outfitLabel}: ${strings.outfitName(settings.preferredOutfit)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = accent
                            )
                            Text(
                                if (unreadCount > 0) "$unreadCount ${strings.desktopUnreadMessages}" else strings.desktopAllRead,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Desktop Application Icon Grid (6 functional apps — zero dead ends!)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DesktopAppTile(
                        icon = Icons.Default.Email,
                        title = strings.desktopAppMessages,
                        badge = if (unreadCount > 0) "$unreadCount" else null,
                        accent = accent,
                        onClick = { activeWindow = DesktopWindow.MESSAGES },
                        modifier = Modifier.weight(1f).testTag("desktop_app_messages")
                    )
                    DesktopAppTile(
                        icon = Icons.Default.Folder,
                        title = strings.desktopAppFiles,
                        badge = "${archiveFiles.size}",
                        accent = DeskAmber,
                        onClick = { activeWindow = DesktopWindow.FILES },
                        modifier = Modifier.weight(1f).testTag("desktop_app_files")
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DesktopAppTile(
                        icon = Icons.Default.Brush,
                        title = strings.sketchbook,
                        badge = "PRO",
                        accent = HarmonyJade,
                        onClick = onOpenStudio,
                        modifier = Modifier.weight(1f).testTag("desktop_app_studio")
                    )
                    DesktopAppTile(
                        icon = Icons.Default.Science,
                        title = strings.physicsLabTitle,
                        badge = "SM",
                        accent = WiredViolet,
                        onClick = onOpenPhysicsLab,
                        modifier = Modifier.weight(1f).testTag("desktop_app_physics_lab")
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DesktopAppTile(
                        icon = Icons.Default.AutoGraph,
                        title = strings.sandboxTitle,
                        badge = "2D",
                        accent = SignalCoral,
                        onClick = onOpenSandbox,
                        modifier = Modifier.weight(1f).testTag("desktop_app_sandbox")
                    )
                    DesktopAppTile(
                        icon = Icons.Default.Tune,
                        title = strings.desktopAppSystem,
                        badge = null,
                        accent = accent,
                        onClick = { activeWindow = DesktopWindow.SYSTEM },
                        modifier = Modifier.weight(1f).testTag("desktop_app_system")
                    )
                }
            }

            // Active Desktop Window Modal
            when (activeWindow) {
                DesktopWindow.MESSAGES -> {
                    AlertDialog(
                        onDismissRequest = { activeWindow = DesktopWindow.NONE },
                        containerColor = VoidSurfaceElevated,
                        title = { Text(strings.desktopAppMessages, color = accent) },
                        text = {
                            val selected = messages.find { it.id == selectedMessageId }
                            if (selected != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(if (isArabic) selected.subjectAr else selected.subjectEn, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    Text(
                                        "${if (isArabic) selected.senderAr else selected.senderEn} • ${if (selected.isCanon) strings.canonTag else strings.originalTag}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected.isCanon) NaviCyan else DeskAmber
                                    )
                                    HorizontalDivider(color = WiredBorder)
                                    Text(if (isArabic) selected.bodyAr else selected.bodyEn, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                    TextButton(onClick = { selectedMessageId = null }) { Text(strings.back) }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 300.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(messages, key = { it.id }) { msg ->
                                        val isRead = msg.id in readMessageIds
                                        Surface(
                                            color = VoidSurface,
                                            shape = RoundedCornerShape(10.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isRead) WiredBorder else accent),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedMessageId = msg.id
                                                    onMarkMessageRead(msg.id)
                                                }
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(if (isArabic) msg.senderAr else msg.senderEn, style = MaterialTheme.typography.labelLarge, color = accent)
                                                    Text(
                                                        if (msg.isCanon) strings.canonTag else strings.originalTag,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (msg.isCanon) HarmonyJade else DeskAmber
                                                    )
                                                }
                                                Text(if (isArabic) msg.subjectAr else msg.subjectEn, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { activeWindow = DesktopWindow.NONE }) { Text(strings.close) }
                        }
                    )
                }

                DesktopWindow.FILES -> {
                    AlertDialog(
                        onDismissRequest = { activeWindow = DesktopWindow.NONE },
                        containerColor = VoidSurfaceElevated,
                        title = { Text(strings.desktopAppFiles, color = DeskAmber) },
                        text = {
                            val opened = archiveFiles.find { it.id == selectedFileId }
                            if (opened != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(opened.fileName, style = MaterialTheme.typography.labelSmall, color = DeskAmber)
                                    Text(if (isArabic) opened.titleAr else opened.titleEn, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                    HorizontalDivider(color = WiredBorder)
                                    Text(if (isArabic) opened.contentAr else opened.contentEn, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                    TextButton(onClick = { selectedFileId = null }) { Text(strings.back) }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 300.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(archiveFiles, key = { it.id }) { f ->
                                        Surface(
                                            color = VoidSurface,
                                            shape = RoundedCornerShape(10.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, WiredBorder),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedFileId = f.id }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(f.fileName, style = MaterialTheme.typography.labelMedium, color = DeskAmber)
                                                    Text(if (isArabic) f.titleAr else f.titleEn, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                                }
                                                Text(
                                                    if (f.isCanon) strings.canonTag else strings.originalTag,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (f.isCanon) HarmonyJade else WiredViolet
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { activeWindow = DesktopWindow.NONE }) { Text(strings.close) }
                        }
                    )
                }

                DesktopWindow.SYSTEM -> {
                    AlertDialog(
                        onDismissRequest = { activeWindow = DesktopWindow.NONE },
                        containerColor = VoidSurfaceElevated,
                        title = { Text(strings.desktopAppSystem, color = accent) },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(strings.desktopThemeTint, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    DesktopThemeTint.entries.forEach { tint ->
                                        FilterChip(
                                            selected = settings.desktopTheme == tint,
                                            onClick = { onUpdateSettings(settings.copy(desktopTheme = tint)) },
                                            label = { Text(tint.name.substringBefore("_"), style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(strings.desktopScanlines, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                    Switch(
                                        checked = settings.desktopScanlines,
                                        onCheckedChange = { onUpdateSettings(settings.copy(desktopScanlines = it)) }
                                    )
                                }
                                HorizontalDivider(color = WiredBorder)
                                Text(strings.outfitLabel, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(strings.autoOutfitByLocation, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    Switch(
                                        checked = settings.autoOutfitByLocation,
                                        onCheckedChange = { onUpdateSettings(settings.copy(autoOutfitByLocation = it)) }
                                    )
                                }
                                LainOutfit.entries.forEach { outfit ->
                                    val selected = settings.preferredOutfit == outfit
                                    Surface(
                                        color = if (selected) accent.copy(alpha = 0.16f) else VoidSurface,
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) accent else WiredBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onUpdateSettings(settings.copy(preferredOutfit = outfit, autoOutfitByLocation = false))
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(strings.outfitName(outfit), style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                            Text(
                                                if (outfit.isCanon) strings.canonTag else strings.originalTag,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (outfit.isCanon) HarmonyJade else DeskAmber
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = { activeWindow = DesktopWindow.NONE }) { Text(strings.done) }
                        }
                    )
                }

                DesktopWindow.NONE -> Unit
            }
        }
    }
}

@Composable
private fun DesktopAppTile(
    icon: ImageVector,
    title: String,
    badge: String?,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = VoidSurface.copy(alpha = 0.90f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = modifier
            .height(106.dp)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            if (badge != null) {
                Surface(
                    color = accent.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(30.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 2)
            }
        }
    }
}
