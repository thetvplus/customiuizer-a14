package tv.withaibuild.customiuizer.mods.island

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper

/** Read-only scalar bridge. Never creates an AudioRecord or returns recorded samples. */
internal object IslandRecorderMeter {
    private const val DESCRIPTOR = "tv.withaibuild.customiuizer.r14.RecorderLevel"
    private const val LEVEL = IBinder.FIRST_CALL_TRANSACTION
    /** Fixed speech display response to a 16-bit peak. Noise remains quiet; no per-frame AGC. */
    internal fun level(raw: Int): Float {
        if (raw <= 256) return 0f
        val x = (raw.coerceAtMost(32767) - 256) / 32511f
        return x
    }
    fun producer(systemUiUid: Int, level: () -> Int): IBinder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != LEVEL) return super.onTransact(code, data, reply, flags)
            if (getCallingUid() != systemUiUid) throw SecurityException("Recorder level owner")
            data.enforceInterface(DESCRIPTOR)
            data.enforceNoDataAvail()
            reply ?: return false
            val result = ModuleHelper.guarded(-1) { level().coerceIn(-1, 32767) }
            reply.writeNoException(); reply.writeInt(result)
            return true
        }
    }
    fun read(owner: IBinder): Int? {
        val input = Parcel.obtain(); val output = Parcel.obtain()
        return try {
            input.writeInterfaceToken(DESCRIPTOR)
            if (!owner.transact(LEVEL, input, output, 0)) null else {
                output.readException()
                output.readInt().takeIf { it in 0..32767 }
            }
        } finally { input.recycle(); output.recycle() }
    }

    /** One visible lease; pause/shade/foreground/detach cancels it. No broadcast per sample. */
    class Consumer(private val scope: CoroutineScope, private val io: CoroutineDispatcher,
        private val output: (FloatArray?) -> Unit) {
        private var owner: IBinder? = null
        private var job: Job? = null
        private var epoch = 0L
        fun start(next: IBinder) {
            if (owner == next && job?.isActive == true) return
            stop(); owner = next
            val generation = ++epoch
            job = scope.launch {
                val bars = FloatArray(5)
                var previous = -1f
                while (isActive && generation == epoch) {
                    val start = SystemClock.uptimeMillis()
                    val raw = withContext(io) { ModuleHelper.guarded<Int?>(null) { read(next) } } ?: break
                    val level = level(raw)
                    // A five-sample envelope, not invented frequency bands. Quiet remains quiet.
                    if (previous < 0f || kotlin.math.abs(level - previous) >= .004f || level == 0f && previous != 0f)
                        previous = level
                    if (bars.any { kotlin.math.abs(it - previous) >= .006f }) {
                        for (i in 0..3) bars[i] = bars[i + 1]
                        bars[4] = previous
                        output(bars)
                    }
                    delay((67L - (SystemClock.uptimeMillis() - start)).coerceAtLeast(1L))
                }
                if (generation == epoch) { owner = null; job = null; output(FloatArray(5)) }
            }
        }
        fun stop() {
            if (owner == null && job == null) return
            epoch++; owner = null; job?.cancel(); job = null; output(null)
        }
    }
}
