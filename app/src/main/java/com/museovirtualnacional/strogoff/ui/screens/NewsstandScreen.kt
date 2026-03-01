package com.museovirtualnacional.strogoff.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.museovirtualnacional.strogoff.data.repository.NotificationHelper
import com.museovirtualnacional.strogoff.data.repository.NotificationPermissionManager
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.museovirtualnacional.strogoff.R
import com.museovirtualnacional.strogoff.data.local.entity.FeedEntity
import com.museovirtualnacional.strogoff.data.repository.NewsRepository
import com.museovirtualnacional.strogoff.data.repository.SuggestedFeed
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
    var feedToEdit by remember { mutableStateOf<FeedEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Grouping Mode
    var groupByCountry by remember { mutableStateOf(true) } // true = Country, false = Category

    // Search & Pagination
    var searchQuery by remember { mutableStateOf("") }
    // Show suggested groups in pages of 10; more are loaded when user scrolls
    var displayedCount by remember { mutableIntStateOf(10) }
    val listState = rememberLazyListState()

    // Filter suggestions
    val context = LocalContext.current
    val notificationHelper = NotificationHelper(context)
    val suggestions = newsRepository.suggestedFeeds
    // Feedspot state
    var feedspotResults by remember { mutableStateOf(emptyList<SuggestedFeed>()) }
    var feedspotLoading by remember { mutableStateOf(false) }
    val feedspotToken by newsRepository.feedspotToken.collectAsState(initial = "")
    // Pending additions while waiting for validation/DB insert
    val pendingAdded by remember { mutableStateOf(mutableStateOf(setOf<String>())) }

    // Permission handling for notifications (Android 13+)
    val hasNotificationPermission = remember { mutableStateOf(notificationHelper.hasPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted: Boolean ->
        hasNotificationPermission.value = granted
    }

    // Listen to global permission requests (so permission can be requested from other places)
    LaunchedEffect(Unit) {
        NotificationPermissionManager.requests.collectLatest {
            if (!hasNotificationPermission.value) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    // Exclude broken and already-subscribed feeds from the available suggestions
    val filteredSuggestions by remember(searchQuery, brokenFeeds, suggestions, feeds, pendingAdded) {
        derivedStateOf {
            val subscribedUrls = feeds.map { it.url }.toSet()
            suggestions.filter { feed ->
                !brokenFeeds.contains(feed.url) &&
                !subscribedUrls.contains(feed.url) &&
                !pendingAdded.value.contains(feed.url) &&
                (searchQuery.isBlank() || 
                 feed.title.contains(searchQuery, ignoreCase = true) || 
                 feed.url.contains(searchQuery, ignoreCase = true))
            }
        }
    }
    
    // Reset pagination when search changes
    LaunchedEffect(searchQuery) {
        // Start showing first page (10 groups) when search changes
        displayedCount = 10
        listState.scrollToItem(0)
    }

    LaunchedEffect(searchQuery, feedspotToken) {
        if (searchQuery.length < 3 || feedspotToken.isBlank()) {
            feedspotResults = emptyList()
            feedspotLoading = false
            return@LaunchedEffect
        }
        feedspotLoading = true
        kotlinx.coroutines.delay(500) // debounce
        feedspotResults = newsRepository.searchFeedspot(searchQuery, feedspotToken)
        feedspotLoading = false
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

    var subscribedExpanded by remember { mutableStateOf(true) }
    var isValidating by remember { mutableStateOf(false) }

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
        // Group suggestions and sort groups by key
        val groups = if (groupByCountry) {
            filteredSuggestions.groupBy { it.country }
        } else {
            filteredSuggestions.groupBy { it.categories.firstOrNull() ?: "General" }
        }.toList().sortedBy { it.first }

        // Take only a pageful of groups
        val visibleGroups = groups.take(displayedCount)

        // Keep expanded state and per-group pagination
        val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
        val displayedPerGroup = remember { mutableStateMapOf<String, Int>() }
        val loadingPerGroup = remember { mutableStateMapOf<String, Boolean>() }

        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { subscribedExpanded = !subscribedExpanded },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.your_subscriptions),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            if (!isValidating) {
                                isValidating = true
                                scope.launch {
                                    val broken = newsRepository.validateAllFeeds()
                                    isValidating = false
                                    if (broken.isEmpty()) {
                                        snackbarHostState.showSnackbar("All feeds are valid")
                                    } else {
                                        snackbarHostState.showSnackbar("Found ${broken.size} invalid feeds")
                                    }
                                }
                            }
                        }) {
                            if (isValidating) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Check, contentDescription = "Validate Feeds")
                            }
                        }
                        Icon(
                            if (subscribedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
            }

            if (subscribedExpanded) {
                if (feeds.isEmpty()) {
                    item {
                        Text(stringResource(R.string.no_subscriptions), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp))
                    }
                } else {
                    items(feeds) { feed ->
                        val isBroken = brokenFeeds.contains(feed.url)
                        Card(
                             colors = if (isBroken) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer) else CardDefaults.cardColors()
                        ) {
                            ListItem(
                                headlineContent = { Text(feed.title) },
                                supportingContent = { 
                                    Column {
                                        Text(feed.categories.joinToString(", "))
                                        if (isBroken) {
                                            Text("Invalid Feed", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = { feedToEdit = feed }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                                        }
                                        IconButton(onClick = { scope.launch { newsRepository.deleteFeed(feed) } }) {
                                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.unsubscribe))
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (feedspotLoading || feedspotResults.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Feedspot Results",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (feedspotLoading) {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                
                if (feedspotResults.isNotEmpty()) {
                    items(feedspotResults) { feed ->
                        val isSubscribed = feeds.any { it.url == feed.url }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                            ),
                            border = if (!isSubscribed) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
                        ) {
                            ListItem(
                                headlineContent = { Text(feed.title) },
                                supportingContent = {
                                    val cats = feed.categories.joinToString(", ")
                                    Text("$cats • ${feed.country}")
                                },
                                trailingContent = {
                                    if (!isSubscribed) {
                                        val isPending = pendingAdded.value.contains(feed.url)
                                        Button(onClick = {
                                            pendingAdded.value = pendingAdded.value + feed.url
                                             scope.launch {
                                                 val err = newsRepository.addFeed(feed.url, feed.title, feed.categories, feed.country)
                                                 if (err == null) {
                                                     snackbarHostState.showSnackbar("Added ${feed.title}")
                                                     NotificationPermissionManager.requestPermission()
                                                 } else {
                                                     snackbarHostState.showSnackbar("Feed invalid: $err")
                                                     newsRepository.markFeedAsBroken(feed.url)
                                                 }
                                                 pendingAdded.value = pendingAdded.value - feed.url
                                             }
                                        }) {
                                            if (isPending) {
                                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                            } else {
                                                Text(stringResource(R.string.add))
                                            }
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

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.suggested_sources),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            visibleGroups.forEach { (groupName, groupFeeds) ->
                // Ensure feeds inside each group are shown alphabetically by title
                val sortedFeeds = groupFeeds.sortedBy { it.title }

                // Header for group
                item(key = "group_header_$groupName") {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            val currently = expandedGroups[groupName] ?: false
                            expandedGroups[groupName] = !currently
                            if (!currently) {
                                // initialize pagination when expanded
                                displayedPerGroup[groupName] = 10
                            }
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                text = groupName,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                if (expandedGroups[groupName] == true) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                    }
                }

                // Feed items when expanded
                if (expandedGroups[groupName] == true) {
                    val displayed = displayedPerGroup[groupName] ?: 10
                    val toShow = sortedFeeds.take(displayed)

                    toShow.forEach { feed ->
                        item(key = "feed_${feed.url}") {
                            val isSubscribed = feeds.any { it.url == feed.url }
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                                ),
                                border = if (!isSubscribed) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
                            ) {
                                ListItem(
                                    headlineContent = { Text(feed.title) },
                                    supportingContent = {
                                        val cats = feed.categories.joinToString(", ")
                                        Text("$cats • ${feed.country}")
                                    },
                                    trailingContent = {
                                        if (!isSubscribed) {
                                            val isPending = pendingAdded.value.contains(feed.url)
                                            Button(onClick = {
                                                // optimistic UI: mark as pending immediately
                                                pendingAdded.value = pendingAdded.value + feed.url
                                                 scope.launch {
                                                     val err = newsRepository.addFeed(feed.url, feed.title, feed.categories, feed.country)
                                                     if (err == null) {
                                                         snackbarHostState.showSnackbar("Added ${feed.title}")
                                                         NotificationPermissionManager.requestPermission()
                                                     } else {
                                                         snackbarHostState.showSnackbar("Feed invalid: $err")
                                                         newsRepository.markFeedAsBroken(feed.url)
                                                     }
                                                     // remove from pending regardless (DB update will re-render)
                                                     pendingAdded.value = pendingAdded.value - feed.url
                                                 }
                                            }) {
                                                if (isPending) {
                                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                                } else {
                                                    Text(stringResource(R.string.add))
                                                }
                                            }
                                        } else {
                                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.added), tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Show load more control if there are more feeds
                    if (displayed < sortedFeeds.size) {
                        item(key = "load_more_$groupName") {
                            val loading = loadingPerGroup[groupName] ?: false
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                if (loading) {
                                    CircularProgressIndicator(Modifier.padding(8.dp))
                                } else {
                                    TextButton(onClick = {
                                        loadingPerGroup[groupName] = true
                                        scope.launch {
                                            // simulate async loading to give UI feedback
                                            kotlinx.coroutines.delay(250)
                                            displayedPerGroup[groupName] = (displayedPerGroup[groupName] ?: 0) + 10
                                            loadingPerGroup[groupName] = false
                                        }
                                    }) {
                                    Text(stringResource(R.string.load_more))
                                    }
                                }
                            }
                        }
                    }
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

        // Gather current category list for dropdown
        val allCategoryOptions = remember(feeds) {
            val base = listOf("General", "Technology", "Science", "World", "Politics", "Sports",
                              "Finance", "Health", "Entertainment", "Business", "Investigative")
            val fromFeeds = feeds.flatMap { it.categories }.distinct()
            (base + fromFeeds).distinct().sorted()
        }

        if (showAddDialog) {
            AddFeedDialog(
                onDismiss = { showAddDialog = false },
                existingCategories = allCategoryOptions,
                onAdd = { title, url, categoryName ->
                    val cats = listOf(categoryName)
                    val err = newsRepository.addFeed(url, title, cats, "Global")
                    if (err == null) { snackbarHostState.showSnackbar("Added $title") }
                    err
                }
            )
        }

        if (feedToEdit != null) {
            val captureFeed = feedToEdit!!  // capture local copy to avoid race condition
            EditFeedDialog(
                feed = captureFeed,
                existingCategories = allCategoryOptions,
                onDismiss = { feedToEdit = null },
                onSave = { updatedTitle, updatedUrl, updatedCategoryName ->
                    scope.launch {
                        val updatedFeed = captureFeed.copy(
                            title = updatedTitle,
                            url = updatedUrl,
                            categories = listOf(updatedCategoryName)
                        )
                        newsRepository.updateFeed(updatedFeed)
                        snackbarHostState.showSnackbar("Updated $updatedTitle")
                        feedToEdit = null
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
    onAdd: (String, String, List<String>, String) -> Unit
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
                            val cats = feed.categories.joinToString(", ")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDropdown(
    selectedCategory: String,
    existingCategories: List<String>,
    onCategorySelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showNewField by remember { mutableStateOf(false) }
    var newCat by remember { mutableStateOf("") }

    Column {
        ExposedDropdownMenuBox(
            expanded = expanded && !showNewField,
            onExpandedChange = { if (!showNewField) expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedCategory,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.category)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && !showNewField) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded && !showNewField,
                onDismissRequest = { expanded = false }
            ) {
                existingCategories.forEach { cat ->
                    DropdownMenuItem(
                        text = { Text(cat) },
                        onClick = { onCategorySelected(cat); expanded = false }
                    )
                }
                Divider()
                DropdownMenuItem(
                    text = { Text("+ New category...") },
                    onClick = { showNewField = true; expanded = false }
                )
            }
        }

        if (showNewField) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = newCat,
                onValueChange = { newCat = it },
                label = { Text("New category name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    Row {
                        IconButton(onClick = {
                            if (newCat.isNotBlank()) {
                                val formatted = newCat.trim().replaceFirstChar { it.uppercase() }
                                onCategorySelected(formatted)
                                showNewField = false
                                newCat = ""
                            }
                        }) { Icon(Icons.Default.Check, contentDescription = "Confirm") }
                        IconButton(onClick = { showNewField = false; newCat = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun AddFeedDialog(
    onDismiss: () -> Unit,
    existingCategories: List<String>,
    onAdd: suspend (String, String, String) -> String?
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(existingCategories.firstOrNull() ?: "General") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text(stringResource(R.string.custom_feed)) },
        text = {
            Column {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.title)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = url,
                    onValueChange = { url = it; errorMsg = null; success = false },
                    label = { Text(stringResource(R.string.url)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMsg != null
                )
                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                CategoryDropdown(
                    selectedCategory = category,
                    existingCategories = existingCategories,
                    onCategorySelected = { category = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isLoading && !success) {
                        isLoading = true
                        errorMsg = null
                        scope.launch {
                            val err = onAdd(title, url, category)
                            if (err == null) {
                                success = true
                                kotlinx.coroutines.delay(600)
                                onDismiss()
                            } else {
                                errorMsg = err
                            }
                            isLoading = false
                        }
                    }
                },
                enabled = url.isNotBlank() && title.isNotBlank() && !isLoading
            ) {
                when {
                    isLoading -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    success -> Icon(Icons.Default.Check, contentDescription = null)
                    else -> Text(stringResource(R.string.add))
                }
            }
        },
        dismissButton = {
            if (!isLoading) TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun EditFeedDialog(
    feed: FeedEntity,
    existingCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(feed.title) }
    var url by remember { mutableStateOf(feed.url) }
    var category by remember { mutableStateOf(feed.categories.firstOrNull() ?: "General") }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Edit Feed") },
        text = {
            Column {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.title)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.url)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                CategoryDropdown(
                    selectedCategory = category,
                    existingCategories = existingCategories,
                    onCategorySelected = { category = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isSaving) {
                        isSaving = true
                        scope.launch {
                            onSave(title, url, category)
                        }
                    }
                },
                enabled = !isSaving
            ) {
                if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            if (!isSaving) TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
