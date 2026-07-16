package com.terminalcode.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.terminalcode.app.service.TerminalService
import com.terminalcode.app.ui.MainScreen
import com.terminalcode.app.ui.theme.DarkBackground
import com.terminalcode.app.ui.theme.TerminalCodeTheme

/**
 * Main entry point for TerminalCode.
 *
 * Sets up the Compose UI with the dark theme and handles
 * intents for opening files from other apps.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start terminal foreground service
        startTerminalService()

        // Handle incoming intents
        handleIntent(intent)

        Log.d(TAG, "MainActivity created")

        setContent {
            TerminalCodeTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    MainScreen(
                        onOpenFile = { uri, name ->
                            // Handle file opening from the file manager
                            Log.d(TAG, "Opening file: $name ($uri)")
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    /**
     * Handles intents from other apps (e.g., opening a text file).
     */
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    Log.d(TAG, "Received VIEW intent: $uri")
                    // TODO: Open file in editor
                }
            }
            Intent.ACTION_EDIT -> {
                intent.data?.let { uri ->
                    Log.d(TAG, "Received EDIT intent: $uri")
                    // TODO: Open file for editing
                }
            }
        }
    }

    /**
     * Starts the terminal foreground service so terminal sessions
     * continue running even when the app is backgrounded.
     */
    private fun startTerminalService() {
        try {
            val serviceIntent = Intent(this, TerminalService::class.java).apply {
                action = TerminalService.ACTION_START
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start terminal service", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop the foreground service
        try {
            val serviceIntent = Intent(this, TerminalService::class.java).apply {
                action = TerminalService.ACTION_STOP
            }
            stopService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping terminal service", e)
        }
    }
}
