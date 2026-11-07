package tv.withaibuild.customiuizer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

class CredentialsShortcut : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shortcut = ShortcutInfoCompat.Builder(this, "unlock_credentials")
            .setShortLabel(getString(R.string.credentials_unlock))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_credentials))
            .setIntent(Intent(this, Credentials::class.java).setAction(Intent.ACTION_VIEW))
            .build()
        // AndroidX supplies both the system ShortcutInfo and legacy result extras.
        setResult(RESULT_OK, ShortcutManagerCompat.createShortcutResultIntent(this, shortcut))
        finish()
    }
}
