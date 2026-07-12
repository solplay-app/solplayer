package com.solplay.iptv

import android.content.Context
import java.security.MessageDigest

/**
 * Gère l'essai gratuit de 30 jours et l'activation de la licence Pro.
 *
 * ATTENTION SÉCURITÉ :
 * Ce système est stocké localement (SharedPreferences). Un utilisateur qui
 * désinstalle/réinstalle l'app ou efface les données réinitialise l'essai.
 * Pour une vraie protection anti-piratage en production, il faut vérifier
 * la licence côté serveur (ex: Firebase, ou ton propre backend) en liant le
 * code d'activation à un identifiant d'appareil (Settings.Secure.ANDROID_ID).
 * La structure ci-dessous est prête à être branchée sur une API distante :
 * il suffit de remplacer validateCodeOffline() par un appel réseau.
 */
object TrialManager {

    private const val PREFS = "solplay_prefs"
    private const val KEY_FIRST_LAUNCH = "first_launch_time"
    private const val KEY_LICENSED = "is_licensed"
    private const val KEY_LICENSE_CODE = "license_code"
    private const val TRIAL_DAYS = 30L
    private const val MILLIS_PER_DAY = 1000L * 60 * 60 * 24

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Doit être appelé une fois au démarrage de l'app (ex: SplashActivity). */
    fun ensureFirstLaunchRecorded(context: Context) {
        val p = prefs(context)
        if (p.getLong(KEY_FIRST_LAUNCH, 0L) == 0L) {
            p.edit().putLong(KEY_FIRST_LAUNCH, System.currentTimeMillis()).apply()
        }
    }

    fun isLicensed(context: Context): Boolean = prefs(context).getBoolean(KEY_LICENSED, false)

    fun getRemainingTrialDays(context: Context): Long {
        val first = prefs(context).getLong(KEY_FIRST_LAUNCH, System.currentTimeMillis())
        val elapsedDays = (System.currentTimeMillis() - first) / MILLIS_PER_DAY
        return (TRIAL_DAYS - elapsedDays).coerceAtLeast(0)
    }

    fun isTrialActive(context: Context): Boolean = getRemainingTrialDays(context) > 0

    /** L'utilisateur peut utiliser l'app s'il est licencié OU encore dans l'essai. */
    fun canAccessApp(context: Context): Boolean = isLicensed(context) || isTrialActive(context)

    /**
     * Active la version Pro avec un code fourni par SolPlay (envoyé après achat,
     * ex: par email/WhatsApp après contact avec stephanegue2018@gmail.com).
     * Remplace validateCodeOffline() par un appel à ton serveur de licences
     * pour une vérification sécurisée en production.
     */
    fun activateLicense(context: Context, code: String): Boolean {
        val trimmed = code.trim()
        val valid = validateCodeOffline(trimmed)
        if (valid) {
            prefs(context).edit()
                .putBoolean(KEY_LICENSED, true)
                .putString(KEY_LICENSE_CODE, trimmed)
                .apply()
        }
        return valid
    }

    private fun validateCodeOffline(code: String): Boolean {
        // Exemple simple de validation locale par format + checksum.
        // À remplacer par une vérification serveur pour la production.
        if (code.length < 10) return false
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(code.substring(0, code.length - 2).toByteArray())
        val checksum = hash.take(1).joinToString("") { "%02x".format(it) }.take(2)
        return code.endsWith(checksum, ignoreCase = true)
    }
}
