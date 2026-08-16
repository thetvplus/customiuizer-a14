package tv.withaibuild.customiuizer

import android.app.Activity
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import javax.crypto.KeyGenerator

open class Credentials : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (keyguardManager != null && keyguardManager.isKeyguardSecure) {
                try {
                    val builder = KeyGenParameterSpec.Builder(
                        "dummy",
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    ).setUserAuthenticationRequired(true)
                    val keyGenerator =
                        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                    keyGenerator.init(builder.build())
                    keyGenerator.generateKey()
                    Toast.makeText(this, R.string.credentials_ok, Toast.LENGTH_SHORT).show()
                    finish()
                } catch (e: Throwable) {
                    val authIntent = keyguardManager.createConfirmDeviceCredentialIntent(
                        getString(R.string.credentials_unlock),
                        getString(R.string.dummy)
                    )
                    startActivityForResult(authIntent, 0)
                }
            } else {
                finish()
                val intent = Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD)
                startActivity(intent)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        finish()
        if (resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, R.string.credentials_success, Toast.LENGTH_SHORT).show()
        }
    }
}
