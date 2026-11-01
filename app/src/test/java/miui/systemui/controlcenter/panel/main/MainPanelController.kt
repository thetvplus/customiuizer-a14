package miui.systemui.controlcenter.panel.main

import android.content.Context
import android.view.View
import android.widget.LinearLayout

class MainPanelController(context: Context) {
    @JvmField var useSeparatedPanels: Boolean? = null
    @JvmField val horizontalMainPanel = LinearLayout(context)
    @JvmField val leftMainPanel = LinearLayout(context)
    @JvmField val rightMainPanel = LinearLayout(context)
    init {
        horizontalMainPanel.addView(leftMainPanel)
        horizontalMainPanel.addView(rightMainPanel)
    }
    fun setUseSeparatedPanels(value: Boolean?) {
        useSeparatedPanels = value
        if (value == false) horizontalMainPanel.removeView(leftMainPanel)
    }
}
