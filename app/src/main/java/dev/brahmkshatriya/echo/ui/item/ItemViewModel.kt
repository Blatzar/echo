package dev.brahmkshatriya.echo.ui.item

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.brahmkshatriya.echo.common.clients.AlbumClient
import dev.brahmkshatriya.echo.common.clients.ArtistClient
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.PlaylistClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.clients.UserClient
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.EchoMediaItem.Companion.toMediaItem
import dev.brahmkshatriya.echo.common.models.MediaItemsContainer
import dev.brahmkshatriya.echo.di.ExtensionModule
import dev.brahmkshatriya.echo.viewmodels.CatchingViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ItemViewModel @Inject constructor(
    throwableFlow: MutableSharedFlow<Throwable>,
    val extensionListFlow: ExtensionModule.ExtensionListFlow
) : CatchingViewModel(throwableFlow) {

    var item: EchoMediaItem? = null
    var client: ExtensionClient? = null
    val itemFlow = MutableStateFlow<EchoMediaItem?>(null)
    val relatedFeed = MutableStateFlow<PagingData<MediaItemsContainer>?>(null)

    override fun onInitialize() {
        viewModelScope.launch {
            tryWith {
                val item = item ?: throw IllegalArgumentException("Item is null")
                val mediaItem = when (item) {
                    is EchoMediaItem.Lists.AlbumItem -> getClient<AlbumClient>(client) {
                        load(item.album, ::loadAlbum, ::getMediaItems)?.toMediaItem()
                    }

                    is EchoMediaItem.Lists.PlaylistItem -> getClient<PlaylistClient>(client) {
                        load(item.playlist, ::loadPlaylist, ::getMediaItems)?.toMediaItem()
                    }

                    is EchoMediaItem.Profile.ArtistItem -> getClient<ArtistClient>(client) {
                        load(item.artist, ::loadArtist, ::getMediaItems)?.toMediaItem()
                    }

                    is EchoMediaItem.Profile.UserItem -> getClient<UserClient>(client) {
                        load(item.user, ::loadUser, ::getMediaItems)?.toMediaItem()
                    }

                    is EchoMediaItem.TrackItem -> getClient<TrackClient>(client) {
                        load(item.track, ::loadTrack, ::getMediaItems)?.toMediaItem()
                    }
                }
                mediaItem?.let { itemFlow.value = it }
            }
        }
    }

    private inline fun <reified T> getClient(
        client: ExtensionClient?, block: T.() -> EchoMediaItem?
    ) = if (client is T) block(client) else null

    private suspend fun <T> load(
        item: T, loadItem: suspend (T) -> T, loadRelated: (T) -> PagedData<MediaItemsContainer>
    ): T? {
        return tryWith {
            val loaded = loadItem(item)
            viewModelScope.launch {
                tryWith {
                    loadRelated(loaded).map { it }
                }?.collectTo(relatedFeed)
            }
            loaded
        }
    }
}
