package com.example.videolinkextractor

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.util.Locale

object VideoDetector {

    private val videoExtensions = listOf(
        ".mp4", ".m4v", ".webm", ".mov", ".mkv", ".avi",
        ".m3u8", ".mpd", ".ts"
    )

    private val videoContentTypes = listOf(
        "video/",
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl",
        "application/dash+xml"
    )

    fun detect(url: String, contentType: String? = null): VideoLink? {
        val clean = url.substringBefore('#').trim()
        if (clean.isBlank()) return null

        val lower = clean.lowercase(Locale.US)
        if (lower.startsWith("blob:") || lower.startsWith("data:")) return null

        val ct = contentType?.lowercase(Locale.US).orEmpty()

        val type = when {
            lower.contains(".m3u8") || ct.contains("mpegurl") -> "M3U8"
            lower.contains(".mpd") || ct.contains("dash+xml") -> "MPD"
            lower.contains(".mp4") -> "MP4"
            lower.contains(".webm") -> "WebM"
            lower.contains(".mov") -> "MOV"
            lower.contains(".m4v") -> "M4V"
            lower.contains(".mkv") -> "MKV"
            lower.contains(".avi") -> "AVI"
            lower.contains(".ts") -> "MPEG-TS"
            ct.startsWith("video/") -> "VIDEO"
            else -> return null
        }

        return VideoLink(clean, type)
    }

    fun detect(request: WebResourceRequest): VideoLink? =
        detect(request.url.toString(), request.requestHeaders["Accept"])
}
