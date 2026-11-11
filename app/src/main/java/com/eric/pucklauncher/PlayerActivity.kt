package com.eric.pucklauncher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.eric.pucklauncher.data.OnHockeyParser
import com.eric.pucklauncher.data.Stream
import java.io.ByteArrayInputStream

/** Beta in-app player: full-screen WebView with popup, redirect and ad-domain blocking. */
class PlayerActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_EMBED = "embed"
        private const val EXTRA_TITLE = "title"

        fun intent(ctx: Context, s: Stream, title: String) = Intent(ctx, PlayerActivity::class.java)
            .putExtra(EXTRA_URL, s.url).putExtra(EXTRA_EMBED, s.embedUrl)
            .putExtra(EXTRA_TITLE, "$title · ${s.channel.ifBlank { s.group }} · ${s.host}")

        /** Ad / popunder / tracker domains seen on onhockey.tv and its stream hosts (suffix match). */
        val BLOCKED = listOf(
            "debitm.org", "nattierfalsest.rest", "posamari.me", "unfortunateheart.com", "lbeato.store",
            "dtscout.com", "klook.com", "togglevpn.app", "1xbet.com", "1xbet.org", "amung.us",
            "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
            "popads.net", "popcash.net", "propellerads.com", "adsterra.com", "a-ads.com", "exoclick.com",
            "juicyads.com", "hilltopads.net", "adcash.com", "onclickads.net", "admaven.com", "clickadu.com",
            "trafficjunky.net", "pushhouse.io", "realsrv.com", "mgid.com", "taboola.com", "outbrain.com",
        )

        fun hostMatches(host: String?, domains: Collection<String>): Boolean =
            host != null && domains.any { host == it || host.endsWith(".$it") }
    }

    private lateinit var web: WebView
    private lateinit var content: LinearLayout
    private lateinit var fullscreen: FrameLayout
    private lateinit var modeButton: Button
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var useSitePage = false
    private lateinit var pageUrl: String
    private var embedUrl: String? = null

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        pageUrl = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        embedUrl = intent.getStringExtra(EXTRA_EMBED)
        useSitePage = savedInstanceState?.getBoolean("site") ?: (embedUrl == null)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""

        val allowedHosts = listOfNotNull("onhockey.tv", Uri.parse(pageUrl).host, embedUrl?.let { Uri.parse(it).host })
            .map { it.removePrefix("www.") }

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fullscreen = FrameLayout(this).apply { setBackgroundColor(Color.BLACK); visibility = View.GONE }

        fun btn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label; isAllCaps = false; setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            minHeight = dp(56); minimumHeight = dp(56); minWidth = dp(64)
            setOnClickListener { onClick() }
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF0A0F1A.toInt()); setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        bar.addView(btn("✕ Close") { finish() })
        bar.addView(TextView(this).apply {
            text = title; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD); maxLines = 1; setPadding(dp(12), 0, dp(12), 0)
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(btn("⟳ Reload") { load() })
        modeButton = btn("") { useSitePage = !useSitePage; updateModeLabel(); load() }
        if (embedUrl != null) actions.addView(modeButton)
        actions.addView(btn("Open in browser") { openInBrowser(pageUrl) })
        bar.addView(HorizontalScrollView(this).apply { addView(actions) })
        updateModeLabel()

        web = WebView(this).apply { setBackgroundColor(Color.BLACK) }
        content.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        content.addView(web, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        root.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(fullscreen, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            content.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(true)          // so popups go through onCreateWindow, where we drop them
            javaScriptCanOpenWindowsAutomatically = false
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val scheme = uri.scheme ?: return true
                if (scheme == "about" || scheme == "data" || scheme == "blob") return false
                if (scheme != "http" && scheme != "https") return true // intent://, market://, etc.
                if (!request.isForMainFrame) return false
                return !hostMatches(uri.host?.removePrefix("www."), allowedHosts) // block top-level redirects away
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (hostMatches(request.url.host, BLOCKED)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?) = false
            override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean { result.cancel(); return true }
            override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean { result.cancel(); return true }
            override fun onJsPrompt(view: WebView, url: String?, message: String?, defaultValue: String?, result: JsPromptResult): Boolean { result.cancel(); return true }
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) { callback.onCustomViewHidden(); return }
                customView = view; customCallback = callback
                fullscreen.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                fullscreen.visibility = View.VISIBLE
                content.visibility = View.GONE
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }

            override fun onHideCustomView() = exitFullscreen()
        }

        onBackPressedDispatcher.addCallback(this) {
            if (customView != null) exitFullscreen() else finish()
        }
        load()
    }

    private fun updateModeLabel() {
        modeButton.text = if (useSitePage) "Clean player" else "Site page"
    }

    private fun load() {
        val embed = embedUrl
        if (useSitePage || embed == null) {
            web.loadUrl(pageUrl, mapOf("Referer" to OnHockeyParser.BASE))
        } else {
            val safe = embed.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
            val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                <meta name="referrer" content="origin"><style>html,body{margin:0;height:100%;background:#000;overflow:hidden}
                iframe{position:fixed;inset:0;width:100%;height:100%;border:0}</style></head><body>
                <iframe src="$safe" allow="autoplay; fullscreen; encrypted-media; picture-in-picture" allowfullscreen
                 referrerpolicy="origin" scrolling="no"></iframe></body></html>"""
            // Base URL = onhockey.tv so the stream host sees the same origin/Referer as on the real site.
            web.loadDataWithBaseURL(OnHockeyParser.BASE, html, "text/html", "utf-8", null)
        }
    }

    private fun exitFullscreen() {
        val v = customView ?: return
        fullscreen.removeView(v)
        fullscreen.visibility = View.GONE
        content.visibility = View.VISIBLE
        customView = null
        customCallback?.onCustomViewHidden()
        customCallback = null
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("site", useSitePage)
    }

    override fun onPause() { super.onPause(); if (::web.isInitialized) web.onPause() }
    override fun onResume() { super.onResume(); if (::web.isInitialized) web.onResume() }
    override fun onDestroy() {
        if (::web.isInitialized) { web.stopLoading(); web.destroy() }
        super.onDestroy()
    }
}
