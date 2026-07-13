package com.solplay.iptv

/**
 * Conserve la liste des chaînes en mémoire, partagée entre PlaylistActivity
 * et ChannelsActivity.
 *
 * Pourquoi : Android limite à environ 1 Mo la taille des données qu'on peut
 * faire transiter d'un écran à l'autre via un Intent (limite du buffer de
 * transaction Binder). Les playlists IPTV contiennent souvent des milliers
 * de chaînes, ce qui dépasse largement cette limite et provoque un crash
 * "Failure from system" au moment de startActivity(). En passant par cet
 * objet en mémoire au lieu de intent.putExtra(), on évite complètement
 * cette limite.
 */
object ChannelRepository {
    var channels: List<Channel> = emptyList()
        private set

    fun setChannels(newChannels: List<Channel>) {
        channels = newChannels
    }

    fun clear() {
        channels = emptyList()
    }
}
