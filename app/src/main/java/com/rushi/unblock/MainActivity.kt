package com.rushi.unblock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.rushi.unblock.ui.theme.UnblockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UnblockTheme {
                Surface(modifier = Modifier) {
                    Text("Unblock — scaffold OK")
                }
            }
        }
    }
}
