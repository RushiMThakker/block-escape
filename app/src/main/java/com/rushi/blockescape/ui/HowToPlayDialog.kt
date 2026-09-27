package com.rushi.blockescape.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One-time (and re-openable from level-select) explanation of the rules. */
@Composable
fun HowToPlayDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("How to play", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier) {
                Text("Slide the blocks out of the way to clear a path for the red block to reach the glowing exit on the edge of the board.")
                Text("Each block only moves along its own length: horizontal blocks slide left and right, vertical blocks slide up and down. Just drag them.")
                Text("Fewer moves is better - try to match the \"Best\" number shown for each level.")
                Text("Stuck? Hint is free and unlimited, and Undo and Restart are always there.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    )
}
