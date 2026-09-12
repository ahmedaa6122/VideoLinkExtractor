package com.example.videolinkextractor

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlEditText: TextInputEditText
    private lateinit var statusText: TextView
    private lateinit var adapter: VideoLinkAdapter

    private val jsInterface = object {
        @JavascriptInterface
        fun onMediaUrl(url: String) {
            runOnUiThread { addDetected(url) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        urlEditText = findViewById(R.id.urlEditText)
        statusText = findViewById(R.id.statusText)

        adapter = VideoLinkAdapter(mutableListOf())
        findViewById<RecyclerView>(R.id.resultRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        findViewById<MaterialButton>(R.id.goButton).setOnClickListener {
            loadUrl()
        }

        findViewById<MaterialButton>(R.id.clearButton).setOnClickListener {
            adapter.clear()
            statusText.text = "تم مسح الروابط"
        }

        configureWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowFileAccess = false
            allowContentAccess = true
            userAgentString = WebSettings.getDefaultUserAgent(this@MainActivity)
        }

        webView.addJavascriptInterface(jsInterface, "VideoExtractor")

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                statusText.text = "جاري تحليل الصفحة..."
            }

            override fun onPageFinished(view: WebView, url: String) {
                statusText.text = "تم تحميل الصفحة — شغّل الفيديو لاكتشاف رابط الوسائط"
                injectDetector(view)
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                VideoDetector.detect(request)?.let {
                    runOnUiThread { addDetected(it.url, it.type) }
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

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }

        adapter.clear()
        statusText.text = "جاري فتح الرابط..."
        webView.loadUrl(url)
    }

    private fun injectDetector(view: WebView) {
        val script = """
            (function() {
                if (window.__videoExtractorInstalled) return;
                window.__videoExtractorInstalled = true;

                function send(u) {
                    try {
                        if (!u || u.indexOf('blob:') === 0 || u.indexOf('data:') === 0) return;
                        var l = u.toLowerCase();
                        if (
                            l.indexOf('.mp4') >= 0 ||
                            l.indexOf('.m4v') >= 0 ||
                            l.indexOf('.webm') >= 0 ||
                            l.indexOf('.mov') >= 0 ||
                            l.indexOf('.m3u8') >= 0 ||
                            l.indexOf('.mpd') >= 0 ||
                            l.indexOf('.ts') >= 0
                        ) {
                            VideoExtractor.onMediaUrl(new URL(u, location.href).href);
                        }
                    } catch(e) {}
                }

                function scan() {
                    document.querySelectorAll('video, audio, source').forEach(function(e) {
                        send(e.currentSrc);
                        send(e.src);
                    });

                    try {
                        performance.getEntriesByType('resource').forEach(function(e) {
                            send(e.name);
                        });
                    } catch(e) {}
                }

                scan();
                setInterval(scan, 1500);

                const observer = new MutationObserver(scan);
                observer.observe(document.documentElement || document, {
                    childList: true,
                    subtree: true,
                    attributes: true,
                    attributeFilter: ['src']
                });

                document.addEventListener('play', function(e) {
                    if (e.target && (e.target.tagName === 'VIDEO' || e.target.tagName === 'AUDIO')) {
                        send(e.target.currentSrc);
                        send(e.target.src);
                    }
                    setTimeout(scan, 300);
                }, true);
            })();
        """.trimIndent()

        view.evaluateJavascript(script, null)
    }

    private fun addDetected(url: String, type: String? = null) {
        val detected = VideoDetector.detect(url, type)
        if (detected != null && adapter.add(detected)) {
            statusText.text = "تم اكتشاف ${detected.type}"
        }
    }

    override fun onDestroy() {
        webView.removeJavascriptInterface("VideoExtractor")
        webView.destroy()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
