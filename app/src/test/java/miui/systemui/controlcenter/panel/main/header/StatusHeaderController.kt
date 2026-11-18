package miui.systemui.controlcenter.panel.main.header

import android.content.Context
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import miui.systemui.controlcenter.R

class StatusHeaderController(context: Context) {
    private val header = ConstraintLayout(context)
    val carrier = TextView(context).apply { id = R.id.header_carrier_view }
    val icons = View(context).apply { id = R.id.header_status_bar_icons }
    var vertical = true
    var privacyPrompt = false
    var verticalBottomMargin = 24
    var carrierWrites = 0
        private set

    init {
        header.addView(carrier, ConstraintLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        header.addView(icons, ConstraintLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
    }

    fun getView() = header
    fun createStatusBarViews() = Unit

    fun updateConstraint() {
        val params = carrier.layoutParams as ConstraintLayout.LayoutParams
        params.bottomToTop = if (vertical) icons.id else -1
        params.bottomMargin = if (vertical) verticalBottomMargin else 0
    }

    fun adjustCarrierOrPrompt() {
        carrier.visibility = if (vertical && privacyPrompt) View.GONE else View.VISIBLE
    }

    fun onExpandChange(translation: Float) {
        carrier.translationY = translation
    }

    companion object {
        @JvmStatic fun `onCreate$lambda-2$lambda-0`(owner: StatusHeaderController, text: String?) {
            owner.carrierWrites++
            owner.carrier.text = text
            owner.adjustCarrierOrPrompt()
        }
    }
}
