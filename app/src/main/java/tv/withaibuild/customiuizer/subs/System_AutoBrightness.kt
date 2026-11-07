package tv.withaibuild.customiuizer.subs

import android.os.Bundle
import tv.withaibuild.customiuizer.SubFragment
import tv.withaibuild.customiuizer.prefs.SeekBarPreference

class System_AutoBrightness : SubFragment() {

    private var maxBrightness: SeekBarPreference? = null
    private var minBrightness: SeekBarPreference? = null

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        maxBrightness = findPreference("pref_key_system_autobrightness_max")
        minBrightness = findPreference("pref_key_system_autobrightness_min")
        maxBrightness?.setMinValue((minBrightness?.getValue() ?: 0) + 1)
        minBrightness?.setOnValueChangeListener(preview = { value ->
            if ((maxBrightness?.getValue() ?: 0) <= value) maxBrightness?.setValue(value + 1, true)
            maxBrightness?.setMinValue(value + 1)
        })
    }
}
