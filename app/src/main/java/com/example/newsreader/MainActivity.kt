package com.example.newsreader

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.newsreader.ui.screens.HomeScreen
import com.example.newsreader.ui.screens.NewsstandScreen
import com.example.newsreader.ui.screens.ReaderScreen
import com.example.newsreader.ui.screens.ScriptManagerScreen
import com.example.newsreader.ui.screens.SettingsScreen
import com.example.newsreader.ui.theme.NewsReaderTheme
import kotlinx.coroutines.runBlocking
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = application as NewsApplication
        
        setContent {
            val theme by app.settingsRepository.theme.collectAsState(initial = "system")
            val language by app.settingsRepository.language.collectAsState(initial = "en")

            // Apply Language
            LaunchedEffect(language) {
                val locale = Locale(language)
                Locale.setDefault(locale)
                val config = Configuration()
                config.setLocale(locale)
                baseContext.resources.updateConfiguration(config, baseContext.resources.displayMetrics)
            }

            // Determine Dark Mode
            val darkTheme = when(theme) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            NewsReaderTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                val currentBackStack by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStack?.destination?.route
                
                val mainRoutes = listOf("home", "newsstand")
                val showBottomBar = currentRoute in mainRoutes

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar {
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Home, contentDescription = stringResource(R.string.for_you)) },
                                    label = { Text(stringResource(R.string.for_you)) },
                                    selected = currentRoute == "home",
                                    onClick = {
                                        navController.navigate("home") {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.LibraryBooks, contentDescription = stringResource(R.string.newsstand)) },
                                    label = { Text(stringResource(R.string.newsstand)) },
                                    selected = currentRoute == "newsstand",
                                    onClick = {
                                        navController.navigate("newsstand") {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home", // Simplified start
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("home") {
                            HomeScreen(
                                newsRepository = app.newsRepository,
                                onArticleClick = { url ->
                                    val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                                    navController.navigate("reader/$encodedUrl")
                                },
                                onManageScriptsClick = {
                                    navController.navigate("scripts")
                                },
                                onSettingsClick = {
                                    navController.navigate("settings")
                                }
                            )
                        }

                        composable("newsstand") {
                            NewsstandScreen(newsRepository = app.newsRepository)
                        }

                        composable("settings") {
                            SettingsScreen(
                                settingsRepository = app.settingsRepository,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        
                        composable(
                            route = "reader/{url}",
                            arguments = listOf(navArgument("url") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val urlArg = backStackEntry.arguments?.getString("url") ?: ""
                            val decodedUrl = URLDecoder.decode(urlArg, StandardCharsets.UTF_8.toString())
                            
                            ReaderScreen(
                                url = decodedUrl,
                                scriptRepository = app.scriptRepository,
                                onBack = { navController.popBackStack() },
                                onManageScripts = { domain ->
                                    val encodedDomain = URLEncoder.encode(domain, StandardCharsets.UTF_8.toString())
                                    navController.navigate("scripts?domain=$encodedDomain")
                                }
                            )
                        }
                        
                        composable(
                            route = "scripts?domain={domain}",
                            arguments = listOf(
                                navArgument("domain") { 
                                    type = NavType.StringType
                                    nullable = true 
                                    defaultValue = null
                                }
                            )
                        ) { backStackEntry ->
                            val domainArg = backStackEntry.arguments?.getString("domain")
                            val decodedDomain = if (domainArg != null) 
                                URLDecoder.decode(domainArg, StandardCharsets.UTF_8.toString()) 
                            else null
                            
                            ScriptManagerScreen(
                                scriptRepository = app.scriptRepository,
                                initialDomain = decodedDomain,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
