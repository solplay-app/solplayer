package com.solplay.desktop.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.solplay.iptv.BuildConfig
import com.solplay.iptv.DeviceKeyManager
import com.solplay.iptv.SavedPlaylist
import com.solplay.iptv.TrialManager
import com.solplay.iptv.XtreamApiClient

/**
 * Équivalent desktop de l'écran/dialogue "À propos" d'Android : identifiant
 * de l'appareil et temps restant avant expiration (essai ou licence).
 *
 * CORRECTIF (date d'expiration) : le dialogue affiche maintenant DEUX
 * expirations distinctes, comme l'écran "À propos" de l'app Android
 * (AboutActivity) :
 *  - la date d'expiration de la LICENCE du logiciel SolPlay (activation
 *    de l'appareil par l'admin),
 *  - la date d'expiration de la PLAYLIST/abonnement IPTV (côté panel
 *    Xtream), qui n'était affichée nulle part jusqu'ici - l'utilisateur ne
 *    voyait donc que la licence, sans savoir quand son abonnement de
 *    chaînes se termine.
 */
@Composable
fun AboutDialog(context: Context, playlist: SavedPlaylist, onDismiss: () -> Unit) {
    val deviceKey = remember { DeviceKeyManager.getDeviceKey(context) }

    // Expiration de la licence du logiciel (calculée localement, comme sur Android).
    val licenseText = remember {
        val remainingLicense = TrialManager.getRemainingLicenseMillis(context)
        val remainingTrial = TrialManager.getRemainingTrialMillis(context)
        when {
            remainingLicense == Long.MAX_VALUE ->
                "Licence SolPlay : active (sans date d'expiration)"
            remainingLicense > 0 -> {
                val expiresAt = System.currentTimeMillis() + remainingLicense
                "Licence SolPlay : expire le ${TrialManager.formatDate(expiresAt)} " +
                    "(${TrialManager.formatDuration(remainingLicense)} restant)"
            }
            remainingTrial > 0 ->
                "Essai gratuit : ${TrialManager.formatDuration(remainingTrial)} restant"
            else -> "Licence SolPlay : aucune licence active"
        }
    }

    // Expiration de la playlist IPTV (interroge le panel Xtream, comme
    // HomeScreen le fait pour la détection d'abonnement expiré - voir
    // XtreamApiClient.checkAccountStatus). Best effort : si le panel ne
    // répond pas ou si ce n'est pas une playlist Xtream, on l'indique
    // simplement au lieu de deviner.
    val playlistStatus by produceState<XtreamApiClient.AccountStatus?>(
        initialValue = null,
        playlist.id
    ) {
        value = XtreamApiClient.checkAccountStatus(playlist)
    }

    val playlistText = when {
        playlistStatus == null ->
            "Playlist « ${playlist.name} » : date d'expiration indisponible (panel non joignable ou non Xtream)"
        playlistStatus!!.expiresAtMillis != null ->
            "Playlist « ${playlist.name} » : expire le ${TrialManager.formatDate(playlistStatus!!.expiresAtMillis!!)}" +
                if (playlistStatus!!.expired) " (EXPIRÉE)" else ""
        else ->
            "Playlist « ${playlist.name} » : abonnement sans date d'expiration"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("À propos de SolPlay") },
        text = {
            Column {
                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(licenseText, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(playlistText, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text("Identifiant de l'appareil :", style = MaterialTheme.typography.labelMedium)
                Text(deviceKey, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        }
    )
}
