package com.mrndtvndv.term.ui.review

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.MotionEvent
import android.view.VelocityTracker
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewAssetLoader
import com.mrndtvndv.term.ui.theme.LocalCustomFontFamily
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Locale
import org.json.JSONObject

private const val AssetUrl = "https://appassets.androidplatform.net/assets/diff-viewer/index.html"
private const val CustomFontPath = "/custom-font/font.ttf"

internal data class DiffDisplaySettings(
    val isDarkTheme: Boolean,
    val showLineNumbers: Boolean,
    val isWordDiffEnabled: Boolean,
    val useCustomFont: Boolean = false,
    val fontVersion: Long = 0L
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

    SetupSearchListeners(webViewRef, search)
    val resolvedSettings = rememberResolvedSettings(context, settings)

    ManageWebViewLifecycle(webViewRef)
    SyncDiffState(webViewRef, isPageLoaded, rawDiff, resolvedSettings)
    SyncThemeAndStyle(webViewRef, isPageLoaded, resolvedSettings, bgColorHex, fgColorHex)
    SyncSearch(webViewRef, isPageLoaded, search)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surfaceColor)
            .nestedScroll(rememberNestedScrollInteropConnection())
    ) {
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
private fun SetupSearchListeners(
    webViewRef: WebView?,
    search: DiffSearchState
) {
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
}

@Composable
private fun rememberResolvedSettings(
    context: android.content.Context,
    settings: DiffDisplaySettings
): DiffDisplaySettings {
    val localCustomFont = LocalCustomFontFamily.current
    return remember(settings, localCustomFont) {
        val useCustom = settings.useCustomFont || (localCustomFont != null)
        val version = if (settings.fontVersion != 0L) {
            settings.fontVersion
        } else if (useCustom) {
            val fontFile = File(context.filesDir, "font.ttf")
            if (fontFile.exists()) fontFile.lastModified() else 0L
        } else {
            0L
        }
        settings.copy(useCustomFont = useCustom, fontVersion = version)
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
            put("useCustomFont", settings.useCustomFont)
            if (settings.useCustomFont) {
                put("fontUrl", "/custom-font/font.ttf?v=${settings.fontVersion}")
            }
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

    LaunchedEffect(settings.useCustomFont, settings.fontVersion, isPageLoaded) {
        if (webView == null || !isPageLoaded) return@LaunchedEffect
        val fontUrl = if (settings.useCustomFont) "/custom-font/font.ttf?v=${settings.fontVersion}" else ""
        webView.evaluateJavascript(
            "window.diffViewer?.setFontFamily(${settings.useCustomFont}, '$fontUrl');",
            null
        )
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
@Suppress("TooManyFunctions")
private class ScrollableDiffWebView(context: android.content.Context) : WebView(context), NestedScrollingChild3 {
    private val childHelper = NestedScrollingChildHelper(this).apply {
        isNestedScrollingEnabled = true
    }
    private var lastY = 0
    private val scrollConsumed = IntArray(2)
    private val scrollOffset = IntArray(2)
    private var nestedOffsetY = 0
    private var velocityTracker: VelocityTracker? = null

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        childHelper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean = childHelper.isNestedScrollingEnabled

    override fun startNestedScroll(axes: Int, type: Int): Boolean =
        childHelper.startNestedScroll(axes, type)

    override fun startNestedScroll(axes: Int): Boolean =
        childHelper.startNestedScroll(axes)

    override fun stopNestedScroll(type: Int) {
        childHelper.stopNestedScroll(type)
    }

    override fun stopNestedScroll() {
        childHelper.stopNestedScroll()
    }

    override fun hasNestedScrollingParent(type: Int): Boolean =
        childHelper.hasNestedScrollingParent(type)

    override fun hasNestedScrollingParent(): Boolean =
        childHelper.hasNestedScrollingParent()

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int,
        consumed: IntArray
    ) {
        childHelper.dispatchNestedScroll(
            dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type, consumed
        )
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean = childHelper.dispatchNestedScroll(
        dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type
    )

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?
    ): Boolean = childHelper.dispatchNestedScroll(
        dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow
    )

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean = childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?
    ): Boolean = childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)

    override fun dispatchNestedFling(
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean
    ): Boolean = childHelper.dispatchNestedFling(velocityX, velocityY, consumed)

    override fun dispatchNestedPreFling(
        velocityX: Float,
        velocityY: Float
    ): Boolean = childHelper.dispatchNestedPreFling(velocityX, velocityY)

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
        val tracker = velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }
        tracker.addMovement(event)

        val motionEvent = MotionEvent.obtain(event)
        val action = event.actionMasked

        if (action == MotionEvent.ACTION_DOWN) {
            nestedOffsetY = 0
        }
        motionEvent.offsetLocation(0f, nestedOffsetY.toFloat())

        val result: Boolean
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                lastY = event.rawY.toInt()
                startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
                parent?.requestDisallowInterceptTouchEvent(true)
                result = super.onTouchEvent(motionEvent)
            }
            MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val rawY = event.rawY.toInt()
                var dy = lastY - rawY

                if (dispatchNestedPreScroll(0, dy, scrollConsumed, scrollOffset, ViewCompat.TYPE_TOUCH)) {
                    dy -= scrollConsumed[1]
                    motionEvent.offsetLocation(0f, -scrollConsumed[1].toFloat())
                    nestedOffsetY += scrollOffset[1]
                }
                lastY = rawY - scrollOffset[1]

                result = super.onTouchEvent(motionEvent)

                dispatchNestedScroll(
                    0, scrollConsumed[1], 0, dy, scrollOffset, ViewCompat.TYPE_TOUCH
                )
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                tracker.computeCurrentVelocity(1000)
                val yVelocity = tracker.yVelocity
                if (yVelocity != 0f) {
                    dispatchNestedPreFling(0f, -yVelocity)
                }
                stopNestedScroll(ViewCompat.TYPE_TOUCH)
                velocityTracker?.recycle()
                velocityTracker = null
                result = super.onTouchEvent(motionEvent)
            }
            else -> {
                result = super.onTouchEvent(motionEvent)
            }
        }
        motionEvent.recycle()
        return result
    }
}

private fun handleCustomFontRequest(
    context: android.content.Context,
    path: String?
): WebResourceResponse? {
    if (path != CustomFontPath) return null
    val fontFile = File(context.filesDir, "font.ttf")
    if (!fontFile.isFile || fontFile.length() <= 0L) return null
    return try {
        WebResourceResponse("font/ttf", null, FileInputStream(fontFile)).apply {
            responseHeaders = mapOf(
                "Access-Control-Allow-Origin" to "*",
                "Cache-Control" to "no-cache"
            )
        }
    } catch (e: IOException) {
        android.util.Log.w("PierreDiffView", "Failed to open custom font", e)
        null
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
                return handleCustomFontRequest(context, request.url.path)
                    ?: assetLoader.shouldInterceptRequest(request.url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onLoaded()
            }
        }

        loadUrl(AssetUrl)
    }
}
