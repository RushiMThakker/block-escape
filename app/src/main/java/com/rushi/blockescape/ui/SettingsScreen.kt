package com.rushi.blockescape.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

const val SEASONAL_LOOK_HELP =
    "Auto turns the festive look on from 1 December to 6 January. On and Off ignore the date."

@Composable
fun SettingsScreen(look: SeasonalLook, onLookChange: (SeasonalLook) -> Unit, onBack: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val wash = Brush.radialGradient(listOf(colorScheme.surface, colorScheme.background))
    ScreenSurface(seasonal = LocalSeasonal.current, brush = wash) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
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
            Spacer(Modifier.height(28.dp))
            Text("Seasonal look", style = MaterialTheme.typography.titleMedium, color = colorScheme.onSurface)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colorScheme.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (option in SeasonalLook.entries) {
                    val selected = option == look
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (selected) colorScheme.primary else colorScheme.surfaceVariant)
                            .border(1.dp, if (selected) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.18f), RoundedCornerShape(11.dp))
                            .clickable(role = Role.RadioButton) { onLookChange(option) }
                            .semantics { contentDescription = "Seasonal look ${seasonalLookLabel(option)}" }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            seasonalLookLabel(option),
                            fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) colorScheme.onPrimary else colorScheme.onSurface
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(SEASONAL_LOOK_HELP, fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
        }
    }
}
