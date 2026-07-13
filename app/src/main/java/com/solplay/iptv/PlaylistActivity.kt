package com.solplay.iptv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.solplay.iptv.databinding.ActivityPlaylistBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaylistBinding

    companion object {
        private const val PREFS = "solplay_prefs"
        private const val KEY_LAST_NAME = "last_playlist_name"
        private const val KEY_LAST_MODE_XTREAM = "last_mode_xtream"
        private const val KEY_LAST_M3U_URL = "last_m3u_url"
        private const val KEY_LAST_XTREAM_SERVER = "last_xtream_server"
        private const val KEY_LAST_XTREAM_USER = "last_xtream_user"
        private const val KEY_LAST_XTREAM_PASS = "last_xtream_pass"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Sécurité : si l'essai a expiré et pas de licence, retour à l'écran d'activation
        if (!TrialManager.canAccessApp(this)) {
            startActivity(Intent(this, LicenseActivity::class.java))
            finish()
            return
        }

        binding = ActivityPlaylistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        DisclaimerDialog.showIfNeeded(this)

        if (TrialManager.isLicensed(this)) {
            binding.tvTrialBanner.visibility = android.view.View.GONE
        } else {
            binding.tvTrialBanner.text = getString(
                R.string.trial_active_format,
                TrialManager.getRemainingTrialDays(this)
            )
        }

        restoreLastPlaylist()

        // Bascule entre le mode "Lien M3U" et le mode "Xtream Codes"
        binding.rgMode.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbXtream) {
                binding.llM3u.visibility = android.view.View.GONE
                binding.llXtream.visibility = android.view.View.VISIBLE
            } else {
                binding.llM3u.visibility = android.view.View.VISIBLE
                binding.llXtream.visibility = android.view.View.GONE
            }
        }

        binding.btnLoadPlaylist.setOnClickListener {
            val url = buildPlaylistUrl()
            if (url == null) {
                Toast.makeText(this, "Veuillez remplir tous les champs requis", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveLastPlaylist()
            loadPlaylist(url)
        }

        binding.btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    /** Recharge les derniers identifiants/lien utilisés, pour éviter de tout retaper. */
    private fun restoreLastPlaylist() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        binding.etPlaylistName.setText(prefs.getString(KEY_LAST_NAME, ""))

        val wasXtream = prefs.getBoolean(KEY_LAST_MODE_XTREAM, false)
        if (wasXtream) {
            binding.rbXtream.isChecked = true
            binding.llM3u.visibility = android.view.View.GONE
            binding.llXtream.visibility = android.view.View.VISIBLE
        }

        binding.etPlaylistUrl.setText(prefs.getString(KEY_LAST_M3U_URL, ""))
        binding.etXtreamServer.setText(prefs.getString(KEY_LAST_XTREAM_SERVER, ""))
        binding.etXtreamUsername.setText(prefs.getString(KEY_LAST_XTREAM_USER, ""))
        binding.etXtreamPassword.setText(prefs.getString(KEY_LAST_XTREAM_PASS, ""))
    }

    private fun saveLastPlaylist() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_LAST_NAME, binding.etPlaylistName.text.toString())
            putBoolean(KEY_LAST_MODE_XTREAM, binding.rbXtream.isChecked)
            putString(KEY_LAST_M3U_URL, binding.etPlaylistUrl.text.toString())
            putString(KEY_LAST_XTREAM_SERVER, binding.etXtreamServer.text.toString())
            putString(KEY_LAST_XTREAM_USER, binding.etXtreamUsername.text.toString())
            putString(KEY_LAST_XTREAM_PASS, binding.etXtreamPassword.text.toString())
            apply()
        }
    }

    /**
     * Construit l'URL de la playlist selon le mode choisi.
     * - Mode M3U : utilise directement le lien saisi.
     * - Mode Xtream Codes : construit l'URL standard de l'API Xtream
     *   (utilisée par la plupart des fournisseurs IPTV légaux avec ce protocole).
     */
    private fun buildPlaylistUrl(): String? {
        return if (binding.rbXtream.isChecked) {
            val server = binding.etXtreamServer.text.toString().trim().trimEnd('/')
            val username = binding.etXtreamUsername.text.toString().trim()
            val password = binding.etXtreamPassword.text.toString().trim()
            if (server.isEmpty() || username.isEmpty() || password.isEmpty()) {
                null
            } else {
                "$server/get.php?username=$username&password=$password&type=m3u_plus&output=ts"
            }
        } else {
            val url = binding.etPlaylistUrl.text.toString().trim()
            url.ifEmpty { null }
        }
    }

    private fun loadPlaylist(url: String) {
        binding.progressBar.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            try {
                val channels = withContext(Dispatchers.IO) { M3uParser.fetchAndParse(url) }
                binding.progressBar.visibility = android.view.View.GONE
                if (channels.isEmpty()) {
                    Toast.makeText(this@PlaylistActivity, "Aucune chaîne trouvée. Vérifiez vos identifiants/lien.", Toast.LENGTH_LONG).show()
                    return@launch
                }
                // On stocke la liste en mémoire (ChannelRepository) au lieu de la faire
                // passer par l'Intent : les grosses playlists (plusieurs milliers de
                // chaînes) dépassaient la limite de transaction Binder (~1 Mo) et
                // provoquaient un crash "Failure from system".
                ChannelRepository.setChannels(channels)
                val intent = Intent(this@PlaylistActivity, ChannelsActivity::class.java)
                startActivity(intent)
            } catch (e: PlaylistLoadException) {
                // Message déjà clair et destiné à l'utilisateur (timeout, serveur, réseau...).
                binding.progressBar.visibility = android.view.View.GONE
                Toast.makeText(this@PlaylistActivity, e.message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                binding.progressBar.visibility = android.view.View.GONE
                Toast.makeText(this@PlaylistActivity, "Erreur de chargement : ${e.message ?: "inconnue"}.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
