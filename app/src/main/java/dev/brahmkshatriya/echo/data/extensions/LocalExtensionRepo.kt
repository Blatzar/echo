package dev.brahmkshatriya.echo.data.extensions

import android.content.Context
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import kotlinx.coroutines.flow.flowOf
import tel.jeelpa.plugger.PluginRepo

class LocalExtensionRepo(val context: Context) : PluginRepo<ExtensionClient> {
    override fun getAllPlugins(exceptionHandler: (Exception) -> Unit) =
        flowOf(listOf(OfflineExtension(context)))
}