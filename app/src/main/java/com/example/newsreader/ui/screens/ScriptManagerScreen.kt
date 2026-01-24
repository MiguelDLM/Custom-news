package com.example.newsreader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.newsreader.data.local.entity.ScriptEntity
import com.example.newsreader.data.repository.ScriptRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptManagerScreen(
    scriptRepository: ScriptRepository,
    initialDomain: String? = null,
    onBack: () -> Unit
) {
    val scripts by scriptRepository.allScripts.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(initialDomain != null && initialDomain.isNotBlank()) }
    val scope = rememberCoroutineScope()

    // State for the dialog
    var newDomain by remember { mutableStateOf(initialDomain ?: "") }
    var newCode by remember { mutableStateOf("") }
    var foundScripts by remember { mutableStateOf<List<com.example.newsreader.data.repository.GreasyForkScriptSummary>>(emptyList()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Script Manager") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                newDomain = ""
                newCode = ""
                showAddDialog = true 
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Script")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // GreasyFork search UI at the top
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newDomain,
                        onValueChange = { newDomain = it },
                        label = { Text("Domain (e.g. example.com)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        scope.launch {
                            val results = withContext(Dispatchers.IO) {
                                if (newDomain.isBlank()) emptyList()
                                else scriptRepository.searchGreasyForkForDomain(newDomain.trim())
                            }
                            foundScripts = results
                        }
                    }) { Text("Search") }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                // Results Section
                if (foundScripts.isNotEmpty()) {
                    item {
                        Text(
                            "GreasyFork Results",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(foundScripts) { fs ->
                        ListItem(
                            headlineContent = { Text(fs.name) },
                            supportingContent = { 
                                Column {
                                    Text("v${fs.version} • ${fs.authors} • ${fs.updatedAt}", style = MaterialTheme.typography.labelSmall)
                                    Spacer(Modifier.height(4.dp))
                                    Text(fs.description, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            },
                            trailingContent = {
                                var isLoading by remember { mutableStateOf(false) }
                                var isInstalled by remember { mutableStateOf(false) }
                                
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                } else if (isInstalled) {
                                    Icon(Icons.Default.Check, contentDescription = "Installed", tint = MaterialTheme.colorScheme.primary)
                                } else {
                                    IconButton(onClick = {
                                        isLoading = true
                                        scope.launch {
                                            val code = withContext(Dispatchers.IO) { scriptRepository.fetchGreasyForkScriptCode(fs) }
                                            if (!code.isNullOrBlank()) {
                                                withContext(Dispatchers.IO) { scriptRepository.addOrUpdateScript(newDomain.trim(), code) }
                                                isInstalled = true
                                                // Optional: Show snackbar
                                            } else {
                                                // Show error
                                            }
                                            isLoading = false
                                        }
                                    }) { Icon(Icons.Default.Add, contentDescription = "Add") }
                                }
                            }
                        )
                        Divider()
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Installed Scripts Section
                item {
                    Text(
                        "Installed Scripts",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                
                if (scripts.isEmpty()) {
                    item {
                        Text("No scripts installed", modifier = Modifier.padding(16.dp))
                    }
                } else {
                    items(scripts) { script ->
                        ListItem(
                            headlineContent = { Text(script.domainMatch) },
                            supportingContent = { Text(script.jsCode.take(50).replace("\n", " ") + "...") },
                            trailingContent = {
                                IconButton(onClick = {
                                    scope.launch { scriptRepository.deleteScript(script) }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        )
                        Divider()
                    }
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add JS Script") },
                text = {
                    Column {
                        TextField(
                            value = newDomain,
                            onValueChange = { newDomain = it },
                            label = { Text("Domain (e.g., google.com)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = newCode,
                            onValueChange = { newCode = it },
                            label = { Text("JavaScript Code") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 5
                        )
                        Text("Example: document.body.style.background = 'black';", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (newDomain.isNotBlank() && newCode.isNotBlank()) {
                            scope.launch {
                                scriptRepository.addScript(newDomain, newCode)
                                showAddDialog = false
                            }
                        }
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
