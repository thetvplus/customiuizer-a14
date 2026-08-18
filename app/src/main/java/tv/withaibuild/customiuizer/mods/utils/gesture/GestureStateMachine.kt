package tv.withaibuild.customiuizer.mods.utils.gesture

import kotlin.math.abs

/**
 * Pure, Android-free state machine for status bar / control center gestures.
 *
 * The function is side-effect free: it consumes an immutable snapshot and event and returns
 * the next snapshot plus a list of commands.  No Android types, no reflection and no
 * preference access are used inside this class.
 */
object GestureStateMachine {

    private val CMD_PASS_THROUGH = listOf(GestureCommand.PassThrough)
    private val CMD_BEGIN_TRACKING = listOf(GestureCommand.BeginTracking)
    private val CMD_RESET = listOf(GestureCommand.Reset)

    /**
     * State transition for a single event.
     */
    fun process(
        snapshot: GestureSnapshot,
        event: GestureEvent,
        config: GestureConfig,
        geometry: GestureGeometry,
    ): Pair<GestureSnapshot, List<GestureCommand>> {
        return when (event.actionMasked) {
            GestureAction.DOWN -> handleDown(snapshot, event, config, geometry)
            GestureAction.MOVE -> handleMove(snapshot, event, config, geometry)
            GestureAction.UP -> handleUp(snapshot, event, config, geometry)
            GestureAction.CANCEL -> handleCancel(snapshot)
            GestureAction.POINTER_DOWN -> handlePointerDown(snapshot, event)
            GestureAction.POINTER_UP -> handlePointerUp(snapshot, event)
            else -> snapshot to CMD_PASS_THROUGH
        }
    }

    private fun handleDown(
        snapshot: GestureSnapshot,
        event: GestureEvent,
        config: GestureConfig,
        geometry: GestureGeometry,
    ): Pair<GestureSnapshot, List<GestureCommand>> {
        val isControlCenter = event.entry == GestureEntry.CONTROL_CENTER_TOUCH
        val isSlidingStart = !isControlCenter || event.y <= geometry.statusBarHeight
        if (!isSlidingStart) {
            return GestureSnapshot(GestureState.IDLE) to CMD_PASS_THROUGH
        }

        val nextSession = GestureSession(
            startX = event.x,
            startY = event.y,
            startTime = event.eventTime,
            startPointerCount = event.activePointerCount,
            startBrightnessRatio = geometry.currentBrightness,
            lastTouchX = snapshot.session.lastTouchX,
            lastTouchTime = snapshot.session.lastTouchTime,
            currentBrightnessRatio = -1f,
        )
        return GestureSnapshot(GestureState.TRACKING, nextSession) to CMD_BEGIN_TRACKING
    }

    private fun handleMove(
        snapshot: GestureSnapshot,
        event: GestureEvent,
        config: GestureConfig,
        geometry: GestureGeometry,
    ): Pair<GestureSnapshot, List<GestureCommand>> {
        val session = snapshot.session
        if (snapshot.state == GestureState.IDLE) return snapshot to emptyList()

        if (event.y - session.startY > geometry.statusBarHeight) {
            return GestureSnapshot(GestureState.IDLE) to CMD_RESET
        }

        val delta = event.x - session.startX
        if (delta == 0f) return snapshot to emptyList()

        val width = geometry.screenWidth.toFloat()
        val threshold = width / 10f
        val wasSliding = snapshot.state == GestureState.SLIDING_BRIGHTNESS || snapshot.state == GestureState.SLIDING_VOLUME

        val effectiveAction = if (session.startPointerCount >= 2) config.dualAction else config.singleAction

        if (!wasSliding && abs(delta) > threshold) {
            val nextState = when (effectiveAction) {
                2 -> GestureState.SLIDING_BRIGHTNESS
                3 -> GestureState.SLIDING_VOLUME
                else -> GestureState.TRACKING
            }
            if (nextState == GestureState.SLIDING_BRIGHTNESS && session.startBrightnessRatio < 0f) {
                return snapshot to emptyList()
            }
            if (nextState == snapshot.state) return snapshot to emptyList()
            val (nextSnapshot, commands) = handleMove(GestureSnapshot(nextState, session), event, config, geometry)
            return nextSnapshot to commands
        }

        return when (snapshot.state) {
            GestureState.SLIDING_BRIGHTNESS -> computeBrightness(snapshot, event, config, geometry, session)
            GestureState.SLIDING_VOLUME -> computeVolume(snapshot, event, config, geometry, session)
            else -> snapshot to emptyList()
        }
    }

    private fun computeBrightness(
        snapshot: GestureSnapshot,
        event: GestureEvent,
        config: GestureConfig,
        geometry: GestureGeometry,
        session: GestureSession,
    ): Pair<GestureSnapshot, List<GestureCommand>> {
        if (session.startBrightnessRatio < 0f) {
            return snapshot to emptyList()
        }
        val delta = event.x - session.startX
        val ratio = delta / geometry.screenWidth * config.brightnessSensitivityFactor
        var next = session.startBrightnessRatio + ratio
        next = next.coerceIn(geometry.minBacklight, geometry.maxBacklight)
        val nextSession = session.copy(currentBrightnessRatio = next)
        return GestureSnapshot(GestureState.SLIDING_BRIGHTNESS, nextSession) to
            listOf(GestureCommand.ApplyTemporaryBrightness(next))
    }

    private fun computeVolume(
        snapshot: GestureSnapshot,
        event: GestureEvent,
        config: GestureConfig,
        geometry: GestureGeometry,
        session: GestureSession,
    ): Pair<GestureSnapshot, List<GestureCommand>> {
        val delta = event.x - session.startX
        val threshold = geometry.screenWidth /
            (config.volumeSensitivityFactor * 20f * geometry.density)
        if (abs(delta) < threshold) return snapshot to emptyList()

        val raise = delta > 0
        val nextSession = session.copy(startX = event.x)
        return GestureSnapshot(GestureState.SLIDING_VOLUME, nextSession) to
            listOf(GestureCommand.AdjustVolume(raise))
    }

    private fun handleUp(
        snapshot: GestureSnapshot,
        event: GestureEvent,
        config: GestureConfig,
        geometry: GestureGeometry,
    ): Pair<GestureSnapshot, List<GestureCommand>> {
        if (snapshot.state == GestureState.IDLE) {
            return snapshot to emptyList()
        }

        val session = snapshot.session
        val commands = mutableListOf<GestureCommand>()

        if (snapshot.state == GestureState.SLIDING_BRIGHTNESS && session.currentBrightnessRatio >= 0f) {
            commands.add(GestureCommand.CommitBrightness(session.currentBrightnessRatio))
        }

        if (snapshot.state != GestureState.SLIDING_BRIGHTNESS && snapshot.state != GestureState.SLIDING_VOLUME) {
            val touchDelta = event.eventTime - session.lastTouchTime
            val touchXDelta = abs(event.x - session.lastTouchX)
            if (touchDelta in 1..249 && touchXDelta < 80f) {
                val position = when {
                    event.x * 5f < geometry.screenWidth -> DoubleTapPosition.LEFT
                    event.x > geometry.screenWidth * 0.8f -> DoubleTapPosition.RIGHT
                    else -> DoubleTapPosition.CENTER
                }
                val actionId = when (position) {
                    DoubleTapPosition.LEFT -> config.doubleTapLeftAction
                    DoubleTapPosition.CENTER -> config.doubleTapAction
                    DoubleTapPosition.RIGHT -> config.doubleTapRightAction
                }
                commands.add(GestureCommand.TriggerDoubleTap(position, actionId))
            } else if (event.eventTime - session.startTime in 601..3999
                && abs(event.x - session.startX) < 80f
            ) {
                commands.add(GestureCommand.TriggerLongPress(config.longPressAction))
            }
        }

        commands.add(GestureCommand.Reset)

        var nextSession = session.copy(
            currentBrightnessRatio = -1f,
            startPointerCount = event.activePointerCount,
        )
        if (snapshot.state != GestureState.SLIDING_BRIGHTNESS && snapshot.state != GestureState.SLIDING_VOLUME) {
            val isDoubleTap = commands.any { it is GestureCommand.TriggerDoubleTap }
            if (!isDoubleTap) {
                nextSession = nextSession.copy(lastTouchX = event.x, lastTouchTime = event.eventTime)
            }
        }

        return GestureSnapshot(GestureState.IDLE, nextSession) to commands
    }

    private fun handleCancel(snapshot: GestureSnapshot): Pair<GestureSnapshot, List<GestureCommand>> {
        val nextSession = snapshot.session.copy(currentBrightnessRatio = -1f)
        return GestureSnapshot(GestureState.IDLE, nextSession) to CMD_RESET
    }

    private fun handlePointerDown(snapshot: GestureSnapshot, event: GestureEvent): Pair<GestureSnapshot, List<GestureCommand>> {
        if (snapshot.state == GestureState.IDLE) return snapshot to emptyList()
        val nextSession = snapshot.session.copy(startPointerCount = event.activePointerCount)
        return snapshot.copy(session = nextSession) to emptyList()
    }

    private fun handlePointerUp(snapshot: GestureSnapshot, event: GestureEvent): Pair<GestureSnapshot, List<GestureCommand>> {
        if (snapshot.state == GestureState.IDLE) return snapshot to emptyList()
        val nextSession = snapshot.session.copy(startPointerCount = event.activePointerCount)
        return snapshot.copy(session = nextSession) to emptyList()
    }
}
