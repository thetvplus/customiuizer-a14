package tv.withaibuild.customiuizer.mods.duostatusbar

/** 24B91020 modern Animation.default: response .50, damping 1.00.
 * The native scalar consumer integrates at 1/300s with two half velocity steps.
 * Only visible finite transitions sample it; this object never schedules work. */
internal class DuoSpring(initial: Float) {
    var value=initial; private set
    private var velocity=0.0
    private var target=initial
    private var at=0L
    private var remainder=0.0
    private var scale=1.0
    val settled get()=kotlin.math.abs(value-target)<.001f && kotlin.math.abs(velocity)<.005
    fun snap(value: Float) { this.value=value; target=value; velocity=0.0; remainder=0.0 }
    fun finish()=snap(target)
    fun retarget(target: Float,now: Long,timeScale: Float=1f) {
        if(target==this.target) return
        sample(now)
        this.target=target; at=now
        scale=if(timeScale.isFinite() && timeScale>0f) timeScale.toDouble() else 1.0
    }
    fun sample(now: Long) {
        if(now<=at) return
        if(settled) { at=now; return }
        // Normalize before bounding: truncating the scaled millisecond horizon
        // to Long made sub-millisecond scales execute zero steps forever.
        val steps=((now-at).toDouble()/scale).coerceAtMost(1500.0)*.3+remainder
        var count=steps.toInt(); remainder=steps-count; at=now
        // Exact power of the existing two-half-step native integrator. A stalled
        // frame needs at most nine matrix powers instead of 450 iterations.
        val dt=1.0/300.0
        val omega=2.0*kotlin.math.PI/.5
        val a=1.0-omega*dt; val b=omega*omega*dt*.5
        var m00=1.0-dt*b; var m01=dt*a
        var m10=-b*(a+m00); var m11=a*(a-b*dt)
        var x=value.toDouble()-target
        while(count>0) {
            if(count and 1 != 0) {
                val next=m00*x+m01*velocity
                velocity=m10*x+m11*velocity; x=next
            }
            count=count ushr 1
            if(count>0) {
                val n00=m00*m00+m01*m10; val n01=m01*(m00+m11)
                val n10=m10*(m00+m11); val n11=m10*m01+m11*m11
                m00=n00; m01=n01; m10=n10; m11=n11
            }
        }
        value=(target+x).toFloat()
        if(settled) finish()
    }
}

/** Two live glyphs at most. Native opacity/scale share the transaction; there
 * is no artificial blank midpoint. Reversal retains both visible values. */
internal class DuoTransition {
    var current=-1; private set
    var previous=-1; private set
    private var incoming=DuoSpring(1f)
    private var outgoing=DuoSpring(0f)
    private var startedAt=0L
    private var horizon=1500L
    val running get()=current!=previous
    val currentAlpha get()=(incoming.value.coerceIn(0f,1f)*255f+.5f).toInt()
    val previousAlpha get()=if(running) (outgoing.value.coerceIn(0f,1f)*255f+.5f).toInt() else 0
    fun replaceCurrent(next: Int) {
        if(previous==current) previous=next
        current=next
    }
    fun change(next: Int,now: Long,animate: Boolean,timeScale: Float=1f): Boolean {
        if(next==current) return false
        fraction(now)
        if(current<0 || !animate) { current=next; finish(); return true }
        if(next==previous) {
            val key=previous; previous=current; current=key
            val old=outgoing; outgoing=incoming; incoming=old
        } else {
            if(incoming.value>=outgoing.value) {
                previous=current
                val old=outgoing; outgoing=incoming; incoming=old
            }
            current=next; incoming.snap(0f)
        }
        outgoing.retarget(0f,now,timeScale); incoming.retarget(1f,now,timeScale); startedAt=now
        horizon=(1500L*timeScale.coerceAtLeast(.01f)).toLong()
        return true
    }
    fun fraction(now: Long): Float {
        if(!running) return 1f
        outgoing.sample(now); incoming.sample(now)
        // Opacity precision and a finite safety horizon are Android lifetime
        // bounds; the native retarget/termination owner has not been recovered.
        if(outgoing.settled && incoming.settled || now-startedAt>=horizon) finish()
        return incoming.value.coerceIn(0f,1f)
    }
    fun finish() { previous=current; incoming.snap(1f); outgoing.snap(0f) }
}

/** Cellular bars explicitly use the same native withAnimation wrapper. Wi-Fi
 * strength setters do not, so those arcs deliberately have no extra spring. */
internal class DuoCellularMotion {
    private val bars=Array(4) { DuoSpring(56f/255f) }
    private var seeded=false
    var running=false; private set
    fun change(network: Int,now: Long,animate: Boolean,timeScale: Float=1f) {
        for(i in bars.indices) {
            val target=(DuoGeometry.signalColor(-1,network,i) ushr 24)/255f
            if(animate && seeded) bars[i].retarget(target,now,timeScale) else bars[i].snap(target)
        }
        seeded=true
        running=bars.any { !it.settled }
    }
    fun sample(now: Long) {
        if(!running) return
        bars.forEach { it.sample(now) }
        running=bars.any { !it.settled }
    }
    fun color(foreground: Int,index: Int)=DuoGeometry.withAlpha(foreground,
        ((foreground ushr 24)*bars[index].value.coerceIn(0f,1f)+.5f).toInt())
    fun finish() { bars.forEach { it.finish() }; running=false }
}

/** Primary glyph identity; battery and network-strength updates do not animate. */
internal object DuoCenter {
    const val WARNING = 128
    fun key(network: Int, audio: Int, config: DuoConfig): Int {
        val glyph = DuoAudioState.glyph(network, if (config.showAudio) audio else 0, config.networkFallback)
        val value = when (glyph) {
            DuoAudioState.WIFI_GLYPH -> DuoNetworkState.wifiLevel(network)
            DuoAudioState.BLUETOOTH_GLYPH -> if (config.bluetoothBatteryColor) DuoAudioState.batteryStep(audio) else 0
            DuoAudioState.CELLULAR_GLYPH -> if (config.cellularStyle == 2) DuoNetworkState.cellularType(network) else 0
            else -> 0
        }
        return glyph or (value shl 3) or (if (network and DuoNetworkState.WIFI_UNVALIDATED != 0) WARNING else 0)
    }
    fun glyph(key: Int) = key and 7
    fun value(key: Int) = (key shr 3) and 15
}
