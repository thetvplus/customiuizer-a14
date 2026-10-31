package tv.withaibuild.customiuizer

import android.app.Activity
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity

open class Credentials : ComponentActivity() {
    private var confirmationPending = false
    private val confirmCredential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        confirmationPending = false
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, R.string.credentials_success, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The registry restores outstanding confirmation after recreation.
        confirmationPending = savedInstanceState?.getBoolean("credentials.pending", false) == true
        if (confirmationPending) return
        try {
            val keyguard = getSystemService(KeyguardManager::class.java)
            if (keyguard?.isKeyguardSecure != true) {
                startActivity(Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD))
                finish()
                return
            }
            // Keep the A14 system confirmation flow. The existing registry owns
            // recreation without introducing a separate BiometricPrompt lifecycle.
            @Suppress("DEPRECATION")
            val intent = keyguard.createConfirmDeviceCredentialIntent(
                getString(R.string.credentials_unlock), getString(R.string.dummy)
            )
            if (intent == null) finish() else {
                confirmationPending = true
                confirmCredential.launch(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("Credentials", "Unable to launch credential confirmation", e)
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("credentials.pending", confirmationPending)
        super.onSaveInstanceState(outState)
    }
}
