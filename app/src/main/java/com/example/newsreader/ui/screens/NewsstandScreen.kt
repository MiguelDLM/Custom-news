package com.example.newsreader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
fun NewsstandScreen(
    newsRepository: NewsRepository
) {
    val feeds by newsRepository.allFeeds.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Sources") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Feed")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Your Subscriptions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (feeds.isEmpty()) {
                item {
                    Text("No subscriptions yet. Add some below!", style = MaterialTheme.typography.bodyMedium)
                }
            }

            items(feeds) { feed ->
                Card {
                    ListItem(
                        headlineContent = { Text(feed.title) },
                        supportingContent = { Text(feed.category) },
                        trailingContent = {
                            IconButton(onClick = { scope.launch { newsRepository.deleteFeed(feed) } }) {
                                Icon(Icons.Default.Delete, contentDescription = "Unsubscribe")
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Suggested Sources",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(newsRepository.suggestedFeeds) { (url, title, category) ->
                val isSubscribed = feeds.any { it.url == url }
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                    ),
                    border = if (!isSubscribed) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
                ) {
                    ListItem(
                        headlineContent = { Text(title) },
                        supportingContent = { Text(category) },
                        trailingContent = {
                            if (!isSubscribed) {
                                Button(onClick = {
                                    scope.launch { newsRepository.addFeed(url, title, category) }
                                }) {
                                    Text("Add")
                                }
                            } else {
                                Icon(Icons.Default.Add, contentDescription = "Added", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                }
            }
        }

        if (showAddDialog) {
            AddFeedDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { title, url, category ->
                    scope.launch {
                        newsRepository.addFeed(url, title, category)
                        showAddDialog = false
                    }
                }
            )
        }
    }
}

@Composable
fun AddFeedDialog(onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("General") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom RSS") },
        text = {
            Column {
                TextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                Spacer(modifier = Modifier.height(8.dp))
                TextField(value = url, onValueChange = { url = it }, label = { Text("URL") })
                Spacer(modifier = Modifier.height(8.dp))
                TextField(value = category, onValueChange = { category = it }, label = { Text("Category") })
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(title, url, category) }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
