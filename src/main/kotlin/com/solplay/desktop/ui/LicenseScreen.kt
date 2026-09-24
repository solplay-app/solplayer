package com.solplay.desktop.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.solplay.desktop.core.QrCodeGenerator
import com.solplay.iptv.DeviceKeyManager
import com.solplay.iptv.TrialManager
import kotlinx.coroutines.delay

/**
 * Équivalent desktop de LicenseActivity.kt : affiche la clé de cet appareil
 * (pour que l'admin l'assigne/l'active depuis son panneau), puis vérifie
 * automatiquement toutes les 10 secondes si elle a été activée - exactement
 * le même comportement que côté Android, aucune action requise de
 * l'utilisateur au-delà de communiquer sa clé.
 *
 * CORRECTIF (mise à jour conforme à la version Android) : jusqu'ici cet
 * écran n'affichait la clé qu'en texte, obligeant à la recopier caractère
 * par caractère dans le panel admin. On affiche maintenant, comme sur
 * Android (LicenseActivity + QrCodeGenerator), un QR code encodant
 * "SOLPLAY:<clé>" directement à l'ouverture de l'application Windows :
 * l'administrateur le scanne avec son téléphone pour activer l'appareil
 * sans ressaisie manuelle.
 */
@Composable
fun LicenseScreen(context: Context, onLicensed: () -> Unit) {
    val deviceKey = remember { DeviceKeyManager.getDeviceKey(context) }
    val qrBitmap: ImageBitmap? = remember(deviceKey) { QrCodeGenerator.generateForDeviceKey(deviceKey) }
    var checking by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var remainingTrialText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            checking = true
            val active = TrialManager.checkOnlineLicense(context)
            checking = false
            if (active) {
                onLicensed()
                return@LaunchedEffect
            }
            val remainingMs = TrialManager.getRemainingTrialMillis(context)
            remainingTrialText = if (remainingMs > 0) {
                val hours = remainingMs / 3_600_000
                val minutes = (remainingMs % 3_600_000) / 60_000
                "Essai gratuit restant : ${hours}h ${minutes}min"
            } else {
                "Essai gratuit terminé"
            }
            delay(10_000)
        }
    }

    Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.widthIn(max = 520.dp)) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Activation SolPlay", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                Text("Scannez ce code avec l'application admin, ou communiquez la clé ci-dessous à votre revendeur :")
                Spacer(Modifier.height(16.dp))
                if (qrBitmap != null) {
                    Card(modifier = Modifier.size(220.dp)) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "QR code d'activation",
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
                SelectionContainer {
                    Text(deviceKey, style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(16.dp))
                if (remainingTrialText.isNotEmpty()) {
                    Text(remainingTrialText, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
                if (checking) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        "Vérification automatique toutes les 10 secondes — cet écran passera seul à l'application dès l'activation.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                statusMessage?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = {
                    if (TrialManager.getRemainingTrialMillis(context) > 0) onLicensed()
                    else statusMessage = "Essai terminé. Merci de contacter votre revendeur avec votre clé ci-dessus."
                }) {
                    Text("Continuer avec l'essai gratuit")
                }
            }
        }
    }
}
