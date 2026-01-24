package com.example.newsreader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.newsreader.data.repository.ScriptRepository
import com.example.newsreader.ui.components.ScriptableWebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.Close
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    url: String,
    scriptRepository: ScriptRepository,
    onBack: () -> Unit,
    onManageScripts: (String) -> Unit
) {
    val context = LocalContext.current
    val domain = try { URI(url).host ?: "" } catch (e: Exception) { "" }
    
    // State for scripts
    var availableScriptsCount by remember { mutableStateOf(0) }
    var showSuggestionBanner by remember { mutableStateOf(false) }
    
    LaunchedEffect(domain) {
        if (domain.isNotBlank()) {
            val count = withContext(Dispatchers.IO) {
                // Quick check for count (we might need a lighter query or just use the search logic)
                scriptRepository.searchGreasyForkForDomain(domain).size
            }
            if (count > 0) {
                availableScriptsCount = count
                showSuggestionBanner = true
                delay(3000) // Show for 3 seconds (2 requested, + fade buffer)
                showSuggestionBanner = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(domain.ifBlank { "Reading" }, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = {
                        val sendIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, url)
                            type = "text/plain"
                        }
                        context.startActivity(android.content.Intent.createChooser(sendIntent, null))
                    }) { Icon(Icons.Default.Share, "Share") }
                    
                    IconButton(onClick = { onManageScripts(domain) }) {
                        BadgedBox(badge = {
                            if (availableScriptsCount > 0) {
                                // Add offset or modify alignment if badge is cut off
                                Badge(modifier = Modifier.offset(x = (-4).dp, y = 4.dp)) { Text("$availableScriptsCount") }
                            }
                        }) {
                            Icon(Icons.Default.Code, "Scripts")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            ScriptableWebView(
                url = url,
                scriptRepository = scriptRepository
            )
            
            // Non-invasive Banner
            AnimatedVisibility(
                visible = showSuggestionBanner,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Scripts available ($availableScriptsCount)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Button(
                            onClick = { onManageScripts(domain) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("View", style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(
                            onClick = { showSuggestionBanner = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, "Dismiss", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }
    }
}
