package tv.withaibuild.customiuizer.mods.island

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.lang.reflect.Field
import java.lang.reflect.Method

/** Native configuration and one scalar getter only. Never reads a PCM buffer or owns
 * the recorder. A binding lives only as long as the current recording-state lease. */
internal class IslandRecorderPeakAccess(private val amplitude: Method, private val engineField: Field,
    private val inspect: (Class<*>) -> List<Field> = ::recorderFields) {
    init { amplitude.isAccessible = true; engineField.isAccessible = true }
    // Main-thread owned, capacity one class. Metadata contains no Service, recorder,
    // levels or samples. Replacing the engine class drops the previous entry.
    private var metadataType: Class<*>? = null
    private var metadata: List<Field> = emptyList()

    fun bind(service: Any): Reader? {
        val engine = engineField.get(service) ?: return null
        if (metadataType !== engine.javaClass) {
            val fields = inspect(engine.javaClass).toList()
            fields.forEach { it.isAccessible = true }
            metadata = fields; metadataType = engine.javaClass
        }
        var nativeField: Field? = null
        var recorder: Any? = null
        for (field in metadata) {
            val value = field.get(engine) ?: continue
            if (recorder != null) return null // An ambiguous engine cannot publish invented levels.
            recorder = value; nativeField = field
        }
        val fullScale = when (recorder) {
            is AudioRecord -> when (recorder.audioFormat) {
                AudioFormat.ENCODING_PCM_16BIT -> 32767
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> 8388607
                AudioFormat.ENCODING_PCM_32BIT -> Int.MAX_VALUE
                else -> return null
            }
            // MediaRecorder.getMaxAmplitude has the platform's signed-16-bit peak range.
            is MediaRecorder -> 32767
            else -> return null
        }
        return Reader(service, engine, engineField, checkNotNull(nativeField), recorder, amplitude, fullScale)
    }

    class Reader internal constructor(private val service: Any, private val engine: Any,
        private val engineField: Field, private val nativeField: Field, private val recorder: Any,
        private val amplitude: Method, private val fullScale: Int) {
        fun owns(owner: Any?) = service === owner
        private fun current() = engineField.get(service) === engine && nativeField.get(engine) === recorder
        fun read(): Int {
            if (!current()) return -1
            val raw = amplitude.invoke(service) as Int
            if (!current()) return -1
            return canonical(raw, fullScale)
        }
    }

    companion object {
        internal fun recorderFields(engineType: Class<*>): List<Field> {
            val fields = ArrayList<Field>(1)
            var type: Class<*>? = engineType
            var depth = 0
            while (type != null && type != Any::class.java && depth++ < 8) {
                for (field in type.declaredFields) if (!java.lang.reflect.Modifier.isStatic(field.modifiers) &&
                    (field.type == AudioRecord::class.java || field.type == MediaRecorder::class.java)) fields.add(field)
                type = type.superclass
            }
            return fields
        }

        /** Convert before the Binder bridge's S16 range check. Wider peaks must never
         * be clipped as though 32767 were their full scale. No sample-wise gain/AGC. */
        fun canonical(raw: Int, fullScale: Int): Int {
            if (raw < 0 || fullScale != 32767 && fullScale != 8388607 && fullScale != Int.MAX_VALUE) return -1
            return (raw.coerceAtMost(fullScale).toLong() * 32767L / fullScale).toInt()
        }
    }
}
