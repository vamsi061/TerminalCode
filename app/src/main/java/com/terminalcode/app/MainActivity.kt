package com.terminalcode.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.terminalcode.app.ui.MainScreen
import com.terminalcode.app.ui.setup.UbuntuSetupScreen
import com.terminalcode.app.ui.theme.DarkBackground
import com.terminalcode.app.ui.theme.TerminalCodeTheme
import java.io.File

class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "terminal_prefs"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val isSetupComplete = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SETUP_COMPLETE, false)

        setContent {
            TerminalCodeTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    if (isSetupComplete) {
                        MainScreen(
                            onOpenFile = { _, _ -> }
                        )
                    } else {
                        UbuntuSetupScreen(
                            onSetupComplete = {
                                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                    .edit()
                                    .putBoolean(KEY_SETUP_COMPLETE, true)
                                    .apply()
                            }
                        )
                    }
                }
            }
        }
    }
}
