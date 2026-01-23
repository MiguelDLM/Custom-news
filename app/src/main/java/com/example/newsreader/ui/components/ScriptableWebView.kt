package com.example.newsreader.ui.components

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.viewinterop.AndroidView
import com.example.newsreader.data.repository.ScriptRepository
import kotlinx.coroutines.launch

@Composable
fun ScriptableWebView(
    url: String,
    scriptRepository: ScriptRepository
) {
    val scope = rememberCoroutineScope()

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                
                webViewClient = object : WebViewClient() {
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
