package com.solplay.iptv

import java.io.Serializable
import java.net.HttpURLConnection
import java.net.URL

data class Channel(
    val name: String,
    val logoUrl: String?,
    val groupTitle: String?,
    val streamUrl: String
) : Serializable

object M3uParser {

    /** Télécharge et parse une playlist M3U depuis une URL distante. */
    fun fetchAndParse(playlistUrl: String): List<Channel> {
        val connection = URL(playlistUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.requestMethod = "GET"
        connection.connect()

        val content = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        return parse(content)
    }

    /** Parse le contenu texte brut d'un fichier M3U/M3U8. */
    fun parse(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        var currentName = ""
        var currentLogo: String? = null
        var currentGroup: String? = null

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    currentName = line.substringAfterLast(",").trim().ifEmpty { "Chaîne inconnue" }
                    currentLogo = Regex("tvg-logo=\"([^\"]*)\"").find(line)?.groupValues?.get(1)
                    currentGroup = Regex("group-title=\"([^\"]*)\"").find(line)?.groupValues?.get(1)
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    channels.add(Channel(currentName, currentLogo, currentGroup, line))
                    currentName = ""
                    currentLogo = null
                    currentGroup = null
                }
            }
        }
        return channels
    }
}
