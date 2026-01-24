package com.example.newsreader.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.newsreader.R
import com.example.newsreader.data.repository.SettingsRepository
import com.example.newsreader.data.repository.AdBlocker
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.io.BufferedReader
import java.io.InputStreamReader
import org.json.JSONArray
import org.json.JSONObject

enum class SettingsPage {
    MAIN,
    INTERFACE,
    LANGUAGE,
    THEME,
    ADBLOCK,
    BACKUP,
    SCRIPT_MANAGER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    onScriptManagerClick: () -> Unit // Add navigation callback
) {
    val context = LocalContext.current
    var currentPage by remember { mutableStateOf(SettingsPage.MAIN) }
    
    // Global Back Handler could be added here if we want hardware back to nav up in settings hierarchy
    
    when (currentPage) {
        SettingsPage.MAIN -> MainSettingsMenu(onBack, { currentPage = it }, onScriptManagerClick)
        SettingsPage.INTERFACE -> InterfaceSettings(settingsRepository) { currentPage = SettingsPage.MAIN }
        SettingsPage.LANGUAGE -> LanguageSettings(settingsRepository) { currentPage = SettingsPage.MAIN }
        SettingsPage.THEME -> ThemeSettings(settingsRepository) { currentPage = SettingsPage.MAIN }
        SettingsPage.ADBLOCK -> AdBlockSettings(settingsRepository) { currentPage = SettingsPage.MAIN }
        SettingsPage.BACKUP -> BackupSettings(settingsRepository) { currentPage = SettingsPage.MAIN }
        SettingsPage.SCRIPT_MANAGER -> { /* Handled externally or we could embed it here if refactored */ }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsMenu(onBack: () -> Unit, onNavigate: (SettingsPage) -> Unit, onScriptManagerClick: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                SettingsCategoryHeader("General")
                SettingsMenuItem("Language", Icons.Default.Language, onClick = { onNavigate(SettingsPage.LANGUAGE) })
                SettingsMenuItem(stringResource(R.string.theme), Icons.Default.DarkMode, onClick = { onNavigate(SettingsPage.THEME) })
                SettingsMenuItem("Interface & Reorder", Icons.Default.ViewQuilt, onClick = { onNavigate(SettingsPage.INTERFACE) })
            }
            item {
                SettingsCategoryHeader("Advanced")
                SettingsMenuItem("Script Manager", Icons.Default.Code, onClick = onScriptManagerClick)
            }
            item {
                SettingsCategoryHeader("Privacy & Security")

                SettingsMenuItem("AdBlocker", Icons.Default.Security, onClick = { onNavigate(SettingsPage.ADBLOCK) })
            }
            item {
                SettingsCategoryHeader("Data")
                SettingsMenuItem("Backup & Restore", Icons.Default.Save, onClick = { onNavigate(SettingsPage.BACKUP) })
            }
        }
    }
}

@Composable
fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsMenuItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

// --- Sub Screens ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdBlockSettings(repository: SettingsRepository, onBack: () -> Unit) {
    val enabledLists by repository.adBlockEnabledLists.collectAsState(initial = emptySet())
    val customLists by repository.adBlockCustomLists.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("AdBlocker") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Add List")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                SettingsCategoryHeader("Default Lists")
            }
            items(AdBlocker.PREDEFINED_LISTS.toList()) { (url, name) ->
                val isEnabled = enabledLists.contains(url)
                ListItem(
                    headlineContent = { Text(name) },
                    supportingContent = { Text(url, maxLines = 1) },
                    trailingContent = {
                        Switch(checked = isEnabled, onCheckedChange = { checked ->
                            val newSet = if (checked) enabledLists + url else enabledLists - url
                            scope.launch { 
                                repository.setAdBlockEnabledLists(newSet)
                                AdBlocker.reload(newSet, customLists)
                            }
                        })
                    }
                )
            }

            item {
                SettingsCategoryHeader("Custom Lists")
            }
            if (customLists.isEmpty()) {
                item { Text("No custom lists added", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
            }
            items(customLists.toList()) { url ->
                ListItem(
                    headlineContent = { Text(url) },
                    trailingContent = {
                        IconButton(onClick = {
                            val newSet = customLists - url
                            scope.launch { 
                                repository.setAdBlockCustomLists(newSet)
                                AdBlocker.reload(enabledLists, newSet)
                            }
                        }) {
                            Icon(Icons.Default.Delete, "Delete")
                        }
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        var newUrl by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Host List URL") },
            text = {
                OutlinedTextField(value = newUrl, onValueChange = { newUrl = it }, label = { Text("URL") })
            },
            confirmButton = {
                Button(onClick = {
                    if (newUrl.isNotBlank()) {
                        val newSet = customLists + newUrl.trim()
                        scope.launch { 
                            repository.setAdBlockCustomLists(newSet) 
                            AdBlocker.reload(enabledLists, newSet)
                        }
                        showAddDialog = false
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterfaceSettings(repository: SettingsRepository, onBack: () -> Unit) {
    val refreshInterval by repository.refreshInterval.collectAsState(initial = "30_min")
    val whitelist by repository.keywordWhitelist.collectAsState(initial = emptySet())
    val blacklist by repository.keywordBlacklist.collectAsState(initial = emptySet())
    val tabOrder by repository.tabOrder.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showReorderDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Interface") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            
            Text("Tabs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Button(onClick = { showReorderDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Reorder Categories") }
            Spacer(modifier = Modifier.height(24.dp))

            Text(stringResource(R.string.refresh_interval), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            val refreshOptions = listOf("15_min" to stringResource(R.string.every_15_min), "30_min" to stringResource(R.string.every_30_min), "1_hour" to stringResource(R.string.every_hour), "daily" to stringResource(R.string.every_day))
            refreshOptions.forEach { (key, label) ->
                Row(Modifier.fillMaxWidth().height(48.dp).selectable(selected = (refreshInterval == key), onClick = { scope.launch { repository.setRefreshInterval(key) } }, role = Role.RadioButton), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = (refreshInterval == key), onClick = null)
                    Text(text = label, modifier = Modifier.padding(start = 16.dp))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            KeywordSection(stringResource(R.string.interests), whitelist, { scope.launch { repository.setKeywordWhitelist(whitelist + it) } }, { scope.launch { repository.setKeywordWhitelist(whitelist - it) } })
            Spacer(modifier = Modifier.height(24.dp))
            KeywordSection(stringResource(R.string.hidden_topics), blacklist, { scope.launch { repository.setKeywordBlacklist(blacklist + it) } }, { scope.launch { repository.setKeywordBlacklist(blacklist - it) } })
        }
    }
    
    if (showReorderDialog) {
        ReorderDialog(currentOrder = tabOrder, onSave = { newOrder -> scope.launch { repository.setTabOrder(newOrder) }; showReorderDialog = false }, onDismiss = { showReorderDialog = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettings(repository: SettingsRepository, onBack: () -> Unit) {
    val language by repository.language.collectAsState(initial = "en")
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.language)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            val languageOptions = listOf("en" to "English", "es" to "Español")
            languageOptions.forEach { (key, label) ->
                Row(Modifier.fillMaxWidth().height(56.dp).selectable(selected = (language == key), onClick = { scope.launch { repository.setLanguage(key) } }, role = Role.RadioButton), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = (language == key), onClick = null)
                    Text(text = label, modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettings(repository: SettingsRepository, onBack: () -> Unit) {
    val theme by repository.theme.collectAsState(initial = "system")
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.theme)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            val themeOptions = listOf("light" to stringResource(R.string.theme_light), "dark" to stringResource(R.string.theme_dark), "system" to stringResource(R.string.theme_system))
            themeOptions.forEach { (key, label) ->
                Row(Modifier.fillMaxWidth().height(56.dp).selectable(selected = (theme == key), onClick = { scope.launch { repository.setTheme(key) } }, role = Role.RadioButton), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = (theme == key), onClick = null)
                    Text(text = label, modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettings(repository: SettingsRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { scope.launch { exportSettings(context, repository, it) } } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { scope.launch { importSettings(context, repository, it) } } }

    Scaffold(topBar = { TopAppBar(title = { Text("Backup & Restore") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Button(onClick = { exportLauncher.launch("news_settings_backup.json") }, modifier = Modifier.fillMaxWidth()) { Text("Export Settings") }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = { importLauncher.launch("application/json") }, modifier = Modifier.fillMaxWidth()) { Text("Import Settings") }
        }
    }
}

// Reused Components

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun KeywordSection(title: String, keywords: Set<String>, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    var newKeyword by remember { mutableStateOf("") }
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = newKeyword, onValueChange = { newKeyword = it }, label = { Text(stringResource(R.string.add_keyword)) }, modifier = Modifier.weight(1f), singleLine = true)
            IconButton(onClick = { if (newKeyword.isNotBlank()) { onAdd(newKeyword.trim()); newKeyword = "" } }) { Icon(Icons.Default.Add, "Add") }
        }
        FlowRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keywords.forEach { keyword ->
                InputChip(selected = false, onClick = {}, label = { Text(keyword) }, trailingIcon = { Icon(Icons.Default.Close, "Remove", Modifier.size(InputChipDefaults.IconSize).clickable { onRemove(keyword) }) })
            }
        }
    }
}

@Composable
fun ReorderDialog(currentOrder: List<String>, onSave: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val defaultCategories = listOf("For You", "Politics", "Technology", "Sports", "Finance", "World", "General")
    val initialList = if (currentOrder.isNotEmpty()) currentOrder else defaultCategories
    var list by remember { mutableStateOf(initialList) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().height(420.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Drag to Reorder", style = MaterialTheme.typography.titleLarge)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(list) { index, item ->
                        ReorderableItem(item = item, index = index, onMoveUp = { if (index > 0) { val m = list.toMutableList(); val t = m[index-1]; m[index-1] = m[index]; m[index] = t; list = m } }, onMoveDown = { if (index < list.size - 1) { val m = list.toMutableList(); val t = m[index+1]; m[index+1] = m[index]; m[index] = t; list = m } })
                    }
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onSave(list) }) { Text("Save") }
                }
            }
        }
    }
}

@Composable
fun ReorderableItem(item: String, index: Int, onMoveUp: () -> Unit, onMoveDown: () -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (dragging) 1.02f else 1f)
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
        val thresholdPx = with(LocalDensity.current) { 36.dp.toPx() }
        var accumulated by remember { mutableStateOf(0f) }
        var pendingMove by remember { mutableStateOf(0) }
        Row(modifier = Modifier.padding(12.dp).pointerInput(Unit) { detectDragGesturesAfterLongPress(onDragStart = { dragging = true }, onDragEnd = { dragging = false; when (pendingMove) { -1 -> onMoveUp(); 1 -> onMoveDown() }; pendingMove = 0; accumulated = 0f }, onDragCancel = { dragging = false; pendingMove = 0; accumulated = 0f }) { change, dragAmount -> accumulated += dragAmount.y; if (accumulated < -thresholdPx) { pendingMove = -1; accumulated = 0f } else if (accumulated > thresholdPx) { pendingMove = 1; accumulated = 0f }; change.consume() } }, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DragHandle, null)
            Spacer(modifier = Modifier.width(16.dp))
            Text(item, modifier = Modifier.weight(1f))
            IconButton(onClick = onMoveUp) { Icon(Icons.Default.KeyboardArrowUp, "Up") }
            IconButton(onClick = onMoveDown) { Icon(Icons.Default.KeyboardArrowDown, "Down") }
        }
    }
}

suspend fun exportSettings(context: Context, repository: SettingsRepository, uri: Uri) {
    try {
        val json = JSONObject()
        json.put("theme", repository.theme.first())
        json.put("language", repository.language.first())
        json.put("refreshInterval", repository.refreshInterval.first())
        json.put("whitelist", JSONArray(repository.keywordWhitelist.first()))
        json.put("blacklist", JSONArray(repository.keywordBlacklist.first()))
        json.put("tabOrder", JSONArray(repository.tabOrder.first()))
        context.contentResolver.openOutputStream(uri)?.use { it.write(json.toString(4).toByteArray()) }
    } catch (e: Exception) { e.printStackTrace() }
}

suspend fun importSettings(context: Context, repository: SettingsRepository, uri: Uri) {
    try {
        val content = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { BufferedReader(InputStreamReader(it)).use { r -> r.readLines().forEach { l -> content.append(l) } } }
        val json = JSONObject(content.toString())
        if (json.has("theme")) repository.setTheme(json.getString("theme"))
        if (json.has("language")) repository.setLanguage(json.getString("language"))
        if (json.has("refreshInterval")) repository.setRefreshInterval(json.getString("refreshInterval"))
        if (json.has("whitelist")) { val arr = json.getJSONArray("whitelist"); val set = mutableSetOf<String>(); for(i in 0 until arr.length()) set.add(arr.getString(i)); repository.setKeywordWhitelist(set) }
        if (json.has("blacklist")) { val arr = json.getJSONArray("blacklist"); val set = mutableSetOf<String>(); for(i in 0 until arr.length()) set.add(arr.getString(i)); repository.setKeywordBlacklist(set) }
        if (json.has("tabOrder")) { val arr = json.getJSONArray("tabOrder"); val list = mutableListOf<String>(); for(i in 0 until arr.length()) list.add(arr.getString(i)); repository.setTabOrder(list) }
    } catch (e: Exception) { e.printStackTrace() }
}
