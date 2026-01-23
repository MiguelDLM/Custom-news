package com.example.newsreader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.newsreader.data.local.entity.ScriptEntity
import com.example.newsreader.data.repository.ScriptRepository
import kotlinx.coroutines.launch

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
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            items(scripts) { script ->
                ListItem(
                    headlineContent = { Text(script.domainMatch) },
                    supportingContent = { Text(script.jsCode, maxLines = 1) },
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
