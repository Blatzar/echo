package dev.brahmkshatriya.echo.player

import androidx.media3.common.MediaItem
import dev.brahmkshatriya.echo.common.models.StreamableAudio
import dev.brahmkshatriya.echo.common.models.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.Collections.synchronizedList

class Queue {
    private val _queue = synchronizedList(mutableListOf<Pair<String, Track>>())
    val queue get() = synchronized(_queue) { _queue.toList() }

    val currentIndex = MutableStateFlow<Int?>(null)

    fun getTrack(mediaId: String?) = queue.find { it.first == mediaId }?.second

    private val _clearQueue = MutableSharedFlow<Unit>()
    val clearQueueFlow = _clearQueue.asSharedFlow()
    fun clearQueue(scope: CoroutineScope) {
        _queue.clear()
        scope.launch {
            _clearQueue.emit(Unit)
        }
    }

    private val _removeTrack = MutableSharedFlow<Int>()
    val removeTrackFlow = _removeTrack.asSharedFlow()
    fun removeTrack(scope: CoroutineScope, index: Int) {
        _queue.removeAt(index)
        scope.launch {
            _removeTrack.emit(index)
            if (_queue.isEmpty()) _clearQueue.emit(Unit)
        }
    }

    private val _addTrack = MutableSharedFlow<Pair<Int, MediaItem>>()
    val addTrackFlow = _addTrack.asSharedFlow()
    fun addTrack(
        scope: CoroutineScope, track: Track, stream: StreamableAudio, offset: Int = 0
    ): Pair<Int, MediaItem> {
        val item = PlayerHelper.mediaItemBuilder(track, stream)
        val mediaId = item.mediaId

        var position = currentIndex.value?.let {it + 1} ?: 0
        position += offset
        position = position.coerceIn(0, _queue.size)

        _queue.add(position , mediaId to track)
        scope.launch {
            _addTrack.emit(position to item)
        }
        return position to item
    }

    private val _moveTrack = MutableSharedFlow<Pair<Int, Int>>()
    val moveTrackFlow = _moveTrack.asSharedFlow()
    fun moveTrack(scope: CoroutineScope, fromIndex: Int, toIndex: Int) {
        Collections.swap(_queue, fromIndex, toIndex)
        scope.launch {
            _moveTrack.emit(fromIndex to toIndex)
        }
    }
}