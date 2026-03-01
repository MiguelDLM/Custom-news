package com.museovirtualnacional.strogoff.ui.components

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.museovirtualnacional.strogoff.data.repository.ScriptRepository
import com.museovirtualnacional.strogoff.data.repository.AdBlocker
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
                // Generic modern User-Agent to reduce block probability
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                
                android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                
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

                                    // 2. Execute Reader Mode AFTER user scripts.
                                    if (isReaderMode && readabilityScript.isNotEmpty()) {
                                        try {
                                            // Combine the library with the execution logic
                                            // We assume readabilityScript defines 'Readability' global or class
                                            val parserScript = """
                                                (function() {
                                                    try {
                                                        // Ensure Readability is available (injected via readabilityScript)
                                                        if (typeof Readability === 'undefined') { return null; }
                                                        var docClone = document.cloneNode(true); 
                                                        var article = new Readability(docClone).parse();
                                                        return article;
                                                    } catch(e) {
                                                        console.error("Reader check failed", e);
                                                        return null;
                                                    }
                                                })();
                                            """.trimIndent()
                                            
                                            // Execute library definition then parsing
                                            // We separate them to ensure the library is evaluated before we use it
                                            // But evaluateJavascript runs asynchronously. 
                                            // Ideally we concat: library + parser
                                            
                                            // Note: readability.js might be large.
                                            view?.evaluateJavascript(readabilityScript + ";\n" + parserScript) { value ->
                                                if (value != null && value != "null" && value != "undefined") {
                                                    val injector = """
                                                        (function(data) {
                                                            if (!data) return;
                                                            try {
                                                                var title = data.title || document.title || '';
                                                                var content = data.content || '';
                                                                // Improved CSS based on ReadYou/Material You style
                                                                var css = 'body { background-color: #FAFAFA; color: #333; font-family: "Georgia", "Times New Roman", serif; line-height: 1.6; padding: 24px; margin: 0; } ' + 
                                                                          '@media (prefers-color-scheme: dark) { body { background-color: #121212; color: #E0E0E0; } } ' +
                                                                          '.strogoff-article { max-width: 800px; margin: 0 auto; padding-bottom: 60px; } ' +
                                                                          '.strogoff-article h1 { font-family: sans-serif; font-size: 32px; margin-bottom: 16px; font-weight: 700; line-height: 1.3; } ' +
                                                                          '.strogoff-article h2 { font-family: sans-serif; font-size: 24px; margin-top: 32px; margin-bottom: 16px; font-weight: 600; } ' +
                                                                          '.strogoff-article p { font-size: 18px; margin-bottom: 20px; text-align: justify; } ' +
                                                                          '.strogoff-article img { max-width: 100%; height: auto; border-radius: 8px; margin: 20px 0; display: block; margin-left: auto; margin-right: auto; } ' + 
                                                                          '.strogoff-article figure { margin: 20px 0; } ' +
                                                                          '.strogoff-article figcaption { font-size: 14px; color: #666; text-align: center; margin-top: 8px; } ' +
                                                                          '.strogoff-article a { color: #2196F3; text-decoration: none; } ' +
                                                                          '.strogoff-article blockquote { border-left: 4px solid #ccc; padding-left: 16px; margin: 20px 0; font-style: italic; color: #666; } ' +
                                                                          '.strogoff-article code { background: #f5f5f5; padding: 2px 4px; border-radius: 4px; font-family: monospace; } ' + 
                                                                          '.strogoff-article pre { background: #f5f5f5; padding: 16px; border-radius: 8px; overflow-x: auto; }';
                                                                          
                                                                // remove any previous injected style
                                                                document.head.querySelectorAll('style[data-strogoff]').forEach(function(n){n.remove()});
                                                                var style = document.createElement('style'); style.setAttribute('data-strogoff', '1'); style.innerHTML = css; document.head.appendChild(style);
                                                                
                                                                // Set viewport for mobile
                                                                var meta = document.querySelector('meta[name="viewport"]');
                                                                if (!meta) {
                                                                    meta = document.createElement('meta');
                                                                    meta.name = 'viewport';
                                                                    document.head.appendChild(meta);
                                                                }
                                                                meta.content = 'width=device-width, initial-scale=1.0';

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
