package com.solplay.desktop.core

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.image.BufferedImage

/**
 * Équivalent desktop de com.solplay.iptv.QrCodeGenerator (Android) : même
 * bibliothèque (zxing) et même format de contenu encodé ("SOLPLAY:<clé>"),
 * pour que le panel admin puisse scanner indifféremment le QR affiché sur
 * téléphone/TV ou sur l'écran d'activation Windows.
 *
 * Différence purement technique : ici on produit une ImageBitmap Compose
 * Desktop (via BufferedImage) au lieu d'un android.graphics.Bitmap, l'API
 * Android n'existant pas sur la JVM classique.
 */
object QrCodeGenerator {

    /** Génère un QR code à partir de n'importe quel contenu texte. */
    fun generate(content: String, sizePx: Int = 512): ImageBitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1
            )
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)

            val img = BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_RGB)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    img.setRGB(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                }
            }
            img.toComposeImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * @param deviceKey Clé appareil brute (16 caractères), identique à celle
     *                  utilisée pour l'activation manuelle affichée en dessous.
     */
    fun generateForDeviceKey(deviceKey: String, sizePx: Int = 512): ImageBitmap? =
        generate("SOLPLAY:$deviceKey", sizePx)
}
