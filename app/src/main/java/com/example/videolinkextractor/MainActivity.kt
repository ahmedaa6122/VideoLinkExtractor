package com.example.videolinkextractor

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import java.io.ByteArrayInputStream
import java.net.URI

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var urlEditText: TextInputEditText
    private lateinit var statusText: TextView
    private lateinit var linksFab: ExtendedFloatingActionButton
    private lateinit var adapter: VideoLinkAdapter

    private val jsInterface = object {
        @JavascriptInterface
        fun onMediaUrl(url: String) = runOnUiThread { addDetected(url) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        urlEditText = findViewById(R.id.urlEditText)
        statusText = findViewById(R.id.statusText)
        linksFab = findViewById(R.id.linksFab)

        adapter = VideoLinkAdapter(mutableListOf())
        findViewById<MaterialButton>(R.id.goButton).setOnClickListener { loadUrl() }
        findViewById<MaterialButton>(R.id.clearButton).setOnClickListener { clearLinks() }
        findViewById<MaterialButton>(R.id.backButton).setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }
        findViewById<MaterialButton>(R.id.forwardButton).setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }
        findViewById<MaterialButton>(R.id.refreshButton).setOnClickListener { webView.reload() }
        linksFab.setOnClickListener { showLinksBottomSheet() }

        configureWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            // Never autoplay. A user gesture is required to start media playback.
            mediaPlaybackRequiresUserGesture = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowFileAccess = false
            allowContentAccess = true
            userAgentString = WebSettings.getDefaultUserAgent(this@MainActivity)
        }

        webView.addJavascriptInterface(jsInterface, "VideoExtractor")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean = false

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult): Boolean {
                result.cancel()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult): Boolean {
                result.cancel()
                return true
            }

            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult
            ): Boolean {
                result.cancel()
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                statusText.text = "جاري تحليل الصفحة..."
                urlEditText.setText(url)
                injectDetector(view)
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                injectDetector(view)
            }

            override fun onPageFinished(view: WebView, url: String) {
                statusText.text = if (adapter.itemCount > 0) {
                    "تم اكتشاف ${adapter.itemCount} رابط وسائط"
                } else {
                    "تم تحليل الصفحة — لا حاجة لتشغيل الفيديو"
                }
                injectDetector(view)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme?.lowercase()
                return if (scheme == "http" || scheme == "https") false else true
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url.toString()
                if (isBlockedAd(url)) return emptyResponse()

                VideoDetector.detect(request)?.let { detected ->
                    runOnUiThread { addDetected(detected.url, detected.type) }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
    }

    private fun loadUrl() {
        var url = urlEditText.text?.toString()?.trim().orEmpty()
        if (url.isBlank()) {
            urlEditText.error = "أدخل الرابط"
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"

        clearLinks(false)
        statusText.text = "جاري فتح الصفحة..."
        webView.loadUrl(url)
    }

    private fun injectDetector(view: WebView) {
        val script = """
            (function() {
                if (window.__videoExtractorInstalled) return;
                window.__videoExtractorInstalled = true;

                // Block common popup mechanisms without preventing normal page navigation.
                window.open = function() { return null; };
                window.alert = function() {};
                window.confirm = function() { return false; };
                window.prompt = function() { return null; };

                function send(u) {
                    try {
                        if (!u || u.indexOf('blob:') === 0 || u.indexOf('data:') === 0) return;
                        var absolute = new URL(u, location.href).href;
                        var l = absolute.toLowerCase();
                        if (
                            l.indexOf('.mp4') >= 0 || l.indexOf('.m4v') >= 0 ||
                            l.indexOf('.webm') >= 0 || l.indexOf('.mov') >= 0 ||
                            l.indexOf('.mkv') >= 0 || l.indexOf('.avi') >= 0 ||
                            l.indexOf('.m3u8') >= 0 || l.indexOf('.mpd') >= 0 ||
                            l.indexOf('.ts') >= 0 || l.indexOf('mime=video') >= 0 ||
                            l.indexOf('type=video') >= 0 || l.indexOf('format=mp4') >= 0
                        ) VideoExtractor.onMediaUrl(absolute);
                    } catch(e) {}
                }

                function scan() {
                    try {
                        document.querySelectorAll('video, audio, source').forEach(function(e) {
                            send(e.currentSrc); send(e.src);
                            e.removeAttribute('autoplay');
                            e.autoplay = false;
                        });
                    } catch(e) {}

                    try {
                        performance.getEntriesByType('resource').forEach(function(e) { send(e.name); });
                    } catch(e) {}

                    try {
                        document.querySelectorAll('a[href], link[href]').forEach(function(e) { send(e.href); });
                    } catch(e) {}
                }

                scan();
                setTimeout(scan, 100);
                setTimeout(scan, 500);
                setTimeout(scan, 1500);
                setInterval(scan, 2500);

                new MutationObserver(scan).observe(document.documentElement || document, {
                    childList: true, subtree: true, attributes: true, attributeFilter: ['src', 'href']
                });
            })();
        """.trimIndent()
        view.evaluateJavascript(script, null)
    }

    private fun addDetected(url: String, type: String? = null) {
        val detected = VideoDetector.detect(url, type) ?: return
        if (adapter.add(detected)) {
            linksFab.text = "الروابط (${adapter.itemCount})"
            linksFab.show()
            statusText.text = "تم التقاط ${adapter.itemCount} رابط — لم يتم تشغيل الفيديو"
        }
    }

    private fun clearLinks(showStatus: Boolean = true) {
        adapter.clear()
        linksFab.text = "الروابط (0)"
        linksFab.hide()
        if (showStatus) statusText.text = "تم مسح الروابط"
    }

    private fun showLinksBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val content = layoutInflater.inflate(R.layout.bottom_sheet_links, null)
        content.findViewById<RecyclerView>(R.id.linksRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
        content.findViewById<MaterialButton>(R.id.closeButton).setOnClickListener { dialog.dismiss() }
        dialog.setContentView(content)
        dialog.show()
    }

    private fun isBlockedAd(rawUrl: String): Boolean {
        return try {
            val uri = URI(rawUrl)
            val host = uri.host?.lowercase().orEmpty()
            val value = rawUrl.lowercase()
            val hosts = listOf(
                "doubleclick.net", "googlesyndication.com", "googleadservices.com",
                "adservice.google.com", "adsystem.com", "adnxs.com", "popads.net",
                "popcash.net", "propellerads.com", "exoclick.com", "trafficjunky.com",
                "taboola.com", "outbrain.com", "adsterra.com", "hilltopads.net"
            )
            hosts.any { host == it || host.endsWith(".$it") } ||
                listOf("/ads/", "/adserver", "/advert", "googlesyndication", "doubleclick").any { value.contains(it) }
        } catch (_: Exception) {
            false
        }
    }

    private fun emptyResponse() = WebResourceResponse(
        "text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0))
    )

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.removeJavascriptInterface("VideoExtractor")
        webView.destroy()
        super.onDestroy()
    }
}
