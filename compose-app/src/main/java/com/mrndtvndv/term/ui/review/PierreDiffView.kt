package com.mrndtvndv.term.ui.review

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewAssetLoader
import java.util.Locale
import org.json.JSONObject

private const val AssetUrl = "https://appassets.androidplatform.net/assets/diff-viewer/index.html"

internal data class DiffDisplaySettings(
    val isDarkTheme: Boolean,
    val showLineNumbers: Boolean,
    val isWordDiffEnabled: Boolean
)

internal class DiffSearchController {
    internal var findNextAction: ((Boolean) -> Unit)? = null

    fun findNext(forward: Boolean) {
        findNextAction?.invoke(forward)
    }
}

internal data class DiffSearchState(
    val query: String = "",
    val controller: DiffSearchController? = null,
    val onMatchCountChange: (Int) -> Unit = {},
    val onMatchIndexChange: (Int) -> Unit = {}
)

@Suppress("UnusedParameter")
private class DiffBridge(
    private val onRenderFinished: () -> Unit = {}
) {
    @JavascriptInterface
    fun onRenderComplete(fileCount: Int, hunkCount: Int) {
        onRenderFinished()
    }

    @JavascriptInterface
    fun onError(message: String) {
        // Logged via JS console
    }
}

/**
 * Android WebView host for rendering code diffs using `@pierre/diffs`.
 * Uses [WebViewAssetLoader] to load bundled assets securely offline without CORS issues.
 */
@Composable
internal fun PierreDiffView(
    rawDiff: String,
    settings: DiffDisplaySettings,
    search: DiffSearchState = DiffSearchState(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val (bgColorHex, fgColorHex) = remember(surfaceColor, onSurfaceColor) {
        val bgHex = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and surfaceColor.toArgb())
        val fgHex = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and onSurfaceColor.toArgb())
        bgHex to fgHex
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isPageLoaded by remember { mutableStateOf(false) }

    val bridge = remember(webViewRef, search.query) {
        DiffBridge {
            if (search.query.isNotBlank()) {
                webViewRef?.post {
                    webViewRef?.findAllAsync(search.query)
                }
            }
        }
    }
    val assetLoader = remember {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }

    DisposableEffect(webViewRef, search.controller) {
        search.controller?.findNextAction = { forward ->
            webViewRef?.findNext(forward)
        }
        onDispose {
            search.controller?.findNextAction = null
        }
    }

    DisposableEffect(webViewRef, search.onMatchCountChange, search.onMatchIndexChange) {
        webViewRef?.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            if (isDoneCounting) {
                search.onMatchCountChange(numberOfMatches)
                search.onMatchIndexChange(if (numberOfMatches > 0) activeMatchOrdinal else 0)
            }
        }
        onDispose {
            webViewRef?.setFindListener(null)
        }
    }

    ManageWebViewLifecycle(webViewRef)
    SyncDiffState(webViewRef, isPageLoaded, rawDiff, settings)
    SyncThemeAndStyle(webViewRef, isPageLoaded, settings, bgColorHex, fgColorHex)
    SyncSearch(webViewRef, isPageLoaded, search)

    Box(modifier = modifier.fillMaxSize().background(surfaceColor)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                createConfiguredWebView(ctx, bridge, assetLoader) {
                    isPageLoaded = true
                }.also { webViewRef = it }
            }
        )
    }
}

@Composable
private fun ManageWebViewLifecycle(webViewRef: WebView?) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, webViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webViewRef?.onPause()
                Lifecycle.Event.ON_RESUME -> webViewRef?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webViewRef?.let { wv ->
                wv.stopLoading()
                wv.clearMatches()
                wv.destroy()
            }
        }
    }
}

@Composable
private fun SyncDiffState(
    webView: WebView?,
    isPageLoaded: Boolean,
    rawDiff: String,
    settings: DiffDisplaySettings
) {
    LaunchedEffect(rawDiff, isPageLoaded) {
        if (webView == null || !isPageLoaded) return@LaunchedEffect
        val options = JSONObject().apply {
            put("isDark", settings.isDarkTheme)
            put("diffStyle", "unified")
            put("showLineNumbers", settings.showLineNumbers)
            put("isWordDiffEnabled", settings.isWordDiffEnabled)
        }
        val optionsJson = options.toString()
        val escapedPatch = JSONObject.quote(rawDiff)
        val script = "window.diffViewer?.renderPatch($escapedPatch, '$optionsJson');"
        webView.evaluateJavascript(script, null)
    }
}

@Composable
private fun SyncThemeAndStyle(
    webView: WebView?,
    isPageLoaded: Boolean,
    settings: DiffDisplaySettings,
    bgColorHex: String,
    fgColorHex: String
) {
    LaunchedEffect(settings.isDarkTheme, bgColorHex, fgColorHex, isPageLoaded) {
        if (webView == null || !isPageLoaded) return@LaunchedEffect
        val script = "window.diffViewer?.updateTheme(${settings.isDarkTheme}, '$bgColorHex', '$fgColorHex');"
        webView.evaluateJavascript(script, null)
    }

    LaunchedEffect(settings.showLineNumbers, isPageLoaded) {
        if (webView == null || !isPageLoaded) return@LaunchedEffect
        webView.evaluateJavascript("window.diffViewer?.setLineNumbers(${settings.showLineNumbers});", null)
    }

    LaunchedEffect(settings.isWordDiffEnabled, isPageLoaded) {
        if (webView == null || !isPageLoaded) return@LaunchedEffect
        webView.evaluateJavascript("window.diffViewer?.setWordDiff(${settings.isWordDiffEnabled});", null)
    }
}

@Composable
private fun SyncSearch(
    webView: WebView?,
    isPageLoaded: Boolean,
    search: DiffSearchState
) {
    LaunchedEffect(search.query, isPageLoaded) {
        if (webView == null || !isPageLoaded) return@LaunchedEffect
        if (search.query.isBlank()) {
            webView.clearMatches()
            search.onMatchCountChange(0)
            search.onMatchIndexChange(0)
        } else {
            webView.findAllAsync(search.query)
        }
    }
}

@SuppressLint("ViewConstructor")
private class ScrollableDiffWebView(context: android.content.Context) : WebView(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(event)
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createConfiguredWebView(
    context: android.content.Context,
    bridge: DiffBridge,
    assetLoader: WebViewAssetLoader,
    onLoaded: () -> Unit
): WebView {
    return ScrollableDiffWebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(Color.TRANSPARENT)
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = true

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        addJavascriptInterface(bridge, "AndroidDiffBridge")

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onLoaded()
            }
        }

        loadUrl(AssetUrl)
    }
}
