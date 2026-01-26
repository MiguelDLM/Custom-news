package com.example.newsreader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.newsreader.R
import com.example.newsreader.data.local.entity.ArticleEntity
import com.example.newsreader.data.repository.NewsRepository
import com.example.newsreader.util.DateUtils
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.net.URI

import com.example.newsreader.data.local.entity.Category

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    newsRepository: NewsRepository,
    onArticleClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onManageScriptsClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Data states
    val feeds by newsRepository.allFeeds.collectAsState(initial = emptyList())
    // hidden tabs feature removed; keep for compatibility but always empty
    val hiddenTabs by newsRepository.hiddenTabs.collectAsState(initial = emptySet())
    val whitelist by newsRepository.whitelist.collectAsState(initial = emptySet())
    val searchHistory by newsRepository.searchHistory.collectAsState(initial = emptyList())
    
    val tabOrder by newsRepository.tabOrder.collectAsState(initial = emptyList())
    
    // Define all possible categories (as strings for display/tabs)
    // We map internal Category enums to localized display strings or simple names
    val allCategories = remember(feeds, tabOrder) {
        // Collect all categories from feeds (flatten list of categories)
        val rawCategories = (listOf("For You") + feeds.flatMap { it.categories }.map { it.name }.distinct().sorted())
        
        // Apply custom order if exists
        if (tabOrder.isNotEmpty()) {
            val ordered = tabOrder.filter { rawCategories.contains(it) }
            val remaining = rawCategories.filter { !ordered.contains(it) }
            ordered + remaining
        } else {
            rawCategories
        }
    }
    
    // Filter hidden tabs
    val visibleCategories = allCategories.filter { !hiddenTabs.contains(it) }
    
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    
    // Ensure index is valid if list shrinks
    if (selectedCategoryIndex >= visibleCategories.size && visibleCategories.isNotEmpty()) {
        selectedCategoryIndex = 0
    }
    
    val currentCategoryKey = visibleCategories.getOrElse(selectedCategoryIndex) { "For You" }
    
    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Sorting State
    var sortByDate by remember { mutableStateOf(true) } // true = Date, false = Source
    var showSortMenu by remember { mutableStateOf(false) }

    // Articles State
    var articles by remember { mutableStateOf<List<ArticleEntity>>(emptyList()) }
    // Pull-to-refresh state: when the user is at the top and scrolls up further we trigger a refresh
    var isRefreshing by remember { mutableStateOf(false) }
    
    // Update articles logic
    LaunchedEffect(currentCategoryKey, searchQuery, sortByDate, whitelist) {
        if (searchQuery.isNotEmpty()) {
             // For search we just do a one-shot fetch for now or we could wrap in flow
             val results = newsRepository.search(searchQuery) // Wildcards handled in Repo/Dao
             articles = sortArticles(results, sortByDate)
        } else {
            val flow = if (currentCategoryKey == "For You") {
                newsRepository.getForYouArticles()
            } else {
                newsRepository.getArticlesByCategory(currentCategoryKey)
            }
            
            flow.collect { list ->
                articles = sortArticles(list, sortByDate)
            }
        }
    }

    // When the user overscrolls at the top, refresh feeds (fetch new RSS)
    val listState = rememberLazyListState()
    LaunchedEffect(listState.isScrollInProgress, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        // If user is at top (first item index 0 and offset 0) and attempts to scroll up (negative offset not exposed),
        // we approximate by checking that they are at top and a refresh isn't already running.
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 && !isRefreshing) {
            // No-op: just keep ready to detect overscroll via nested fling in real app; here we expose a manual trigger
        }
    }

    // Simple top-swipe detection using LaunchedEffect watching isScrollInProgress: if user stops scrolling at top quickly, refresh
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 && !isRefreshing) {
            // Trigger a refresh of RSS feeds
            isRefreshing = true
            scope.launch {
                try {
                    newsRepository.syncFeeds()
                } catch (e: Exception) {
                    // ignore
                }
                isRefreshing = false
            }
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = { 
                        if (searchQuery.isNotBlank()) {
                            scope.launch { newsRepository.saveSearch(searchQuery) }
                        }
                    },
                    active = true,
                    onActiveChange = { isSearchActive = it },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    trailingIcon = { 
                        if (searchQuery.isNotEmpty()) {
                             IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, "Clear") }
                        } else {
                             IconButton(onClick = { isSearchActive = false }) { Icon(Icons.Default.Close, "Close") } 
                        }
                    } 
                ) {
                 if (searchQuery.isEmpty()) {
                       LazyColumn {
                           item {
                               Row(
                                   modifier = Modifier.fillMaxWidth().padding(16.dp),
                                   horizontalArrangement = Arrangement.SpaceBetween,
                                   verticalAlignment = Alignment.CenterVertically
                               ) {
                                    Text(stringResource(R.string.recent_searches), style = MaterialTheme.typography.titleMedium)
                                    if (searchHistory.isNotEmpty()) {
                                        TextButton(onClick = { scope.launch { newsRepository.clearHistory() } }) {
                                            Text(stringResource(R.string.clear_all))
                                        }
                                    }
                                }
                           }
                           items(searchHistory) { historyItem ->
                               ListItem(
                                   headlineContent = { Text(historyItem.query) },
                                   leadingContent = { Icon(Icons.Default.History, null) },
                                   trailingContent = { 
                                       IconButton(onClick = { scope.launch { newsRepository.deleteHistoryItem(historyItem) } }) {
                                           Icon(Icons.Default.Close, "Remove")
                                       }
                                   },
                                   modifier = Modifier.clickable { searchQuery = historyItem.query }
                               )
                           }
                       }
                   } else {
                       // Show results here if SearchBar supports content for results, 
                       // otherwise we rely on main content below, but SearchBar overlays content usually.
                       // We should duplicate result list here or hide main content.
                       // For simplicity in this structure, let's show results list inside SearchBar if active
                       LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                       ) {
                            items(articles) { article ->
                                NewsCard(
                                    article = article, 
                                    onClick = { onArticleClick(article.link) },
                                    sourceName = getDomainName(article.link)
                                )
                            }
                       }
                   }
                }
            } else {
                Column {
                    TopAppBar(
                        title = { Text(stringResource(R.string.app_name)) },
                        actions = {
                            // Sort Menu
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.Default.Sort, contentDescription = "Sort")
                                }
                                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Date (Newest)") },
                                        onClick = { sortByDate = true; showSortMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Source (A-Z)") },
                                        onClick = { sortByDate = false; showSortMenu = false }
                                    )
                                }
                            }
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                             IconButton(onClick = onSettingsClick) {
                                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                            }
                        }
                    )
                    if (visibleCategories.isNotEmpty()) {
                        // Simple, stable tabs bar. Reordering is available from Settings only.
                        val tabs = visibleCategories
                        if (tabs.isEmpty()) {
                            selectedCategoryIndex = 0
                        } else if (selectedCategoryIndex >= tabs.size) {
                            selectedCategoryIndex = tabs.size - 1
                        }

                        ScrollableTabRow(selectedTabIndex = selectedCategoryIndex) {
                            tabs.forEachIndexed { index, key ->
                                val title = when (key) {
                                    "For You" -> stringResource(R.string.for_you)
                                    "Politics", "POLITICS" -> stringResource(R.string.politics)
                                    "Technology", "TECHNOLOGY" -> stringResource(R.string.technology)
                                    "Sports", "SPORTS" -> stringResource(R.string.sports)
                                    "Finance", "FINANCE" -> stringResource(R.string.finance)
                                    "World", "WORLD" -> stringResource(R.string.world)
                                    "General", "GENERAL" -> stringResource(R.string.general)
                                    else -> key
                                }

                                Tab(
                                    selected = selectedCategoryIndex == index,
                                    onClick = { selectedCategoryIndex = index; searchQuery = "" },
                                    text = { Text(title) }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->


        if (visibleCategories.isEmpty() && feeds.isEmpty()) {
             Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                 Text(stringResource(R.string.no_subscriptions))
             }
        } else if (currentCategoryKey == "For You" && articles.isEmpty() && searchQuery.isEmpty()) {
             Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                 Column(horizontalAlignment = Alignment.CenterHorizontally) {
                      Text(stringResource(R.string.no_topics))
                     Spacer(modifier = Modifier.height(8.dp))
                      Button(onClick = onSettingsClick) {
                          Text(stringResource(R.string.add_interests))
                      }
                 }
             }
        } else if (articles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (searchQuery.isNotEmpty()) {
                     Text("No results for '$searchQuery'")
                } else {
                     CircularProgressIndicator() 
                }
            }
        } else {
            // Use Compose pull-to-refresh from material
            val pullRefreshState = rememberPullRefreshState(isRefreshing, onRefresh = {
                if (!isRefreshing) {
                    isRefreshing = true
                    scope.launch {
                        try {
                            newsRepository.syncFeeds()
                        } catch (e: Exception) {
                            // ignore
                        }
                        isRefreshing = false
                    }
                }
            })

            Box(modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .pullRefresh(pullRefreshState)) {

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Show a small progress indicator at top when refreshing (kept for accessibility fallback)
                    if (isRefreshing) {
                        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                    }
                    items(articles, key = { it.id }) { article ->
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Show a small progress indicator at top when refreshing
                if (isRefreshing) {
                    item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                }
                    items(articles, key = { it.id }) { article ->
                    val dismissState = rememberDismissState(
                        confirmValueChange = {
                            if (it != DismissValue.Default) {
                                scope.launch { 
                                    newsRepository.hideArticle(article.id)
                                }
                                true
                            } else {
                                false
                            }
                        }
                    )

                    SwipeToDismiss(
                        state = dismissState,
                        background = {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Hide")
                            }
                        },
                        dismissContent = {
                            NewsCard(
                                article = article, 
                                onClick = { onArticleClick(article.link) },
                                sourceName = getDomainName(article.link)
                            )
                        },
                        directions = setOf(DismissDirection.EndToStart)
                    )
                }

                // Pull refresh indicator overlay
                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}

fun sortArticles(list: List<ArticleEntity>, byDate: Boolean): List<ArticleEntity> {
    return if (byDate) {
        list.sortedByDescending { it.pubDateMillis }
    } else {
        list.sortedBy { getDomainName(it.link) }
    }
}

fun getDomainName(url: String): String {
    return try {
        val domain = URI(url).host
        domain.removePrefix("www.")
    } catch (e: Exception) {
        ""
    }
}

@Composable
fun NewsCard(article: ArticleEntity, onClick: () -> Unit, sourceName: String) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            if (article.imageUrl != null) {
                Box {
                    AsyncImage(
                        model = article.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentScale = ContentScale.Crop
                    )
                    // Source overlay
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(8.dp).align(Alignment.BottomStart)
                    ) {
                        Text(
                            text = sourceName,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (article.imageUrl == null) {
                            Text(
                                text = sourceName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (article.pubDateMillis > 0) {
                            Text(
                                text = DateUtils.getTimeAgo(article.pubDateMillis, context),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    // Share button moved to Reader
                }
            }
        }
    }
}
