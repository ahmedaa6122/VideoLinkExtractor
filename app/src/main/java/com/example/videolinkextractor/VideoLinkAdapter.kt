package com.example.videolinkextractor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class VideoLinkAdapter(
    private val items: MutableList<VideoLink>
) : RecyclerView.Adapter<VideoLinkAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val type: TextView = view.findViewById(R.id.typeText)
        val url: TextView = view.findViewById(R.id.urlText)
        val copy: MaterialButton = view.findViewById(R.id.copyButton)
        val share: MaterialButton = view.findViewById(R.id.shareButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_video_link, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.type.text = item.type
        holder.url.text = item.url

        holder.copy.setOnClickListener {
            val clipboard = it.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Video URL", item.url))
            Toast.makeText(it.context, "تم نسخ الرابط", Toast.LENGTH_SHORT).show()
        }

        holder.share.setOnClickListener {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, item.url)
            }
            it.context.startActivity(Intent.createChooser(intent, "مشاركة الرابط"))
        }

        holder.url.setOnClickListener {
            it.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
        }
    }

    override fun getItemCount() = items.size

    fun add(link: VideoLink): Boolean {
        if (items.any { it.url == link.url }) return false
        items.add(0, link)
        notifyItemInserted(0)
        return true
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }
}
