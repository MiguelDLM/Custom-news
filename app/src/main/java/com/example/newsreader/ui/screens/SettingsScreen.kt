package com.example.newsreader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.newsreader.R
import com.example.newsreader.data.repository.SettingsRepository
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit
) {
    val theme by settingsRepository.theme.collectAsState(initial = "system")
    val language by settingsRepository.language.collectAsState(initial = "en")
    val refreshInterval by settingsRepository.refreshInterval.collectAsState(initial = "30_min")
    val whitelist by settingsRepository.keywordWhitelist.collectAsState(initial = emptySet())
    val blacklist by settingsRepository.keywordBlacklist.collectAsState(initial = emptySet())
    val hiddenTabs by settingsRepository.hiddenTabs.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()

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
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Tab Management (Hidden Tabs)
            TabManagementSection(
                hiddenTabs = hiddenTabs,
                onHide = { scope.launch { settingsRepository.setHiddenTabs(hiddenTabs + it) } },
                onShow = { scope.launch { settingsRepository.setHiddenTabs(hiddenTabs - it) } }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabManagementSection(
    hiddenTabs: Set<String>,
    onHide: (String) -> Unit,
    onShow: (String) -> Unit
) {
    var newTab by remember { mutableStateOf("") }

    Column {
        Text("Hidden Categories (Tabs)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text("Enter exact category name to hide (e.g. Sports)", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newTab,
                onValueChange = { newTab = it },
                label = { Text("Category Name") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = {
                if (newTab.isNotBlank()) {
                    onHide(newTab.trim())
                    newTab = ""
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = "Hide")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            hiddenTabs.forEach { tab ->
                InputChip(
                    selected = false,
                    onClick = { },
                    label = { Text(tab, style = androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Show",
                            Modifier.size(InputChipDefaults.IconSize).clickable { onShow(tab) }
                        )
                    }
                )
            }
        }
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
