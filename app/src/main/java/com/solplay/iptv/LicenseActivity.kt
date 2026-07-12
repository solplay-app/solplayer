package com.solplay.iptv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.solplay.iptv.databinding.ActivityLicenseBinding

class LicenseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLicenseBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLicenseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val remaining = TrialManager.getRemainingTrialDays(this)
        val trialActive = TrialManager.isTrialActive(this)

        if (trialActive) {
            binding.tvStatus.text = getString(R.string.trial_active_format, remaining)
            binding.btnContinueTrial.visibility = android.view.View.VISIBLE
        } else {
            binding.tvStatus.text = getString(R.string.trial_expired_title)
            binding.btnContinueTrial.visibility = android.view.View.GONE
        }

        binding.btnContinueTrial.setOnClickListener {
            goToApp()
        }

        binding.btnActivate.setOnClickListener {
            val code = binding.etLicenseCode.text.toString()
            if (TrialManager.activateLicense(this, code)) {
                Toast.makeText(this, R.string.license_success, Toast.LENGTH_LONG).show()
                goToApp()
            } else {
                Toast.makeText(this, R.string.license_invalid, Toast.LENGTH_LONG).show()
            }
        }

        binding.btnContactUs.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:" + getString(R.string.contact_email))
                putExtra(Intent.EXTRA_SUBJECT, "Achat SolPlay Pro")
            }
            startActivity(Intent.createChooser(intent, "Contacter SolPlay"))
        }
    }

    private fun goToApp() {
        startActivity(Intent(this, PlaylistActivity::class.java))
        finish()
    }
}
