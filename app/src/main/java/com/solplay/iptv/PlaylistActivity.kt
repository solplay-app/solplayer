package com.solplay.iptv

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

        if (TrialManager.isLicensed(this)) {
            binding.tvTrialBanner.visibility = android.view.View.GONE
        } else {
            binding.tvTrialBanner.text = getString(
                R.string.trial_active_format,
                TrialManager.getRemainingTrialDays(this)
            )
        }

        binding.btnLoadPlaylist.setOnClickListener {
            val url = binding.etPlaylistUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Veuillez entrer un lien de playlist", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            loadPlaylist(url)
        }

        binding.btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private fun loadPlaylist(url: String) {
        binding.progressBar.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            try {
                val channels = withContext(Dispatchers.IO) { M3uParser.fetchAndParse(url) }
                binding.progressBar.visibility = android.view.View.GONE
                if (channels.isEmpty()) {
                    Toast.makeText(this@PlaylistActivity, "Aucune chaîne trouvée dans cette playlist", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val intent = Intent(this@PlaylistActivity, ChannelsActivity::class.java)
                intent.putExtra(ChannelsActivity.EXTRA_CHANNELS, ArrayList(channels))
                startActivity(intent)
            } catch (e: Exception) {
                binding.progressBar.visibility = android.view.View.GONE
                Toast.makeText(this@PlaylistActivity, "Erreur de chargement : ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
