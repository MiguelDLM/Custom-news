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
    scriptRepository: ScriptRepository,
    isReaderMode: Boolean = false,
    readabilityScript: String = ""
) {
    val scope = rememberCoroutineScope()
    
    // Init AdBlocker (using defaults if not configured, or ideally inject settings repo to get lists)
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
                                    // 1. Execute User Scripts first so they can modify the DOM (remove paywalls,
                                    //    lazy-load content, etc.). These run in the page context.
                                    val scripts = scriptRepository.getScriptsForUrl(currentUrl)
                                    scripts.forEach { script ->
                                        val safeScript = "(function() { ${script.jsCode} })();"
                                        try {
                                            view?.evaluateJavascript(safeScript, null)
                                        } catch (e: Exception) {
                                            // Ignore individual script errors to avoid breaking the flow
                                        }
                                    }

                                    // 2. Execute Reader Mode AFTER user scripts. The readability JS should
                                    //     return an object {title, content} which we render in a clean view.
                                    if (isReaderMode && readabilityScript.isNotEmpty()) {
                                        try {
                                            view?.evaluateJavascript(readabilityScript) { value ->
                                                if (value != null && value != "null") {
                                                    val injector = """
                                                        (function(data) {
                                                            if (!data) return;
                                                            try {
                                                                var title = data.title || document.title || '';
                                                                var content = data.content || '';
                                                                var css = 'body{background:#fff;color:#121212;font-family:Roboto,Helvetica,Arial,sans-serif;line-height:1.6;padding:20px;} .strogoff-article{max-width:900px;margin:0 auto;} .strogoff-article h1{font-size:24px;margin-bottom:12px;} img{max-width:100%;height:auto;}';
                                                                // remove any previous injected style
                                                                document.head.querySelectorAll('style[data-strogoff]').forEach(function(n){n.remove()});
                                                                var style = document.createElement('style'); style.setAttribute('data-strogoff', '1'); style.innerHTML = css; document.head.appendChild(style);
                                                                document.body.innerHTML = '<div class="strogoff-article"><h1>' + title + '</h1>' + content + '</div>';
                                                                window.scrollTo(0,0);
                                                            } catch (e) { console.error(e); }
                                                        })(%s);
                                                    """.trimIndent()

                                                    // The 'value' is a JSON string; we must pass it safely into the injector.
                                                    val safeValue = value
                                                    val final = injector.replace("%s", safeValue)
                                                    try {
                                                        view.evaluateJavascript(final, null)
                                                    } catch (e: Exception) { /* ignore */ }
                                                }
                                            }
                                        } catch (e: Exception) { /* ignore */ }
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
