package tv.withaibuild.customiuizer.tasker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import tv.withaibuild.customiuizer.R

class UnlockSettings : AppCompatActivity() {

    private val provider = UnlockTokenProvider()
    private var hostInfo: UnlockTokenProvider.HostInfo? = null
    private var bindingStatus: UnlockTokenProvider.HostBindingStatus? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.tasker_unlock)

        val hostInfoText = findViewById<TextView>(R.id.host_info)
        val callingPackage = callingPackage

        if (callingPackage == null) {
            hostInfoText.text = getString(R.string.unlock_host_missing)
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        this.hostInfo = provider.getHostInfo(this, callingPackage)
        val info = this.hostInfo
        if (info == null) {
            hostInfoText.text = getString(R.string.unlock_host_untrusted, callingPackage)
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        // Read-only: do not create or update any binding here.
        val status = provider.prepare(this, info)
        this.bindingStatus = status

        when (status) {
            is UnlockTokenProvider.HostBindingStatus.NewHost -> {
                hostInfoText.text = getString(
                    R.string.unlock_host_first,
                    formatHostSummary(info)
                )
            }
            is UnlockTokenProvider.HostBindingStatus.Reuse -> {
                hostInfoText.text = getString(
                    R.string.unlock_host_reuse,
                    formatHostSummary(info)
                )
            }
            UnlockTokenProvider.HostBindingStatus.Mismatch -> {
                hostInfoText.text = getString(
                    R.string.unlock_host_cert_mismatch,
                    info.applicationLabel,
                    info.packageName,
                    info.currentFingerprint ?: "?"
                )
                setResult(Activity.RESULT_CANCELED)
                finish()
                return
            }
        }

        val bundle = intent.getBundleExtra(Constants.EXTRA_BUNDLE)
        if (bundle != null) {
            val opt = bundle.getInt("system_noscreenlock_force", -1)
            val checkedId = when (opt) {
                0 -> R.id.force_locked
                1 -> R.id.force_unlocked
                else -> R.id.force_off
            }
            findViewById<RadioGroup>(R.id.force_option).check(checkedId)
        }

        val ok = findViewById<Button>(R.id.force_ok)
        ok.setOnClickListener {
            onConfirm()
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        })

        if (bundle == null || !bundle.containsKey(UnlockTokenProvider.BUNDLE_KEY_TOKEN)) {
            Toast.makeText(this, R.string.unlock_token_missing, Toast.LENGTH_LONG).show()
        }
    }

    private fun formatHostSummary(info: UnlockTokenProvider.HostInfo): String {
        return getString(
            R.string.unlock_host_summary,
            info.applicationLabel,
            info.packageName,
            info.currentFingerprint ?: "?",
            info.historySummary
        )
    }

    private fun onConfirm() {
        val info = hostInfo ?: run {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        // bind() is the only place that may write token or certificate state.
        val hostToken = provider.bind(this, info)
        if (hostToken == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val opt = findViewById<RadioGroup>(R.id.force_option).checkedRadioButtonId
        val lockState = when (opt) {
            R.id.force_locked -> 0
            R.id.force_unlocked -> 1
            else -> -1
        }

        val stringRes = when (lockState) {
            1 -> R.string.system_noscreenlock_force_unlocked
            0 -> R.string.system_noscreenlock_force_locked
            else -> R.string.system_noscreenlock_force_off
        }

        val resultIntent = Intent().apply {
            putExtra(Constants.EXTRA_STRING_BLURB, getString(stringRes))
            val outBundle = Bundle().apply {
                putInt("system_noscreenlock_force", lockState)
                putString(UnlockTokenProvider.BUNDLE_KEY_TOKEN, hostToken.token)
                putString(UnlockTokenProvider.BUNDLE_KEY_HOST_PACKAGE, hostToken.hostPackage)
            }
            putExtra(Constants.EXTRA_BUNDLE, outBundle)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

}
