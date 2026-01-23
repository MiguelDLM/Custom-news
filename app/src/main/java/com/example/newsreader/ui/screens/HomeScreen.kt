package com.example.newsreader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.newsreader.data.local.entity.FeedEntity
import com.example.newsreader.data.repository.NewsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    newsRepository: NewsRepository,
    onFeedClick: (String) -> Unit,
    onManageScriptsClick: () -> Unit
) {
    val feeds by newsRepository.allFeeds.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("News Reader") },
                actions = {
                    IconButton(onClick = onManageScriptsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Manage Scripts")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Feed")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            items(feeds) { feed ->
                ListItem(
                    headlineContent = { Text(feed.title) },
                    supportingContent = { Text(feed.url) },
                    modifier = Modifier.clickable { onFeedClick(feed.url) },
                    trailingContent = {
                        IconButton(onClick = {
                            scope.launch { newsRepository.deleteFeed(feed) }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                )
                Divider()
            }
        }

        if (showAddDialog) {
            AddFeedDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { title, url ->
                    scope.launch {
                        newsRepository.addFeed(url, title)
                        showAddDialog = false
                    }
                }
            )
        }
    }
}

@Composable
fun AddFeedDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add RSS Feed") },
        text = {
            Column {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(title, url) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
