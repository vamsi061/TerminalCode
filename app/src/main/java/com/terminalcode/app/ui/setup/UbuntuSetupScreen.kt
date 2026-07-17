package com.terminalcode.app.ui.setup

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminalcode.app.ui.theme.*
import java.io.File

@Composable
fun UbuntuSetupScreen(
    onSetupComplete: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkBackground, Color(0xFF0D1117), Color(0xFF010409))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp))

            // App icon
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(TerminalGreen, DarkAccent)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Terminal, null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Ubuntu Terminal",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold, color = DarkTextPrimary
                )
            )
            Text(
                "Full Linux terminal for Android",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = DarkTextSecondary
                )
            )

            Spacer(Modifier.height(48.dp))

            when (currentStep) {
                0 -> WelcomeStep(
                    onContinue = { currentStep = 1 }
                )
                1 -> GuideStep(
                    onSkip = onSetupComplete,
                    onComplete = onSetupComplete
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Welcome!",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold, color = DarkTextPrimary
            )
        )
        Spacer(Modifier.height(16.dp))

        Text(
            text = "This app gives you a full Linux terminal on your Android device.\n\n" +
                    "The terminal works immediately with Android's shell.\n\n" +
                    "To get a full Ubuntu environment, you can install udroid " +
                    "(Ubuntu-on-Android) from the setup guide in the next step.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = DarkTextSecondary, lineHeight = 22.sp
            ),
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onContinue,
            colors = ButtonDefaults.buttonColors(
                containerColor = DarkAccent, contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Default.ArrowForward, null)
            Spacer(Modifier.width(8.dp))
            Text("Get Started", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onContinue) {
            Text("Skip", color = DarkTextSecondary)
        }
    }
}

@Composable
private fun GuideStep(
    onSkip: () -> Unit,
    onComplete: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Setup Guide",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold, color = DarkTextPrimary
            )
        )
        Spacer(Modifier.height(24.dp))

        // Option 1: Install udroid
        SetupCard(
            title = "Option 1: Install Ubuntu via udroid",
            icon = Icons.Default.Android,
            description = "This installs a full Ubuntu environment using PRoot.\n" +
                    "Requires Termux app (install from GitHub first).",
            steps = listOf(
                "1. Install Termux from GitHub releases",
                "2. Open Termux and run:",
                "   apt update && apt upgrade -y",
                "3. Install udroid:",
                "   . <(curl -Ls https://bit.ly/udroid-installer)",
                "4. Install Ubuntu:",
                "   udroid install jammy:xfce4",
                "5. Launch Ubuntu:",
                "   udroid login jammy"
            )
        )

        Spacer(Modifier.height(16.dp))

        // Option 2: Just use the terminal
        SetupCard(
            title = "Option 2: Use Android Shell",
            icon = Icons.Default.Terminal,
            description = "The terminal works immediately with Android's built-in shell.\n" +
                    "Good for basic commands, file management, and scripting.",
            steps = listOf(
                "• Full Android shell access",
                "• Multi-tab support",
                "• File manager included",
                "• No setup required",
                "• Works immediately"
            ),
            isGreen = true
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onComplete,
            colors = ButtonDefaults.buttonColors(
                containerColor = TerminalGreen, contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Default.Terminal, null)
            Spacer(Modifier.width(8.dp))
            Text("Start Terminal", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SetupCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    steps: List<String>,
    isGreen: Boolean = false
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon, null,
                    tint = if (isGreen) TerminalGreen else DarkAccent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold, color = DarkTextPrimary
                    )
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = DarkTextSecondary, lineHeight = 18.sp
                )
            )

            Spacer(Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0D1117),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    steps.forEach { step ->
                        Text(
                            step,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = if (step.startsWith("•") || step.startsWith("1") || step.startsWith("2") ||
                                    step.startsWith("3") || step.startsWith("4") || step.startsWith("5"))
                                    DarkTextPrimary else TerminalGreen
                            )
                        )
                    }
                }
            }
        }
    }
}
