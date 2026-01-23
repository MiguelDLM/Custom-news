package com.example.newsreader.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.newsreader.data.repository.ScriptRepository
import com.example.newsreader.ui.components.ScriptableWebView
import java.net.URI
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    url: String,
    scriptRepository: ScriptRepository,
    onBack: () -> Unit,
    onManageScripts: (String) -> Unit // Pass the domain to pre-fill
) {
    val context = LocalContext.current
    // Extract domain for convenience
    val domain = try {
        URI(url).host
    } catch (e: Exception) {
        ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(domain ?: "Reading") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val sendIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, url)
                            type = "text/plain"
                        }
                        val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { onManageScripts(domain ?: "") }) {
                        Icon(Icons.Default.Code, contentDescription = "Inject Script")
                    }
                }
            )
        }
    ) { padding ->
        // Use Surface/Column to show a small banner above the webview when a script is available
        Surface(modifier = Modifier.padding(padding)) {
            Column {
                // Script availability state
                var foundScriptSummary by remember { mutableStateOf<com.example.newsreader.data.repository.GreasyForkScriptSummary?>(null) }
                var remoteCode by remember { mutableStateOf<String?>(null) }
                var localScript by remember { mutableStateOf<com.example.newsreader.data.local.entity.ScriptEntity?>(null) }
                var checking by remember { mutableStateOf(true) }
                var errorMsg by remember { mutableStateOf<String?>(null) }

                val scope = rememberCoroutineScope()

                LaunchedEffect(domain) {
                    checking = true
                    errorMsg = null
                    try {
                        // Check local script
                        localScript = withContext(Dispatchers.IO) { scriptRepository.getScriptForDomain(domain ?: "") }
                        // Search GreasyFork for candidates
                        val results = withContext(Dispatchers.IO) {
                            if (domain.isNullOrBlank()) emptyList()
                            else scriptRepository.searchGreasyForkForDomain(domain!!)
                        }
                        val first = results.firstOrNull()
                        if (first != null) {
                            foundScriptSummary = first
                            // fetch remote code
                            remoteCode = withContext(Dispatchers.IO) { scriptRepository.fetchGreasyForkScriptCode(first) }
                        } else {
                            foundScriptSummary = null
                            remoteCode = null
                        }
                    } catch (e: Exception) {
                        errorMsg = e.localizedMessage
                    } finally {
                        checking = false
                    }
                }

                // Banner
                if (!checking && (foundScriptSummary != null || localScript != null)) {
                    val updateAvailable = remoteCode != null && localScript != null && remoteCode != localScript?.jsCode
                    val installAvailable = remoteCode != null && localScript == null

                    Surface(modifier = Modifier.fillMaxWidth().padding(8.dp), tonalElevation = 4.dp) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                if (installAvailable) {
                                    Text("Script available for this site")
                                } else if (updateAvailable) {
                                    Text("Update available for the installed script")
                                } else if (localScript != null) {
                                    Text("Script installed for this site")
                                }
                                if (foundScriptSummary != null) {
                                    Text(foundScriptSummary!!.url, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Row {
                                if (installAvailable) {
                                    Button(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                remoteCode?.let { scriptRepository.addOrUpdateScript(domain ?: "", it) }
                                            }
                                            // refresh localScript state
                                            localScript = withContext(Dispatchers.IO) { scriptRepository.getScriptForDomain(domain ?: "") }
                                        }
                                    }) { Text("Install") }
                                } else if (updateAvailable) {
                                    Button(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                remoteCode?.let { scriptRepository.addOrUpdateScript(domain ?: "", it) }
                                            }
                                            localScript = withContext(Dispatchers.IO) { scriptRepository.getScriptForDomain(domain ?: "") }
                                        }
                                    }) { Text("Update") }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { /* open script manager */ onManageScripts(domain ?: "") }) { Text("Manage") }
                            }
                        }
                    }
                }

                // WebView below
                ScriptableWebView(
                    url = url,
                    scriptRepository = scriptRepository
                )
            }
        }
    }
}
