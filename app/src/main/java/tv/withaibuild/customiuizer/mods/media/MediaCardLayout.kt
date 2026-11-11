package tv.withaibuild.customiuizer.mods.media

import java.lang.reflect.Modifier

/** The ROM prunes several standard ConstraintSet methods. Use its retained
 * Constraint.clone and prepared layout fields; no bundled AndroidX dependency. */
internal class MediaCardLayout(private val sets: Class<*>) {
    private val construct = sets.getDeclaredConstructor().apply { isAccessible = true }
    private val constraints = sets.getDeclaredField("mConstraints").apply {
        isAccessible = true; require(Map::class.java.isAssignableFrom(type) && !Modifier.isStatic(modifiers))
    }
    private val attributes = sets.getDeclaredField("mSavedAttributes").apply {
        isAccessible = true; require(Map::class.java.isAssignableFrom(type) && !Modifier.isStatic(modifiers))
    }
    private val properties = listOf("mForceId", "mIdString", "mMatchLabels", "mRotate", "derivedState")
        .map { sets.getDeclaredField(it).apply {
            isAccessible = true; require(!Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers))
        } }
    private val constraint = sets.getDeclaredMethod("getConstraint", Int::class.javaPrimitiveType!!).returnType
    private val copy = constraint.declaredMethods.single {
        it.name == "clone" && it.parameterCount == 0 && it.returnType == constraint && !Modifier.isStatic(it.modifiers)
    }.apply { isAccessible = true }
    private val custom = constraint.getDeclaredField("mCustomConstraints").apply { isAccessible = true }
    private val layout = constraint.getDeclaredField("layout").apply { isAccessible = true }
    private val integer = listOf("leftToLeft", "leftToRight", "rightToLeft", "rightToRight", "startToStart", "startToEnd",
        "endToStart", "endToEnd", "topToTop", "topToBottom", "bottomToTop", "bottomToBottom", "baselineToBaseline",
        "baselineToTop", "baselineToBottom", "mWidth", "mHeight", "startMargin", "endMargin", "topMargin")
        .associateWith { name -> layout.type.getDeclaredField(name).apply {
            isAccessible = true; require(type == Int::class.javaPrimitiveType && !Modifier.isStatic(modifiers))
        } }
    private val bias = layout.type.getDeclaredField("horizontalBias").apply {
        isAccessible = true; require(type == Float::class.javaPrimitiveType && !Modifier.isStatic(modifiers))
    }

    @Suppress("UNCHECKED_CAST")
    internal fun prepare(original: Any, ids: Map<String, Int>, density: Float, compact: Boolean = false): Any {
        require(density.isFinite() && density > 0f)
        val source = constraints.get(original) as Map<Int, Any>
        val positioned = listOf("album_art", "icon", "header_title", "header_artist", "media_seamless", "media_progress_bar",
            "actionPlayPause", "actionNext", "actionPrev", "action0", "action1", "action2", "action3", "action4",
            "media_elapsed_time", "media_total_time", "media_scrubbing_elapsed_time", "media_scrubbing_total_time")
        require(positioned.all { ids[it] in source })
        val backdrop = requireNotNull(layout.get(source.getValue(ids.getValue("media_bg"))))
        val minimumHeight = ((if (compact) 140 else 168) * density + .5f).toInt()
        require(integer.getValue("mHeight").getInt(backdrop) >= minimumHeight) { "Unsupported media host height" }
        val result = construct.newInstance()
        for (field in properties) {
            val value = field.get(original)
            field.set(result, if (value is Array<*>) value.clone() else value)
        }
        (attributes.get(result) as MutableMap<Any, Any>).putAll(attributes.get(original) as Map<Any, Any>)
        val target = constraints.get(result) as MutableMap<Int, Any>
        for ((id, value) in source) {
            val duplicate = requireNotNull(copy.invoke(value)) { "Native constraint clone returned null" }
            (custom.get(duplicate) as MutableMap<Any, Any>).putAll(custom.get(value) as Map<Any, Any>)
            target[id] = duplicate
        }
        fun dp(value: Int) = (value * density + .5f).toInt()
        fun place(name: String, w: Int, h: Int, top: Int, horizontal: Float = .5f): Any {
            val native = requireNotNull(layout.get(target.getValue(ids.getValue(name)))) { "Native layout is absent" }
            for ((name, field) in integer) if (name.contains("To")) field.setInt(native, -1)
            integer.getValue("mWidth").setInt(native, dp(w))
            integer.getValue("mHeight").setInt(native, dp(h))
            integer.getValue("startToStart").setInt(native, 0)
            integer.getValue("endToEnd").setInt(native, 0)
            integer.getValue("topToTop").setInt(native, 0)
            integer.getValue("startMargin").setInt(native, dp(16))
            integer.getValue("endMargin").setInt(native, dp(16))
            integer.getValue("topMargin").setInt(native, dp(top))
            bias.setFloat(native, horizontal)
            return native
        }
        // Android layout policy. The reference establishes separation of header,
        // time and transport; these dp values are not recovered Apple dimensions.
        val artSize = if (compact) 44 else 52
        place("album_art", artSize, artSize, if (compact) 12 else 16, 0f)
        val app = place("icon", 16, 16, 0, 0f)
        integer.getValue("startToStart").setInt(app, ids.getValue("album_art"))
        integer.getValue("endToEnd").setInt(app, -1)
        integer.getValue("topToTop").setInt(app, ids.getValue("album_art"))
        integer.getValue("startMargin").setInt(app, dp(artSize - 16))
        integer.getValue("topMargin").setInt(app, dp(artSize - 16))
        place("media_seamless", 48, 48, if (compact) 10 else 18, 1f)
        for ((name, top) in listOf("header_title" to if (compact) 12 else 17,
            "header_artist" to if (compact) 34 else 43)) {
            val native = place(name, 0, 24, top)
            integer.getValue("startToStart").setInt(native, -1)
            integer.getValue("endToEnd").setInt(native, -1)
            integer.getValue("startToEnd").setInt(native, ids.getValue("album_art"))
            integer.getValue("endToStart").setInt(native, ids.getValue("media_seamless"))
            integer.getValue("startMargin").setInt(native, dp(12))
            integer.getValue("endMargin").setInt(native, dp(8))
        }
        // Preserve the native SeekBar target. Its thin track is centered inside
        // this row; labels sit below the ink and the transport target never overlaps it.
        place("media_progress_bar", 0, 32, if (compact) 56 else 76)
        // Native collapsed height is 140 dp, expanded is at least 168 dp.
        // Keep visible compact actions inside their host and retain 48 dp targets.
        val actionTop = if (compact) 92 else 120
        for ((name, position) in listOf("actionPrev" to .22f, "actionPlayPause" to .5f, "actionNext" to .78f))
            place(name, 48, 48, actionTop, position)
        for (index in 0..4) place("action$index", 48, 48, actionTop, index / 4f)
        // The native time row is constrained below action0. Keeping that chain
        // after moving transport enlarges the host and recenters its backdrop.
        val timeTop = if (compact) 76 else 96
        place("media_elapsed_time", 64, 16, timeTop, 0f)
        place("media_total_time", 64, 16, timeTop, 1f)
        place("media_scrubbing_elapsed_time", 48, 48, actionTop, 0f)
        place("media_scrubbing_total_time", 48, 48, actionTop, 1f)
        return result
    }
}
