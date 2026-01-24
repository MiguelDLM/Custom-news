package com.example.newsreader.ui.components

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.example.newsreader.data.repository.ScriptRepository
import com.example.newsreader.data.repository.AdBlocker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ScriptableWebView(
    url: String,
    scriptRepository: ScriptRepository
) {
    val scope = rememberCoroutineScope()
    
    // Init AdBlocker (using defaults if not configured, or ideally inject settings repo to get lists)
    // For simplicity in this view component, we'll just init with defaults or rely on Repository init elsewhere.
    // Ideally AdBlocker.init() should be called in Application or MainActivity with proper lists.
    // We'll call reload with empty extra lists for now to ensure it's up, but better logic is in Main.
    // Actually, let's just make init optional or parameterless for default behavior if already inited.
    // We changed init signature. Let's fix AdBlocker.kt first to allow parameterless init for defaults.
    LaunchedEffect(Unit) {
        // We will assume it's initialized by MainActivity or we pass empty sets for defaults
        AdBlocker.init(emptySet(), emptySet()) 
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                
                webViewClient = object : WebViewClient() {
                    
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val requestUrl = request?.url?.toString()
                        if (requestUrl != null && AdBlocker.isBlocked(requestUrl)) {
                            // Block request by returning empty response
                            return WebResourceResponse("text/plain", "utf-8", null)
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        url?.let { currentUrl ->
                            scope.launch {
                                try {
                                    val scripts = scriptRepository.getScriptsForUrl(currentUrl)
                                    scripts.forEach { script ->
                                        // Execute user script
                                        // We wrap it in a function to isolate scope slightly
                                        val safeScript = "(function() { ${script.jsCode} })();"
                                        view?.evaluateJavascript(safeScript, null)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                }
            }
        },
        update = { webView ->
            if (webView.url != url) {
                webView.loadUrl(url)
            }
        }
    )
}
