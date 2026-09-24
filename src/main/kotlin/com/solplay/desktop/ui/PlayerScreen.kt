package com.solplay.desktop.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.solplay.desktop.core.AsyncImage
import com.solplay.iptv.Channel
import com.solplay.iptv.ChannelRepository
import com.solplay.iptv.DevicePlaylistSync
import com.solplay.iptv.PlaylistStore
import com.solplay.iptv.SavedPlaylist
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.player.base.MediaPlayer

/** Formate une durée en millisecondes en "MM:SS" ou "H:MM:SS" pour la barre de progression. */
private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/**
 * Lecteur vidéo desktop, remplace ExoPlayer/Media3 (PlayerActivity côté
 * Android) - ExoPlayer est une librairie Android uniquement, indisponible
 * sur JVM desktop. VLC (via vlcj) est utilisé à la place : mêmes formats
 * pris en charge (HLS, TS, MP4...), donc compatible avec exactement les
 * mêmes flux IPTV.
 *
 * PRÉREQUIS IMPORTANT : contrairement à ExoPlayer qui est autonome, vlcj
 * pilote une installation LOCALE de VLC Media Player. L'utilisateur final
 * doit avoir VLC installé sur son PC Windows (gratuit :
 * https://www.videolan.org/vlc/download-windows.html) - sinon cet écran
 * affichera une erreur au lancement de la lecture.
 *
 * CORRECTIFS appliqués (alignement sur la version Android) :
 * 1. SON QUI SE COUPE EN DIRECT : cache libVLC porté à 5s + option
 *    ":live-caching" (équivalent du buffer ExoPlayer de PlayerActivity,
 *    buildIptvLoadControl) au lieu du cache quasi-instantané par défaut.
 * 2. PANNEAU "☰ Chaînes" IMPOSSIBLE À REFERMER : bouton ✕ dans l'en-tête
 *    du panneau, clic sur le fond assombri, et touche Échap ferment le
 *    panneau (avant, seul un second clic précis sur le bouton le fermait).
 * 3. ERREUR FFMPEG BRUTE AFFICHÉE ("avformat_open_input() error...") :
 *    remplacée par un message convivial "Cette chaîne ou ce contenu n'est
 *    pas disponible pour le moment." + bouton "Réessayer", exactement
 *    comme PlayerActivity côté Android ("Cette chaîne n'est pas disponible
 *    pour le moment"). 3 reprises automatiques sont tentées avant
 *    d'afficher le message.
 * 4. PLUS RIEN NE SE LANCE APRÈS UN MOMENT / OBLIGÉ DE SUPPRIMER LE
 *    COMPTE : le composant VLC natif n'était JAMAIS libéré (release())
 *    en quittant le lecteur - chaque lecture empilait une instance native
 *    jusqu'à épuisement des ressources, et plus aucun contenu ne démarrait.
 *    Le composant est maintenant libéré proprement à la sortie de l'écran.
 * 5. PAS DE BARRE DE PROGRESSION SUR LES FILMS : ajout d'une barre de
 *    contrôle complète (lecture/pause, slider de progression, temps
 *    écoulé / total). En direct (durée inconnue), un badge "EN DIRECT"
 *    est affiché à la place du slider.
 */
@Composable
fun PlayerScreen(
    context: Context,
    playlist: SavedPlaylist,
    streamUrl: String,
    title: String,
    onBack: () -> Unit,
    onSwitchChannel: (streamUrl: String, title: String) -> Unit,
    onRevoked: () -> Unit
) {
    val mediaPlayerComponent = remember { EmbeddedMediaPlayerComponent() }
    var revokedMessage by remember { mutableStateOf<String?>(null) }
    val currentStreamUrl = rememberUpdatedState(streamUrl)

    // --- État d'erreur convivial (correctif 3) ---
    var playbackError by remember(streamUrl) { mutableStateOf(false) }
    var errorAttempts by remember(streamUrl) { mutableStateOf(0) }

    // --- État de la barre de contrôle (correctif 5) ---
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var seeking by remember { mutableStateOf(false) }
    var sliderPos by remember { mutableStateOf(0f) }

    // Options de lecture libVLC (correctif 1) : cache réseau aligné sur le
    // buffer ExoPlayer côté Android (~3-5s au lieu de ~300ms par défaut),
    // + ":live-caching" spécifique aux flux en direct dont le débit fluctue,
    // + resynchronisation d'horloge agressive de VLC désactivée (source de
    // coupures audio sur les flux IPTV au débit irrégulier).
    val streamOptions = remember {
        arrayOf(
            ":network-caching=5000",
            ":live-caching=5000",
            ":file-caching=3000",
            ":sout-mux-caching=3000",
            ":clock-jitter=0",
            ":clock-synchro=0"
        )
    }

    fun startPlayback() {
        try {
            mediaPlayerComponent.mediaPlayer().media().play(currentStreamUrl.value, *streamOptions)
        } catch (_: Exception) { /* composant libéré entre-temps */ }
    }

    // Gestion d'erreur (correctif 3) : jusqu'à 3 reprises automatiques avec
    // délai croissant (comme le watchdog de PlayerActivity côté Android),
    // puis affichage d'un message convivial au lieu du code d'erreur brut.
    // Dès que la lecture repart (événement playing), l'erreur s'efface.
    DisposableEffect(mediaPlayerComponent) {
        val listener = object : MediaPlayerEventAdapter() {
            override fun error(mediaPlayer: MediaPlayer) {
                if (errorAttempts < 3) {
                    errorAttempts++
                    val delayMs = 2000L * errorAttempts
                    Thread {
                        try {
                            Thread.sleep(delayMs)
                            if (!playbackError) startPlayback()
                        } catch (_: Exception) { /* le composant a pu être libéré entre-temps */ }
                    }.start()
                } else {
                    playbackError = true
                }
            }

            override fun playing(mediaPlayer: MediaPlayer) {
                playbackError = false
                errorAttempts = 0
            }
        }
        mediaPlayerComponent.mediaPlayer().events().addMediaPlayerEventListener(listener)
        onDispose {
            mediaPlayerComponent.mediaPlayer().events().removeMediaPlayerEventListener(listener)
        }
    }

    // Libération du composant VLC natif (correctif 4) : sans release(), les
    // ressources natives s'accumulaient à chaque lecture jusqu'à ce que plus
    // aucune vidéo ne démarre, quel que soit le contenu choisi.
    DisposableEffect(Unit) {
        onDispose {
            try { mediaPlayerComponent.mediaPlayer().controls().stop() } catch (_: Exception) {}
            try { mediaPlayerComponent.mediaPlayer().release() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(playlist.id) {
        val tag = playlist.fromCode ?: return@LaunchedEffect
        while (true) {
            delay(120_000L) // même intervalle que PlayerActivity côté Android
            if (!DevicePlaylistSync.checkStillAssigned(context, tag)) {
                try { mediaPlayerComponent.mediaPlayer().controls().stop() } catch (_: Exception) {}
                PlaylistStore.delete(context, playlist.id)
                revokedMessage = "L'accès à cette playlist a été retiré par l'administrateur."
                break
            }
        }
    }

    LaunchedEffect(revokedMessage) {
        if (revokedMessage != null) {
            delay(3000L)
            onRevoked()
        }
    }

    // IMPORTANT : ne pas appeler .play() dès la composition. SwingPanel
    // rattache le composant vlcj à la fenêtre native de façon asynchrone
    // (après la passe de composition Compose) - si .play() est appelé
    // avant que ce rattachement soit terminé, vlcj lève l'erreur "the
    // video surface component must be displayable". On attend donc que le
    // composant soit effectivement "displayable" avant de démarrer la lecture.
    LaunchedEffect(streamUrl) {
        var attempts = 0
        while (!mediaPlayerComponent.isDisplayable && attempts < 150) { // ~3s max
            delay(20L)
            attempts++
        }
        startPlayback()
    }

    DisposableEffect(streamUrl) {
        onDispose {
            try { mediaPlayerComponent.mediaPlayer().controls().stop() } catch (_: Exception) {}
        }
    }

    // Sondage de l'état de lecture toutes les 500 ms pour la barre de
    // contrôle (correctif 5) : position, durée totale, lecture/pause.
    // Pour un flux en direct, libVLC renvoie une durée <= 0 : le slider est
    // alors remplacé par le badge "EN DIRECT".
    LaunchedEffect(streamUrl) {
        while (true) {
            try {
                val mp = mediaPlayerComponent.mediaPlayer()
                isPlaying = mp.status().isPlaying()
                val len = mp.status().length()
                durationMs = if (len > 0) len else 0L
                if (!seeking) {
                    val t = mp.status().time()
                    positionMs = if (t > 0) t else 0L
                    if (durationMs > 0) {
                        sliderPos = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    }
                }
            } catch (_: Exception) { /* composant libéré */ }
            delay(500L)
        }
    }

    // Capturée une seule fois à l'entrée sur cet écran (même bouquet/recherche
    // que ce qui était affiché sur HomeScreen au moment du clic) : ne doit pas
    // changer pendant qu'on regarde, y compris quand on change de chaîne
    // depuis ce panneau.
    val playingList = remember { ChannelRepository.playingList }

    var showChannelPanel by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            revokedMessage?.let { msg ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(msg, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                }
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))

                if (playingList.size > 1) {
                    Surface(
                        color = SolPlayColors.Orange,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { showChannelPanel = !showChannelPanel }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.Menu, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Chaînes", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Zone vidéo + superposition d'erreur conviviale (correctif 3).
            Box(Modifier.weight(1f).fillMaxWidth()) {
                SwingPanel(
                    modifier = Modifier.fillMaxSize(),
                    factory = { mediaPlayerComponent }
                )

                if (playbackError) {
                    Box(
                        Modifier.fillMaxSize().background(Color(0xCC000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card {
                            Column(
                                Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "⚠ Lecture impossible",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Cette chaîne ou ce contenu n'est pas disponible pour le moment.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(16.dp))
                                Row {
                                    Button(onClick = {
                                        playbackError = false
                                        errorAttempts = 0
                                        startPlayback()
                                    }) { Text("Réessayer") }
                                    Spacer(Modifier.width(12.dp))
                                    OutlinedButton(onClick = onBack) { Text("Retour") }
                                }
                            }
                        }
                    }
                }
            }

            // Barre de contrôle (correctif 5) : toujours visible, comme la
            // barre de contrôle du PlayerActivity Android. Sur les films /
            // épisodes (durée connue) : slider de progression + temps. Sur
            // le direct (durée inconnue) : badge "EN DIRECT".
            Surface(color = Color(0xFF1B1B1B)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        try {
                            val mp = mediaPlayerComponent.mediaPlayer()
                            if (isPlaying) mp.controls().pause() else mp.controls().play()
                        } catch (_: Exception) {}
                    }) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Lecture",
                            tint = Color.White
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (durationMs > 0) {
                        Text(formatTime(positionMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = sliderPos,
                            onValueChange = {
                                seeking = true
                                sliderPos = it
                            },
                            onValueChangeFinished = {
                                try {
                                    mediaPlayerComponent.mediaPlayer().controls().setPosition(sliderPos)
                                } catch (_: Exception) {}
                                seeking = false
                            },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        Text(formatTime(durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    } else {
                        Surface(color = SolPlayColors.Orange, shape = RoundedCornerShape(4.dp)) {
                            Text(
                                "EN DIRECT",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Panneau latéral "Changer de chaîne" (correctif 2) : fond assombri
        // cliquable pour fermer + bouton ✕ + touche Échap. Reste
        // TRANSPARENT/semi-sombre par-dessus la vidéo, mêmes couleurs que
        // l'app Android.
        if (showChannelPanel) {
            val filtered = remember(playingList, query) {
                if (query.isBlank()) playingList
                else playingList.filter { it.name.contains(query, ignoreCase = true) }
            }
            val listState = rememberLazyListState()
            val panelFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { panelFocusRequester.requestFocus() }

            fun closePanel() {
                showChannelPanel = false
                query = ""
            }

            fun selectChannel(channel: Channel) {
                onSwitchChannel(channel.streamUrl, channel.name)
                closePanel()
            }

            // Fond assombri : un clic n'importe où hors du panneau le ferme.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { closePanel() }
            )

            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(320.dp)
                    .background(SolPlayColors.PanelOverlay)
            ) {
                Surface(color = SolPlayColors.PanelHeaderOverlay, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp)
                    ) {
                        Text(
                            "Changer de chaîne",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).padding(vertical = 12.dp)
                        )
                        IconButton(onClick = { closePanel() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Fermer", tint = Color.White)
                        }
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Rechercher une chaîne...", color = SolPlayColors.White60) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = SolPlayColors.SearchOverlayBg,
                        unfocusedContainerColor = SolPlayColors.SearchOverlayBg,
                        focusedBorderColor = SolPlayColors.White60,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().padding(10.dp)
                )

                if (filtered.isEmpty()) {
                    Text(
                        "Aucune chaîne trouvée.",
                        color = SolPlayColors.White60,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .focusRequester(panelFocusRequester)
                            .focusTarget()
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                val index = listState.firstVisibleItemIndex
                                when (event.key) {
                                    Key.Escape -> {
                                        closePanel()
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        scope.launch { listState.animateScrollToItem((index + 1).coerceAtMost(filtered.lastIndex)) }
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        scope.launch { listState.animateScrollToItem((index - 1).coerceAtLeast(0)) }
                                        true
                                    }
                                    Key.DirectionRight, Key.PageDown -> {
                                        scope.launch { listState.animateScrollToItem((index + 10).coerceAtMost(filtered.lastIndex)) }
                                        true
                                    }
                                    Key.DirectionLeft, Key.PageUp -> {
                                        scope.launch { listState.animateScrollToItem((index - 10).coerceAtLeast(0)) }
                                        true
                                    }
                                    Key.Enter, Key.NumPadEnter -> {
                                        filtered.getOrNull(index)?.let { selectChannel(it) }
                                        true
                                    }
                                    else -> false
                                }
                            }
                    ) {
                        items(filtered, key = { it.streamUrl }) { channel ->
                            val isActive = channel.streamUrl == streamUrl
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectChannel(channel) }
                                    .background(if (isActive) SolPlayColors.SearchOverlayBg else Color.Transparent)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).background(SolPlayColors.SearchOverlayBg)) {
                                    AsyncImage(
                                        channel.logoUrl,
                                        channel.name,
                                        Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        channel.name,
                                        color = if (isActive) SolPlayColors.Orange else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    channel.groupTitle?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, color = SolPlayColors.White60, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
