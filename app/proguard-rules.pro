# TerminalCode ProGuard Rules

# Keep the application class
-keep class com.terminalcode.app.TerminalCodeApp { *; }

# Keep JavaScript interface methods (called from WebView)
-keepclassmembers class com.terminalcode.app.terminal.TerminalWebViewBridge {
    @android.webkit.JavascriptInterface <methods>;
}

-keepclassmembers class com.terminalcode.app.editor.MonacoEditorBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep terminal session
-keep class com.terminalcode.app.terminal.TerminalSession { *; }

# Keep file repository
-keep class com.terminalcode.app.files.FileRepository { *; }

# Keep service
-keep class com.terminalcode.app.service.TerminalService { *; }

# Keep ViewModels
-keep class com.terminalcode.app.ui.terminal.TerminalViewModel { *; }
-keep class com.terminalcode.app.ui.editor.EditorViewModel { *; }
-keep class com.terminalcode.app.ui.files.FileViewModel { *; }

# Keep data classes
-keepclassmembers class com.terminalcode.app.** {
    <fields>;
}

# Keep WebView and related classes
-keep class android.webkit.** { *; }

# Keep JSON
-keep class org.json.** { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
