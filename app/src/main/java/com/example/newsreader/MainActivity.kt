package com.example.newsreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.newsreader.ui.screens.FeedScreen
import com.example.newsreader.ui.screens.HomeScreen
import com.example.newsreader.ui.screens.ReaderScreen
import com.example.newsreader.ui.screens.ScriptManagerScreen
import com.example.newsreader.ui.theme.NewsReaderTheme
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = application as NewsApplication
        
        setContent {
            NewsReaderTheme {
                val navController = rememberNavController()
                
                NavHost(navController = navController, startDestination = "home") {
                    
                    composable("home") {
                        HomeScreen(
                            newsRepository = app.newsRepository,
                            onFeedClick = { url ->
                                val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                                navController.navigate("feed/$encodedUrl")
                            },
                            onManageScriptsClick = {
                                navController.navigate("scripts")
                            }
                        )
                    }
                    
                    composable(
                        route = "feed/{url}",
                        arguments = listOf(navArgument("url") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val urlArg = backStackEntry.arguments?.getString("url") ?: ""
                        val decodedUrl = URLDecoder.decode(urlArg, StandardCharsets.UTF_8.toString())
                        
                        FeedScreen(
                            url = decodedUrl,
                            newsRepository = app.newsRepository,
                            onArticleClick = { articleUrl ->
                                val encodedUrl = URLEncoder.encode(articleUrl, StandardCharsets.UTF_8.toString())
                                navController.navigate("reader/$encodedUrl")
                            },
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
