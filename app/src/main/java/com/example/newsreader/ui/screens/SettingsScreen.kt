package com.example.newsreader.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import kotlinx.coroutines.flow.first
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
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
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val theme by settingsRepository.theme.collectAsState(initial = "system")
    val language by settingsRepository.language.collectAsState(initial = "en")
    val refreshInterval by settingsRepository.refreshInterval.collectAsState(initial = "30_min")
    val whitelist by settingsRepository.keywordWhitelist.collectAsState(initial = emptySet())
    val blacklist by settingsRepository.keywordBlacklist.collectAsState(initial = emptySet())
    val tabOrder by settingsRepository.tabOrder.collectAsState(initial = emptyList())
    
    val scope = rememberCoroutineScope()
    var showReorderDialog by remember { mutableStateOf(false) }

    // Export Launcher
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            scope.launch {
                exportSettings(context, settingsRepository, it)
            }
        }
    }

    // Import Launcher
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                importSettings(context, settingsRepository, it)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Backup & Restore
            Text("Backup & Restore", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportLauncher.launch("news_settings_backup.json") }) {
                    Text("Export Settings")
                }
                OutlinedButton(onClick = { importLauncher.launch("application/json") }) {
                    Text("Import Settings")
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Tab Ordering
            Text("Interface", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { showReorderDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Reorder Categories (Tabs)")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Theme Section
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            
            val themeOptions = listOf(
                "light" to stringResource(R.string.theme_light),
                "dark" to stringResource(R.string.theme_dark),
                "system" to stringResource(R.string.theme_system)
            )

            themeOptions.forEach { (key, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .selectable(
                            selected = (theme == key),
                            onClick = { scope.launch { settingsRepository.setTheme(key) } },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (theme == key),
                        onClick = null 
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Language Section
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            val languageOptions = listOf(
                "en" to "English",
                "es" to "Español"
            )

            languageOptions.forEach { (key, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .selectable(
                            selected = (language == key),
                            onClick = { scope.launch { settingsRepository.setLanguage(key) } },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (language == key),
                        onClick = null
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Refresh Interval Section
            Text(stringResource(R.string.refresh_interval), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            val refreshOptions = listOf(
                "15_min" to stringResource(R.string.every_15_min),
                "30_min" to stringResource(R.string.every_30_min),
                "1_hour" to stringResource(R.string.every_hour),
                "daily" to stringResource(R.string.every_day)
            )

            refreshOptions.forEach { (key, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .selectable(
                            selected = (refreshInterval == key),
                            onClick = { scope.launch { settingsRepository.setRefreshInterval(key) } },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (refreshInterval == key),
                        onClick = null
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Interests (Whitelist)
            KeywordSection(
                title = stringResource(R.string.interests),
                keywords = whitelist,
                onAdd = { scope.launch { settingsRepository.setKeywordWhitelist(whitelist + it) } },
                onRemove = { scope.launch { settingsRepository.setKeywordWhitelist(whitelist - it) } }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Hidden Topics (Blacklist)
            KeywordSection(
                title = stringResource(R.string.hidden_topics),
                keywords = blacklist,
                onAdd = { scope.launch { settingsRepository.setKeywordBlacklist(blacklist + it) } },
                onRemove = { scope.launch { settingsRepository.setKeywordBlacklist(blacklist - it) } }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    if (showReorderDialog) {
        ReorderDialog(
            currentOrder = tabOrder,
            onSave = { newOrder -> 
                scope.launch { settingsRepository.setTabOrder(newOrder) }
                showReorderDialog = false
            },
            onDismiss = { showReorderDialog = false }
        )
    }
}

@Composable
fun ReorderDialog(currentOrder: List<String>, onSave: (List<String>) -> Unit, onDismiss: () -> Unit) {
    // Default categories if empty
    val defaultCategories = listOf("For You", "Politics", "Technology", "Sports", "Finance", "World", "General")
    val initialList = if (currentOrder.isNotEmpty()) currentOrder else defaultCategories
    var list by remember { mutableStateOf(initialList) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().height(420.dp)) {
             Column(modifier = Modifier.padding(16.dp)) {
                 Text("Drag to Reorder", style = MaterialTheme.typography.titleLarge)
                 Spacer(modifier = Modifier.height(12.dp))

                 // We implement a simple long-press drag that swaps item with neighbor
                 LazyColumn(
                     modifier = Modifier.weight(1f)
                 ) {
                     itemsIndexed(list) { index, item ->
                         ReorderableItem(
                             item = item,
                             index = index,
                             onMoveUp = {
                                 if (index > 0) {
                                     val mutable = list.toMutableList()
                                     val temp = mutable[index-1]
                                     mutable[index-1] = mutable[index]
                                     mutable[index] = temp
                                     list = mutable
                                 }
                             },
                             onMoveDown = {
                                 if (index < list.size - 1) {
                                     val mutable = list.toMutableList()
                                     val temp = mutable[index+1]
                                     mutable[index+1] = mutable[index]
                                     mutable[index] = temp
                                     list = mutable
                                 }
                             }
                         )
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        // Accumulate vertical drag and queue a move; apply on drag end to avoid
        // mutating the list during an ongoing measure pass.
        val thresholdPx = with(LocalDensity.current) { 36.dp.toPx() }
        var accumulated by remember { mutableStateOf(0f) }
        var pendingMove by remember { mutableStateOf(0) } // -1 = up, 1 = down, 0 = none

        Row(
            modifier = Modifier
                .padding(12.dp)
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            // apply pending move when drag ends
                            when (pendingMove) {
                                -1 -> onMoveUp()
                                1 -> onMoveDown()
                            }
                            pendingMove = 0
                            accumulated = 0f
                        },
                        onDragCancel = {
                            dragging = false
                            pendingMove = 0
                            accumulated = 0f
                        }
                    ) { change, dragAmount ->
                        accumulated += dragAmount.y
                        // Queue move when threshold reached; do not mutate list immediately
                        if (accumulated < -thresholdPx) {
                            pendingMove = -1
                            accumulated = 0f
                        } else if (accumulated > thresholdPx) {
                            pendingMove = 1
                            accumulated = 0f
                        }
                        change.consume()
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
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
        
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(json.toString(4).toByteArray())
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

suspend fun importSettings(context: Context, repository: SettingsRepository, uri: Uri) {
    try {
        val content = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    content.append(line)
                    line = reader.readLine()
                }
            }
        }
        
        val json = JSONObject(content.toString())
        
        if (json.has("theme")) repository.setTheme(json.getString("theme"))
        if (json.has("language")) repository.setLanguage(json.getString("language"))
        if (json.has("refreshInterval")) repository.setRefreshInterval(json.getString("refreshInterval"))
        
        if (json.has("whitelist")) {
            val arr = json.getJSONArray("whitelist")
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) set.add(arr.getString(i))
            repository.setKeywordWhitelist(set)
        }
        
        if (json.has("blacklist")) {
            val arr = json.getJSONArray("blacklist")
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) set.add(arr.getString(i))
            repository.setKeywordBlacklist(set)
        }

        if (json.has("tabOrder")) {
            val arr = json.getJSONArray("tabOrder")
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) list.add(arr.getString(i))
            repository.setTabOrder(list)
        }
        
    } catch (e: Exception) {
        e.printStackTrace()
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordSection(
    title: String,
    keywords: Set<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var newKeyword by remember { mutableStateOf("") }

    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newKeyword,
                onValueChange = { newKeyword = it },
                label = { Text(stringResource(R.string.add_keyword)) },
                placeholder = { Text(stringResource(R.string.keyword_hint)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = {
                if (newKeyword.isNotBlank()) {
                    onAdd(newKeyword.trim())
                    newKeyword = ""
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            keywords.forEach { keyword ->
                InputChip(
                    selected = false,
                    onClick = { },
                    label = { Text(keyword) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove",
                            Modifier.size(InputChipDefaults.IconSize).clickable { onRemove(keyword) }
                        )
                    }
                )
            }
        }
    }
}
