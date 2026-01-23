package com.example.newsreader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.newsreader.R
import com.example.newsreader.data.local.entity.FeedEntity
import com.example.newsreader.data.repository.NewsRepository
import com.example.newsreader.data.repository.SuggestedFeed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsstandScreen(
    newsRepository: NewsRepository
) {
    val feeds by newsRepository.allFeeds.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    
    // Grouping Mode
    var groupByCountry by remember { mutableStateOf(true) } // true = Country, false = Category

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.newsstand)) },
                actions = {
                    TextButton(onClick = { groupByCountry = !groupByCountry }) {
                        Text(if (groupByCountry) "Group by Category" else "Group by Country")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
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
                    stringResource(R.string.your_subscriptions),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (feeds.isEmpty()) {
                item {
                    Text(stringResource(R.string.no_subscriptions), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                items(feeds) { feed ->
                    Card {
                        ListItem(
                            headlineContent = { Text(feed.title) },
                            supportingContent = { Text(feed.category) },
                            trailingContent = {
                                IconButton(onClick = { scope.launch { newsRepository.deleteFeed(feed) } }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.unsubscribe))
                                }
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.suggested_sources),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Group suggestions
            val suggestions = newsRepository.suggestedFeeds
            val groups = if (groupByCountry) {
                suggestions.groupBy { 
                    when(it.country) {
                        "MX" -> "Mexico"
                        "US" -> "United States"
                        "ES" -> "Spain"
                        else -> it.country
                    }
                }
            } else {
                suggestions.groupBy { it.category }
            }

            groups.forEach { (groupName, groupFeeds) ->
                item {
                    ExpandableGroup(title = groupName, feeds = groupFeeds, currentFeeds = feeds, onAdd = { url, title, cat ->
                        scope.launch { newsRepository.addFeed(url, title, cat) }
                    })
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
fun ExpandableGroup(
    title: String, 
    feeds: List<SuggestedFeed>, 
    currentFeeds: List<FeedEntity>,
    onAdd: (String, String, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
        }
    }
    
    AnimatedVisibility(visible = expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            feeds.forEach { feed ->
                val isSubscribed = currentFeeds.any { it.url == feed.url }
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                    ),
                    border = if (!isSubscribed) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
                ) {
                    ListItem(
                        headlineContent = { Text(feed.title) },
                        supportingContent = { Text("${feed.category} • ${feed.country}") },
                        trailingContent = {
                            if (!isSubscribed) {
                                Button(onClick = { onAdd(feed.url, feed.title, feed.category) }) {
                                    Text(stringResource(R.string.add))
                                }
                            } else {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.added), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                }
            }
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
        title = { Text(stringResource(R.string.custom_feed)) },
        text = {
            Column {
                TextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.title)) })
                Spacer(modifier = Modifier.height(8.dp))
                TextField(value = url, onValueChange = { url = it }, label = { Text(stringResource(R.string.url)) })
                Spacer(modifier = Modifier.height(8.dp))
                TextField(value = category, onValueChange = { category = it }, label = { Text(stringResource(R.string.category)) })
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(title, url, category) }) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
