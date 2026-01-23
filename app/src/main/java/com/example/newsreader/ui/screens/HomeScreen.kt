package com.example.newsreader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.newsreader.data.local.entity.ArticleEntity
import com.example.newsreader.data.repository.NewsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    newsRepository: NewsRepository,
    onArticleClick: (String) -> Unit,
    onManageScriptsClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    
    // Data states
    var feedsInitialized by remember { mutableStateOf(false) }
    val feeds by newsRepository.allFeeds.collectAsState(initial = emptyList())
    
    // Categories logic
    val categories = remember(feeds) {
        listOf("For You") + feeds.map { it.category }.distinct().sorted()
    }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    
    // Articles flow based on selection
    val currentCategory = categories.getOrElse(selectedCategoryIndex) { "For You" }
    
    // We observe the flow dynamically based on category
    var articles by remember { mutableStateOf<List<ArticleEntity>>(emptyList()) }
    
    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Sync on start
    LaunchedEffect(Unit) {
        newsRepository.syncFeeds()
    }

    // Update articles when category changes or DB updates
    LaunchedEffect(currentCategory, searchQuery) {
        if (searchQuery.isNotEmpty()) {
            articles = newsRepository.search(searchQuery)
        } else {
             val flow = if (currentCategory == "For You") {
                newsRepository.getAllArticles()
            } else {
                newsRepository.getArticlesByCategory(currentCategory)
            }
            flow.collect { list ->
                articles = list
            }
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = { },
                    active = true,
                    onActiveChange = { isSearchActive = it },
                    placeholder = { Text("Search news...") },
                    trailingIcon = { IconButton(onClick = { isSearchActive = false; searchQuery = "" }) { Icon(Icons.Default.Settings, "Close") } } // Just reusing icon for now
                ) {
                   // Search results are handled in main content
                }
            } else {
                Column {
                    TopAppBar(
                        title = { Text("Google News Clone") },
                        actions = {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                            IconButton(onClick = onManageScriptsClick) {
                                Icon(Icons.Default.Settings, contentDescription = "Scripts")
                            }
                        }
                    )
                    ScrollableTabRow(selectedTabIndex = selectedCategoryIndex) {
                        categories.forEachIndexed { index, title ->
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
    ) { padding ->
        if (articles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (feeds.isEmpty()) {
                     Text("No sources yet. Go to Newsstand tab.")
                } else {
                     CircularProgressIndicator() // Or empty state
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(articles) { article ->
                    NewsCard(article = article, onClick = { onArticleClick(article.link) })
                }
            }
        }
    }
}

@Composable
fun NewsCard(article: ArticleEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            if (article.imageUrl != null) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentScale = ContentScale.Crop
                )
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (article.pubDate != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = article.pubDate,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}
