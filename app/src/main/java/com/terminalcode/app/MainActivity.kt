package com.terminalcode.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.terminalcode.app.ui.MainScreen
import com.terminalcode.app.ui.theme.DarkBackground
import com.terminalcode.app.ui.theme.TerminalCodeTheme

/**
 * Main entry point for TerminalCode.
 *
 * Uses Termux's TerminalView + TerminalSession for a proper
 * real TTY terminal experience on Android.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TerminalCodeTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    MainScreen(
                        onOpenFile = { _, _ -> }
                    )
                }
            }
        }
    }
}
