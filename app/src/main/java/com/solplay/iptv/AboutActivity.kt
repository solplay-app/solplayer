package com.solplay.iptv

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.solplay.iptv.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val licensed = TrialManager.isLicensed(this)
        binding.tvLicenseStatus.text = if (licensed) {
            "Statut : Version Pro activée ✅"
        } else {
            "Statut : Essai gratuit (${TrialManager.getRemainingTrialDays(this)} jour(s) restant(s))"
        }
    }
}
