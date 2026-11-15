package com.rushi.blockescape.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rushi.blockescape.season.SeasonalLook
import com.rushi.blockescape.ui.festive.LocalSeasonal

/** Wording for each option; pure so the labels and the helper text stay testable and consistent. */
fun seasonalLookLabel(look: SeasonalLook): String = when (look) {
    SeasonalLook.AUTO -> "Auto"
    SeasonalLook.ON -> "On"
    SeasonalLook.OFF -> "Off"
}

fun onOffLabel(on: Boolean): String = if (on) "On" else "Off"

const val SEASONAL_LOOK_HELP =
    "Auto turns the festive look on from 1 December to 6 January. On and Off ignore the date."

const val VIBRATION_HELP = "Short buzzes for a blocked move and when you clear a level."

const val MUSIC_HELP =
    "Soft background music that plays only while the festive look is on. It stays quiet if other audio is already playing."

const val PRIVACY_OPTIONS_HELP =
    "Change or withdraw your consent for ads. Without consent, ads are not personalised, and Google may show limited ads or none."

@Composable
fun SettingsScreen(
    look: SeasonalLook,
    onLookChange: (SeasonalLook) -> Unit,
    vibrationOn: Boolean,
    onVibrationChange: (Boolean) -> Unit,
    musicOn: Boolean,
    onMusicChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    // Shown only when Google's consent SDK says privacy options are required (EEA, UK, Switzerland).
    privacyOptionsVisible: Boolean = false,
    onPrivacyOptions: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val wash = Brush.radialGradient(listOf(colorScheme.surface, colorScheme.background))
    ScreenSurface(seasonal = LocalSeasonal.current, brush = wash) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            FestiveTopInset()
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Text("‹ Levels", color = colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))

            OptionRow(
                title = "Seasonal look",
                options = SeasonalLook.entries.map { seasonalLookLabel(it) },
                selectedIndex = SeasonalLook.entries.indexOf(look),
                onSelect = { onLookChange(SeasonalLook.entries[it]) },
                help = SEASONAL_LOOK_HELP
            )
            Spacer(Modifier.height(24.dp))
            OptionRow(
                title = "Vibration",
                options = listOf(onOffLabel(true), onOffLabel(false)),
                selectedIndex = if (vibrationOn) 0 else 1,
                onSelect = { onVibrationChange(it == 0) },
                help = VIBRATION_HELP
            )
            Spacer(Modifier.height(24.dp))
            OptionRow(
                title = "Festive music",
                options = listOf(onOffLabel(true), onOffLabel(false)),
                selectedIndex = if (musicOn) 0 else 1,
                onSelect = { onMusicChange(it == 0) },
                help = MUSIC_HELP
            )
            if (privacyOptionsVisible) {
                Spacer(Modifier.height(24.dp))
                Text("Privacy options", style = MaterialTheme.typography.titleMedium, color = colorScheme.onSurface)
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colorScheme.surfaceVariant)
                        .border(1.dp, colorScheme.onSurfaceVariant.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button, onClick = onPrivacyOptions)
                        .semantics { contentDescription = "Privacy options" }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Review ad choices", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                }
                Spacer(Modifier.height(10.dp))
                Text(PRIVACY_OPTIONS_HELP, fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A titled segmented control with a line of helper text under it. */
@Composable
private fun OptionRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    help: String
) {
    val colorScheme = MaterialTheme.colorScheme
    Text(title, style = MaterialTheme.typography.titleMedium, color = colorScheme.onSurface)
    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) colorScheme.primary else colorScheme.surfaceVariant)
                    .border(
                        1.dp,
                        if (selected) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
                        RoundedCornerShape(11.dp)
                    )
                    .clickable(role = Role.RadioButton) { onSelect(index) }
                    .semantics { contentDescription = "$title $label" }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) colorScheme.onPrimary else colorScheme.onSurface
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(help, fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
}
