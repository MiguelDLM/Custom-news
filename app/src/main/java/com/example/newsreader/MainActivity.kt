package com.example.newsreader

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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.newsreader.ui.screens.HomeScreen
import com.example.newsreader.ui.screens.NewsstandScreen
import com.example.newsreader.ui.screens.ReaderScreen
import com.example.newsreader.ui.screens.ScriptManagerScreen
import com.example.newsreader.ui.theme.NewsReaderTheme
import kotlinx.coroutines.runBlocking
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = application as NewsApplication
        
        // Check initial state synchronously for simplicity in this demo (ideally use ViewModel/SplashScreen)
        val initialRoute = runBlocking {
            if (app.newsRepository.getFeedCount() == 0) "newsstand" else "home"
        }
        
        setContent {
            NewsReaderTheme {
                val navController = rememberNavController()
                val currentBackStack by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStack?.destination?.route
                
                // Routes that show bottom bar
                val mainRoutes = listOf("home", "newsstand")
                val showBottomBar = currentRoute in mainRoutes

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar {
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                    label = { Text("For You") },
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
                                    icon = { Icon(Icons.Default.LibraryBooks, contentDescription = "Newsstand") },
                                    label = { Text("Newsstand") },
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
                        startDestination = initialRoute,
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
                                }
                            )
                        }

                        composable("newsstand") {
                            NewsstandScreen(newsRepository = app.newsRepository)
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
