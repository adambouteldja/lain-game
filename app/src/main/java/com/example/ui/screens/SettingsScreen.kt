package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ControlMode
import com.example.data.ControlSize
import com.example.data.GameSettings
import com.example.data.LainOutfit
import com.example.i18n.LocalStrings
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: GameSettings,
    onUpdateSettings: (GameSettings) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val strings = LocalStrings.current

    Scaffold(
        containerColor = WiredVoid,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.back,
                        tint = TextPrimary
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = strings.settingsHeader,
                    style = MaterialTheme.typography.headlineMedium,
                    color = WiredViolet
                )
            }

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .align(Alignment.CenterHorizontally)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Language Card (English LTR / Arabic RTL)
                SettingsCard(title = strings.languageSection, icon = Icons.Default.Language) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = settings.languageCode == "en",
                            onClick = { onUpdateSettings(settings.copy(languageCode = "en")) },
                            label = { Text(strings.englishOption) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("lang_en_chip")
                        )
                        FilterChip(
                            selected = settings.languageCode == "ar",
                            onClick = { onUpdateSettings(settings.copy(languageCode = "ar")) },
                            label = { Text(strings.arabicOption) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("lang_ar_chip")
                        )
                    }
                }

                // Lain Wardrobe & Outfit Card
                SettingsCard(title = strings.outfitLabel, icon = Icons.Default.Checkroom) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = strings.autoOutfitByLocation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = settings.autoOutfitByLocation,
                            onCheckedChange = { onUpdateSettings(settings.copy(autoOutfitByLocation = it)) },
                            modifier = Modifier.testTag("settings_auto_outfit_switch")
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LainOutfit.entries.forEach { outfit ->
                            val selected = settings.preferredOutfit == outfit
                            FilterChip(
                                selected = selected,
                                onClick = { onUpdateSettings(settings.copy(preferredOutfit = outfit)) },
                                label = { Text(strings.outfitName(outfit)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .testTag("outfit_chip_${outfit.name.lowercase()}")
                            )
                        }
                    }
                }

                // Audio Card
                SettingsCard(title = strings.audioSection, icon = Icons.Default.VolumeUp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(strings.muteAll, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        Switch(
                            checked = settings.isMuted,
                            onCheckedChange = { onUpdateSettings(settings.copy(isMuted = it)) },
                            modifier = Modifier.testTag("settings_mute_switch")
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(strings.musicVolume, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Text("${(settings.musicVolume * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = NaviCyan)
                        }
                        Slider(
                            value = settings.musicVolume,
                            onValueChange = { onUpdateSettings(settings.copy(musicVolume = it)) },
                            enabled = !settings.isMuted,
                            modifier = Modifier.testTag("settings_music_slider")
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(strings.sfxVolume, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Text("${(settings.sfxVolume * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = NaviCyan)
                        }
                        Slider(
                            value = settings.sfxVolume,
                            onValueChange = { onUpdateSettings(settings.copy(sfxVolume = it)) },
                            enabled = !settings.isMuted,
                            modifier = Modifier.testTag("settings_sfx_slider")
                        )
                    }
                }

                // Controls & Ergonomics Card
                SettingsCard(title = strings.controlsSection, icon = Icons.Default.Gamepad) {
                    Text(strings.controlModeLabel, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = settings.controlMode == ControlMode.BUTTONS,
                            onClick = { onUpdateSettings(settings.copy(controlMode = ControlMode.BUTTONS)) },
                            label = { Text(strings.modeButtons) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("control_mode_buttons")
                        )
                        FilterChip(
                            selected = settings.controlMode == ControlMode.JOYSTICK,
                            onClick = { onUpdateSettings(settings.copy(controlMode = ControlMode.JOYSTICK)) },
                            label = { Text(strings.modeJoystick) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("control_mode_joystick")
                        )
                    }

                    Text(strings.controlSizeLabel, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val sizeLabels = listOf(
                            ControlSize.COMPACT to strings.sizeCompact,
                            ControlSize.STANDARD to strings.sizeStandard,
                            ControlSize.LARGE to strings.sizeLarge
                        )
                        sizeLabels.forEach { (sz, lbl) ->
                            FilterChip(
                                selected = settings.controlSize == sz,
                                onClick = { onUpdateSettings(settings.copy(controlSize = sz)) },
                                label = { Text(lbl) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            )
                        }
                    }

                    // Customizable Horizontal Edge Padding
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(strings.edgePaddingLabel, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Text("${settings.controlHorizontalPaddingDp.roundToInt()} dp", style = MaterialTheme.typography.labelSmall, color = NaviCyan)
                        }
                        Slider(
                            value = settings.controlHorizontalPaddingDp,
                            onValueChange = { onUpdateSettings(settings.copy(controlHorizontalPaddingDp = it)) },
                            valueRange = 4f..36f,
                            modifier = Modifier.testTag("settings_edge_padding_slider")
                        )
                    }

                    // Customizable Bottom Lift Offset
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(strings.bottomOffsetLabel, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Text("${settings.controlBottomOffsetDp.roundToInt()} dp", style = MaterialTheme.typography.labelSmall, color = NaviCyan)
                        }
                        Slider(
                            value = settings.controlBottomOffsetDp,
                            onValueChange = { onUpdateSettings(settings.copy(controlBottomOffsetDp = it)) },
                            valueRange = 0f..36f,
                            modifier = Modifier.testTag("settings_bottom_offset_slider")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(strings.reduceShakeLabel, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        Switch(
                            checked = settings.reduceShake,
                            onCheckedChange = { onUpdateSettings(settings.copy(reduceShake = it)) }
                        )
                    }

                    Text(
                        text = strings.orientationNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = WiredSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, WiredBorder, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = NaviCyan, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            content()
        }
    }
}
