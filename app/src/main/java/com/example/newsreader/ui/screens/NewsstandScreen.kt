package com.example.newsreader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.newsreader.R
import com.example.newsreader.data.local.entity.Category
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
    val brokenFeeds by newsRepository.brokenFeeds.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Grouping Mode
    var groupByCountry by remember { mutableStateOf(true) } // true = Country, false = Category

    // Search & Pagination
    var searchQuery by remember { mutableStateOf("") }
    // Show suggested groups in pages of 10; more are loaded when user scrolls
    var displayedCount by remember { mutableIntStateOf(10) }
    val listState = rememberLazyListState()

    // Filter suggestions
    val suggestions = newsRepository.suggestedFeeds
    val filteredSuggestions by remember(searchQuery, brokenFeeds, suggestions) {
        derivedStateOf {
            suggestions.filter { feed ->
                !brokenFeeds.contains(feed.url) &&
                (searchQuery.isBlank() || 
                 feed.title.contains(searchQuery, ignoreCase = true) || 
                 feed.url.contains(searchQuery, ignoreCase = true))
            }
        }
    }
    
    // Reset pagination when search changes
    LaunchedEffect(searchQuery) {
        displayedCount = 20
        listState.scrollToItem(0)
    }

    // Infinite Scroll detection for loading more suggestion groups
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) false
            else visibleItems.last().index >= totalItems - 5 
        }
    }

    LaunchedEffect(isAtBottom) {
        if (isAtBottom) {
            // Load 10 more groups when the user reaches the bottom
            displayedCount += 10
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.newsstand)) },
                    actions = {
                        TextButton(onClick = { groupByCountry = !groupByCountry }) {
                            Text(if (groupByCountry) "Group by Category" else "Group by Country")
                        }
                    }
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search feeds...") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null) } }
                    } else null
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
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
                            supportingContent = { Text(feed.categories.joinToString(", ") { it.name }) },
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

            // Group suggestions and sort groups by key
            val groups = if (groupByCountry) {
                filteredSuggestions.groupBy { it.country }
            } else {
                filteredSuggestions.groupBy { it.categories.firstOrNull()?.name ?: "General" }
            }.toList().sortedBy { it.first }

            // Take only a pageful of groups
            val visibleGroups = groups.take(displayedCount)

            visibleGroups.forEach { (groupName, groupFeeds) ->
                item {
                    // Ensure feeds inside each group are shown alphabetically by title
                    val sortedFeeds = groupFeeds.sortedBy { it.title }
                    ExpandableGroup(title = groupName, feeds = sortedFeeds, currentFeeds = feeds, onAdd = { url, title, cats, country ->
                        scope.launch {
                            val success = newsRepository.addFeed(url, title, cats, country)
                            if (success) {
                                snackbarHostState.showSnackbar("Added $title")
                            } else {
                                snackbarHostState.showSnackbar("Feed is broken/invalid. Hiding it.")
                                newsRepository.markFeedAsBroken(url)
                            }
                        }
                    })
                }
            }
            
            if (visibleGroups.size < groups.size) {
                 item {
                     Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                         CircularProgressIndicator()
                     }
                 }
            }
        }

        if (showAddDialog) {
            AddFeedDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { title, url, categoryName ->
                    scope.launch {
                        val cats = listOf(Category.fromString(categoryName))
                        val success = newsRepository.addFeed(url, title, cats, "Global")
                        if (success) {
                             snackbarHostState.showSnackbar("Added $title")
                             showAddDialog = false
                        } else {
                             snackbarHostState.showSnackbar("Invalid Feed URL")
                        }
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
    onAdd: (String, String, List<Category>, String) -> Unit
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
                        supportingContent = { 
                            val cats = feed.categories.joinToString(", ") { it.name }
                            Text("$cats • ${feed.country}") 
                        },
                        trailingContent = {
                            if (!isSubscribed) {
                                Button(onClick = { onAdd(feed.url, feed.title, feed.categories, feed.country) }) {
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
