package dev.brahmkshatriya.echo.player

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.drawable.Animatable
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.animation.LinearInterpolator
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.activity.viewModels
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.session.MediaBrowser
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.DOWN
import androidx.recyclerview.widget.ItemTouchHelper.START
import androidx.recyclerview.widget.ItemTouchHelper.UP
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager.VERTICAL
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_DRAGGING
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_SETTLING
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.checkbox.MaterialCheckBox.OnCheckedStateChangedListener
import com.google.android.material.checkbox.MaterialCheckBox.STATE_CHECKED
import dev.brahmkshatriya.echo.MainActivity
import dev.brahmkshatriya.echo.R
import dev.brahmkshatriya.echo.player.PlayerHelper.Companion.toTimeString
import dev.brahmkshatriya.echo.ui.adapters.PlaylistAdapter
import dev.brahmkshatriya.echo.ui.utils.emit
import dev.brahmkshatriya.echo.ui.utils.loadInto
import dev.brahmkshatriya.echo.ui.utils.observe
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.math.max
import kotlin.math.min


@SuppressLint("NotifyDataSetChanged")
fun createPlayer(
    activity: MainActivity
) {

    val playerBinding = activity.binding.bottomPlayer
    val playlistBinding = playerBinding.bottomPlaylist

    val container = activity.binding.bottomPlayerContainer as View
    val playlistContainer = playerBinding.bottomPlaylistContainer as View

    val playerViewModel: PlayerViewModel by activity.viewModels()
    val uiViewModel: PlayerUIViewModel by activity.viewModels()


    // Apply the UI Changes

    val navView = activity.binding.navView
    val bottomPlayerBehavior = BottomSheetBehavior.from(container)
    val bottomPlaylistBehavior = BottomSheetBehavior.from(playlistContainer)

    container.setOnClickListener {
        bottomPlayerBehavior.state = STATE_EXPANDED
    }

    bottomPlaylistBehavior.addBottomSheetCallback(object :
        BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            bottomPlayerBehavior.isDraggable = newState == STATE_COLLAPSED
            if (newState == STATE_SETTLING || newState == STATE_DRAGGING) return
            playerBinding.expandedSeekBar.isEnabled = newState != STATE_EXPANDED
            PlayerBackButtonHelper.playlistState.value = newState
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            val offset = 1 - slideOffset
            playlistBinding.root.translationY = -uiViewModel.playlistTranslationY * offset
            playlistBinding.playlistRecyclerContainer.alpha = slideOffset
            playerBinding.expandedBackground.alpha = slideOffset
        }
    })

    val collapsedCoverSize = activity.resources.getDimension(R.dimen.collapsed_cover_size).toInt()
    bottomPlayerBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == STATE_SETTLING || newState == STATE_DRAGGING) return
            PlayerBackButtonHelper.playerSheetState.value = newState
            when (newState) {
                STATE_HIDDEN -> playerViewModel.clearQueue()
                else -> bottomPlayerBehavior.isHideable = newState != STATE_EXPANDED
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            val offset = max(0f, slideOffset)
            playerBinding.collapsedContainer.translationY = -collapsedCoverSize * offset
            playerBinding.expandedContainer.translationY = collapsedCoverSize * (1 - offset)
            navView.translationY = uiViewModel.bottomNavTranslateY * offset
        }
    })

    PlayerBackButtonHelper.bottomSheetBehavior = bottomPlayerBehavior
    PlayerBackButtonHelper.playlistBehavior = bottomPlaylistBehavior

    container.post {
        bottomPlayerBehavior.state = PlayerBackButtonHelper.playerSheetState.value
        bottomPlaylistBehavior.state = PlayerBackButtonHelper.playlistState.value
        container.translationY = 0f
    }
    activity.observe(playerViewModel.fromNotification) {
        if (it) bottomPlayerBehavior.state = STATE_EXPANDED
    }

    playerBinding.playerClose.setOnClickListener {
        bottomPlayerBehavior.state = STATE_HIDDEN
    }

    //Connect the UI to the ViewModel

    fun <T> MutableSharedFlow<T>.emit(block: () -> T) {
        activity.emit(this, block)
    }

    val playPauseListener = object : OnCheckedStateChangedListener {
        var enabled = true
        override fun onCheckedStateChangedListener(checkBox: MaterialCheckBox, state: Int) {
            if (enabled) playerViewModel.playPause.emit {
                when (state) {
                    STATE_CHECKED -> true
                    else -> false
                }
            }
        }
    }

    playerBinding.trackPlayPause.addOnCheckedStateChangedListener(playPauseListener)
    playerBinding.collapsedTrackPlayPause.addOnCheckedStateChangedListener(playPauseListener)

    playerBinding.trackNext.setOnClickListener {
        playerViewModel.seekToNext.emit {}
        (playerBinding.trackNext.icon as Animatable).start()
    }

    playerBinding.trackPrevious.setOnClickListener {
        playerViewModel.seekToPrevious.emit {}
        (playerBinding.trackPrevious.icon as Animatable).start()
    }

    var expandedAnimator: ObjectAnimator? = null
    var collapsedAnimator: ObjectAnimator? = null
    playerBinding.expandedSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
        var touched = false
        override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
            if (touched) playerBinding.trackCurrentTime.text = p1.toLong().toTimeString()
        }

        override fun onStartTrackingTouch(p0: SeekBar?) {
            touched = true
            expandedAnimator?.cancel()
        }

        override fun onStopTrackingTouch(p0: SeekBar?) {
            touched = false
            p0?.progress?.let {
                playerViewModel.seekTo.emit { it.toLong() }
            }
        }
    })

    val drawables = listOf(
        AppCompatResources.getDrawable(activity, R.drawable.ic_repeat_to_repeat_one_40dp),
        AppCompatResources.getDrawable(activity, R.drawable.ic_repeat_one_to_no_repeat_40dp),
        AppCompatResources.getDrawable(activity, R.drawable.ic_no_repeat_to_repeat_40dp)
    )
    val repeatModes = listOf(
        REPEAT_MODE_ONE, REPEAT_MODE_OFF, REPEAT_MODE_ALL
    )

    playerBinding.trackRepeat.setOnClickListener {
        playerBinding.trackRepeat.icon = when (playerBinding.trackRepeat.icon) {
            drawables[0] -> drawables[1].apply {
                ObjectAnimator.ofFloat(it, "alpha", 1f, 0.4f).setDuration(400).start()
            }

            drawables[1] -> drawables[2].apply {
                ObjectAnimator.ofFloat(it, "alpha", 0.4f, 1f).setDuration(400).start()
            }

            else -> drawables[0]
        }
        (playerBinding.trackRepeat.icon as Animatable).start()
        playerViewModel.repeat.emit {
            repeatModes[drawables.indexOf(playerBinding.trackRepeat.icon)]
        }
    }

    val repeatMode = uiViewModel.repeatMode
    playerBinding.trackRepeat.icon = drawables[repeatModes.indexOf(repeatMode)]
    playerBinding.trackRepeat.alpha = if (repeatMode == REPEAT_MODE_OFF) 0.4f else 1f
    (playerBinding.trackRepeat.icon as Animatable).start()


    val linearLayoutManager = LinearLayoutManager(activity, VERTICAL, false)
    val callback = object : ItemTouchHelper.SimpleCallback(UP or DOWN, START) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            playerViewModel.moveQueueItems(
                viewHolder.bindingAdapterPosition, target.bindingAdapterPosition
            )
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            playerViewModel.removeQueueItem(
                viewHolder.bindingAdapterPosition
            )
        }
    }
    val touchHelper = ItemTouchHelper(callback)
    val adapter = PlaylistAdapter(object : PlaylistAdapter.Callback() {
        override fun onDragHandleClicked(viewHolder: PlaylistAdapter.ViewHolder) {
            touchHelper.startDrag(viewHolder)
        }

        override fun onItemClicked(position: Int) {
            playerViewModel.audioIndexFlow.emit { position }
        }

        override fun onItemClosedClicked(position: Int) {
            playerViewModel.removeQueueItem(position)
        }
    })

    playlistBinding.playlistRecycler.apply {
        layoutManager = linearLayoutManager
        this.adapter = adapter
        touchHelper.attachToRecyclerView(this)
    }

    activity.apply {
        observe(uiViewModel.track) { track ->
            track ?: return@observe

            playerBinding.collapsedTrackTitle.text = track.title
            playerBinding.expandedTrackTitle.text = track.title

            track.artists.joinToString(" ") { it.name }.run {
                playerBinding.collapsedTrackAuthor.text = this
                playerBinding.expandedTrackAuthor.text = this
            }
            track.cover?.run {
                loadInto(playerBinding.collapsedTrackCover)
                loadInto(playerBinding.expandedTrackCover)
            }

            container.post {
                if (bottomPlayerBehavior.state == STATE_HIDDEN) {
                    bottomPlayerBehavior.state = STATE_COLLAPSED
                    bottomPlaylistBehavior.state = STATE_COLLAPSED
                    bottomPlayerBehavior.isDraggable = true
                }
            }
        }

        observe(uiViewModel.nextEnabled) {
            playerBinding.trackNext.isEnabled = it
        }
        observe(uiViewModel.previousEnabled) {
            playerBinding.trackPrevious.isEnabled = it
        }

        observe(uiViewModel.isPlaying) {
            playPauseListener.enabled = false
            playerBinding.trackPlayPause.isChecked = it
            playerBinding.collapsedTrackPlayPause.isChecked = it
            playPauseListener.enabled = true
        }

        observe(uiViewModel.buffering) {
            playerBinding.collapsedSeekBar.isIndeterminate = it
            playerBinding.expandedSeekBar.isEnabled = !it
            playerBinding.trackPlayPause.isEnabled = !it
            playerBinding.collapsedTrackPlayPause.isEnabled = !it
        }

        observe(uiViewModel.totalDuration) {
            playerBinding.collapsedSeekBar.max = it
            playerBinding.expandedSeekBar.max = it

            playerBinding.trackTotalTime.text = it.toLong().toTimeString()
        }

        observe(uiViewModel.progress) { (current, buffered) ->
            if (!playerBinding.expandedSeekBar.isPressed) {
                playerBinding.collapsedSeekBar.secondaryProgress = buffered
                playerBinding.expandedSeekBar.secondaryProgress = buffered

                var old = playerBinding.expandedSeekBar.progress
                if (old == 0) old = current
                val duration = min(1000L, max(0L, (current - old).toLong()))
                playerBinding.collapsedSeekBar.apply {
                    collapsedAnimator?.cancel()
                    collapsedAnimator =
                        ObjectAnimator.ofInt(this, "progress", current).setDuration(duration)
                    collapsedAnimator?.interpolator = LinearInterpolator()
                    collapsedAnimator?.start()
                }
                playerBinding.expandedSeekBar.apply {
                    expandedAnimator?.cancel()
                    expandedAnimator =
                        ObjectAnimator.ofInt(this, "progress", current).setDuration(duration)
                    expandedAnimator?.interpolator = LinearInterpolator()
                    expandedAnimator?.start()
                }
                playerBinding.trackCurrentTime.text = current.toLong().toTimeString()
            }
        }
        observe(uiViewModel.playlist) {
            val viewHolder =
                playlistBinding.playlistRecycler.findViewHolderForAdapterPosition(it) as PlaylistAdapter.ViewHolder?
            adapter.setCurrent(viewHolder)
        }

        observe(playerViewModel.clearQueueFlow) {
            adapter.notifyDataSetChanged()
            println("Cleared")
            container.post {
                bottomPlayerBehavior.isDraggable = true
                container.post {
                    if (bottomPlayerBehavior.state != STATE_HIDDEN) {
                        bottomPlayerBehavior.state = STATE_HIDDEN
                        println("State changed")
                    }
                }
            }
        }

        observe(playerViewModel.audioQueueFlow) {
            (it.localConfiguration?.tag as? Int)?.let { index ->
                adapter.notifyItemInserted(index)
            }
        }
    }
}

fun attachPlayerView(
    activity: MainActivity
) {

    val playerBinding = activity.binding.bottomPlayer
    val playlistBinding = playerBinding.bottomPlaylist

    val container = activity.binding.bottomPlayerContainer as View
    val playlistContainer = playerBinding.bottomPlaylistContainer as View

    val uiViewModel: PlayerUIViewModel by activity.viewModels()

    val bottomPlayerBehavior = BottomSheetBehavior.from(container)
    val playlistBehavior = BottomSheetBehavior.from(playlistContainer)

    val peekHeight = activity.resources.getDimension(R.dimen.bottom_player_peek_height).toInt()
    val playlistPeekHeight = activity.resources.getDimension(R.dimen.playlist_peek_height).toInt()

    val navView = activity.binding.navView

    val orientation: Int = activity.resources.configuration.orientation

    ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
        val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        bottomPlayerBehavior.peekHeight = peekHeight + systemInsets.bottom
        playlistBehavior.peekHeight = playlistPeekHeight + systemInsets.bottom
        insets
    }

    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
        navView.post {
            uiViewModel.bottomNavTranslateY = navView.height
        }
    } else {
        navView.post {
            uiViewModel.bottomNavTranslateY = -navView.height
        }

        // Need to manually handle system insets for landscape mode
        // since we can't use the fitSystemWindows on the root view,
        // or the playlist bottom sheet will be hidden behind the navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(
            playerBinding.expandedTrackCoverContainer
        ) { view, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.updateLayoutParams<MarginLayoutParams> {
                topMargin = systemInsets.top
                bottomMargin = systemInsets.bottom
                leftMargin = systemInsets.left
            }

            playerBinding.collapsedContainer.updateLayoutParams<MarginLayoutParams> {
                leftMargin = systemInsets.left
                rightMargin = systemInsets.right
            }

            playerBinding.collapsePlayer.updateLayoutParams<MarginLayoutParams> {
                topMargin = systemInsets.top
            }

            playerBinding.expandedTrackInfoContainer.updatePadding(
                right = systemInsets.right,
                top = systemInsets.top,
                bottom = systemInsets.bottom
            )

            playerBinding.bottomPlaylistContainer.updatePadding(
                left = 0,
                right = 0,
                bottom = systemInsets.bottom
            )

            uiViewModel.bottomNavTranslateY = -(navView.height + systemInsets.top)
            insets
        }
    }


    ViewCompat.setOnApplyWindowInsetsListener(playlistBinding.root) { _, insets ->
        val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        uiViewModel.playlistTranslationY = systemInsets.top
        playlistBinding.root.translationY = -uiViewModel.playlistTranslationY.toFloat()
        insets
    }
}


fun startPlayer(activity: MainActivity, player: MediaBrowser) {

    val playerViewModel: PlayerViewModel by activity.viewModels()
    val uiViewModel: PlayerUIViewModel by activity.viewModels()

    val listener = PlayerListener(player, uiViewModel)
    player.addListener(listener)
    player.currentMediaItem?.let {
        listener.update(it.mediaId)
        activity.emit(uiViewModel.playlist) {
            player.currentMediaItemIndex
        }
    }

    activity.apply {
        observe(playerViewModel.playPause) {
            if (it) player.play() else player.pause()
        }
        observe(playerViewModel.seekToPrevious) {
            player.seekToPrevious()
            player.playWhenReady = true
        }
        observe(playerViewModel.seekToNext) {
            player.seekToNext()
            player.playWhenReady = true
        }
        observe(playerViewModel.audioIndexFlow) {
            if (it >= 0) {
                player.seekToDefaultPosition(it)
                uiViewModel.playlist.emit(it)
            }
        }
        observe(playerViewModel.seekTo) {
            player.seekTo(it)
        }
        observe(playerViewModel.repeat) {
            player.repeatMode = it
        }
        observe(playerViewModel.audioQueueFlow) {
            player.addMediaItem(it)
            player.prepare()
            player.playWhenReady = true
        }
        observe(playerViewModel.clearQueueFlow) {
            player.pause()
            player.clearMediaItems()
            player.stop()
        }
        observe(playerViewModel.itemMovedFlow) { (new, old) ->
            player.moveMediaItem(old, new)
        }
        observe(playerViewModel.itemRemovedFlow) {
            player.removeMediaItem(it)
        }
    }
}