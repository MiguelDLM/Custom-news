package com.example.newsreader.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.newsreader.data.repository.ScriptRepository
import com.example.newsreader.ui.components.ScriptableWebView
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    url: String,
    scriptRepository: ScriptRepository,
    onBack: () -> Unit,
    onManageScripts: (String) -> Unit // Pass the domain to pre-fill
) {
    // Extract domain for convenience
    val domain = try {
        URI(url).host
    } catch (e: Exception) {
        ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reading") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onManageScripts(domain ?: "") }) {
                        Icon(Icons.Default.Code, contentDescription = "Inject Script")
                    }
                }
            )
        }
    ) { padding ->
        // Use Surface/Box to handle padding if ScriptableWebView doesn't support modifier
        Surface(modifier = Modifier.padding(padding)) {
            ScriptableWebView(
                url = url,
                scriptRepository = scriptRepository
            )
        }
    }
}
