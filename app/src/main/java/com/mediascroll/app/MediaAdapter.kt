package com.mediascroll.app

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.mediascroll.app.model.MediaItem

class MediaAdapter(
    private val context: Context,
    private val player: ExoPlayer
) : ListAdapter<MediaItem, RecyclerView.ViewHolder>(Diff()) {

    companion object {
        private const val TYPE_IMAGE = 0
        private const val TYPE_VIDEO = 1
    }

    // Track which position the player should be attached to
    private var activePosition = RecyclerView.NO_POSITION

    // Map of position -> PlayerView for currently bound video holders
    private val playerViewMap = mutableMapOf<Int, PlayerView>()

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).isVideo) TYPE_VIDEO else TYPE_IMAGE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(context)
        return if (viewType == TYPE_VIDEO) {
            VideoVH(inflater.inflate(R.layout.item_video, parent, false))
        } else {
            ImageVH(inflater.inflate(R.layout.item_image, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is ImageVH -> {
                Glide.with(context)
                    .load(item.uri)
                    .centerCrop()
                    .into(holder.imageView)
            }
            is VideoVH -> {
                playerViewMap[position] = holder.playerView
                holder.playerView.useController = false
                // Attach player only if this is the active position
                if (position == activePosition) {
                    holder.playerView.player = player
                } else {
                    holder.playerView.player = null
                }
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is VideoVH) {
            // Remove from map when recycled
            val entry = playerViewMap.entries.find { it.value == holder.playerView }
            entry?.let {
                if (it.key != activePosition) {
                    playerViewMap.remove(it.key)
                }
                holder.playerView.player = null
            }
        }
    }

    /**
     * Called when a new item becomes visible. Switches player to that item.
     */
    fun onItemVisible(position: Int) {
        if (position < 0 || position >= itemCount) return

        // Detach from previous video view
        playerViewMap[activePosition]?.player = null

        activePosition = position
        val item = getItem(position)

        if (item.isVideo) {
            // Prepare and start playback
            player.setMediaItem(ExoMediaItem.fromUri(item.uri))
            player.prepare()
            player.playWhenReady = true
            player.repeatMode = ExoPlayer.REPEAT_MODE_ONE
            // Attach to the view if it's already bound
            playerViewMap[position]?.player = player
        } else {
            // Stop video playback when showing an image
            player.stop()
            player.clearMediaItems()
        }
    }

    fun pausePlayback() {
        player.pause()
    }

    fun resumePlayback() {
        if (activePosition != RecyclerView.NO_POSITION &&
            activePosition < itemCount &&
            getItem(activePosition).isVideo
        ) {
            player.playWhenReady = true
        }
    }

    inner class ImageVH(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageView)
    }

    inner class VideoVH(view: View) : RecyclerView.ViewHolder(view) {
        val playerView: PlayerView = view.findViewById(R.id.playerView)
    }

    class Diff : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean =
            oldItem.uri == newItem.uri

        override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean =
            oldItem == newItem
    }
}
