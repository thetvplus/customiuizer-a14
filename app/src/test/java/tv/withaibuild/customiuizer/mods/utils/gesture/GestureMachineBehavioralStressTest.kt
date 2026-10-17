package tv.withaibuild.customiuizer.mods.utils.gesture

import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Deterministic behavioural stress test for [GestureMachine] and [PhysicalGestureArbiter].
 *
 * The test runs three fixed random seeds.  For each seed it drives two owner-specific
 * [GestureMachine] instances (ownerIds 1 and 2) sharing one [PhysicalGestureArbiter],
 * one [RecordingExecutor] and one [GestureConfigPublisher].  Randomised sequences of
 * gesture events, owner attach/detach, resolver readiness toggles and config republishes
 * are generated and checked against a set of invariants.
 *
 * The invariants are deliberately strong: a failure is reported with the seed, the step
 * index, the generated step, the owner, the physical token, snapshots before/after, the
 * commands that were emitted and the current arbiter token map.
 */
class GestureMachineBehavioralStressTest {

    private val seeds = listOf(0xA14L, 0xC0570L, 0x51A7EL)
    private val stepsPerSeed = 10_000
    private val dummyContext = Any()

    private val actions = listOf(
        GestureAction.DOWN,
        GestureAction.MOVE,
        GestureAction.POINTER_DOWN,
        GestureAction.POINTER_UP,
        GestureAction.UP,
        GestureAction.CANCEL,
    )

    private val entries = listOf(
        GestureEntry.STATUS_BAR_TOUCH,
        GestureEntry.CONTROL_CENTER_TOUCH,
        GestureEntry.STATUS_BAR_INTERCEPT,
    )

    // --------------------------------------------------------------------------------------------
    // Test sink: records every command that reaches the effect executor, together with the owner
    // and the entry point it was generated for (the context passed to dispatch() is the event).
    // --------------------------------------------------------------------------------------------
    private class RecordingExecutor : GestureEffectExecutor {
        data class RecordedCommand(
            val ownerId: Int,
            val entry: GestureEntry,
            val command: GestureCommand,
        )

        val commands = mutableListOf<RecordedCommand>()

        override fun execute(
            commands: List<GestureCommand>,
            dependencies: GestureDependencies,
            config: GestureConfig,
            context: Any?,
        ) {
            val event = context as? GestureEvent
            for (command in commands) {
                this.commands.add(
                    RecordedCommand(
                        ownerId = dependencies.ownerId,
                        entry = event?.entry ?: GestureEntry.STATUS_BAR_TOUCH,
                        command = command,
                    ),
                )
            }
        }
    }

    // --------------------------------------------------------------------------------------------
    // Resolver whose readiness can be toggled on/off.
    // --------------------------------------------------------------------------------------------
    private class ToggleableResolver(
        private val ownerId: Int,
        private val deps: (Int) -> GestureDependencies,
    ) : GestureDependenciesResolver {
        @Volatile
        var ready = true

        override fun prepare(
            ownerId: Int,
            classLoaderIdentity: String,
            context: Any,
        ): GestureDependenciesResult = if (ready) {
            GestureDependenciesResult.Ready(deps(this.ownerId))
        } else {
            GestureDependenciesResult.NotReady
        }
    }

    // --------------------------------------------------------------------------------------------
    // Minimal display manager stand-in that supports brightness reading.
    // --------------------------------------------------------------------------------------------
    private class DisplayStub {
        var currentBrightness: Float = 0.5f
        fun getBrightness(displayId: Int): Float = currentBrightness
        fun setTemporaryBrightness(displayId: Int, value: Float) {}
        fun setBrightness(displayId: Int, value: Float) {}
    }

    private sealed class Step {
        data class Event(val ownerId: Int, val event: GestureEvent) : Step()
        data class Attach(val ownerId: Int) : Step()
        data class Detach(val ownerId: Int) : Step()
        data class ToggleResolver(val ownerId: Int) : Step()
        object PublishConfig : Step()
    }

    // Context captured for a single event step.  It is declared outside the inner Harness
    // because Kotlin does not allow non-inner nested classes inside an inner class.
    private data class EventContext(
        val ownerId: Int,
        val event: GestureEvent,
        val before: GestureSnapshot,
        val after: GestureSnapshot,
        val observed: List<GestureCommand>,
        val emitted: List<RecordingExecutor.RecordedCommand>,
        val arbiterBefore: Map<PhysicalGestureArbiter.Token, Int>,
        val arbiterAfter: Map<PhysicalGestureArbiter.Token, Int>,
    )

    @Test
    fun threeSeedsPassBehavioralStress() {
        for (seed in seeds) {
            Harness(seed).run()
        }
    }

    private inner class Harness(val seed: Long) {
        val random = Random(seed)
        val arbiter = PhysicalGestureArbiter()
        val executor = RecordingExecutor()

        var baseConfig: GestureConfig = randomConfig()
        val publisher = GestureConfigPublisher({ baseConfig })

        val resolvers = (1..2).associateWith { ownerId ->
            ToggleableResolver(ownerId) { dependenciesFor(it) }
        }

        val machines = (1..2).associateWith { ownerId ->
            GestureMachine(
                classLoaderIdentity = "behavioral-stress",
                configResolver = { publisher.get() },
                depsResolver = resolvers.getValue(ownerId),
                effectExecutor = executor,
                arbiter = arbiter,
            )
        }

        val attached = mutableMapOf(1 to false, 2 to false)
        val activeToken = mutableMapOf<Int, PhysicalGestureArbiter.Token?>(1 to null, 2 to null)
        val gestureConfig = mutableMapOf<Int, GestureConfig?>(1 to null, 2 to null)

        val commitCounts = mutableMapOf<PhysicalGestureArbiter.Token, Int>()
        val doubleTapCounts = mutableMapOf<PhysicalGestureArbiter.Token, Int>()
        val longPressCounts = mutableMapOf<PhysicalGestureArbiter.Token, Int>()

        var time = 100_000L
        var stepIndex = -1

        init {
            publisher.publish()
        }

        fun run() {
            repeat(stepsPerSeed) { index ->
                stepIndex = index
                val step = nextStep()
                val context = executeAndReturnContext(step)

                checkInvariants(step, context)
            }
        }

        // ----------------------------------------------------------------------------------------
        // Helpers
        // ----------------------------------------------------------------------------------------

        private fun dependenciesFor(ownerId: Int): GestureDependencies {
            val stub = DisplayStub()
            return GestureDependencies(
                ownerId = ownerId,
                classLoaderIdentity = "behavioral-stress",
                displayManager = stub,
                displayId = 0,
                minimumBacklight = 0.0f,
                maximumBacklight = 1.0f,
                audioManager = Any(),
                statusBarHeight = 80,
                screenWidth = 1080,
                density = 3.0f,
                getBrightnessMethod = DisplayStub::class.java
                    .getMethod("getBrightness", Int::class.java),
            )
        }

        private fun randomConfig(): GestureConfig = GestureConfig(
            singleAction = random.nextInt(1, 5),
            dualAction = random.nextInt(1, 5),
            doubleTapAction = random.nextInt(1, 5),
            doubleTapLeftAction = random.nextInt(1, 5),
            doubleTapRightAction = random.nextInt(1, 5),
            longPressAction = random.nextInt(1, 5),
            brightnessSensitivityFactor = random.nextFloat() * 1.9f + 0.1f,
            volumeSensitivityFactor = random.nextFloat() * 1.9f + 0.1f,
            longPressVibrate = false,
            ignoreVibrateOff = false,
        )

        private fun otherOwner(ownerId: Int): Int = if (ownerId == 1) 2 else 1

        private fun tokenOf(event: GestureEvent): PhysicalGestureArbiter.Token =
            PhysicalGestureArbiter.Token(event.downTime, event.deviceId, event.source)

        private fun isBusiness(command: GestureCommand): Boolean = when (command) {
            is GestureCommand.ApplyTemporaryBrightness,
            is GestureCommand.AdjustVolume,
            is GestureCommand.CommitBrightness,
            is GestureCommand.TriggerDoubleTap,
            is GestureCommand.TriggerLongPress -> true
            else -> false
        }

        private fun randomOwner(): Int = if (random.nextBoolean()) 1 else 2

        // ----------------------------------------------------------------------------------------
        // Step generation
        // ----------------------------------------------------------------------------------------

        private fun nextStep(): Step {
            val kind = random.nextInt(0, 100)
            val owner = randomOwner()
            return when {
                kind < 85 -> generateEvent(owner)
                kind < 90 -> Step.Attach(owner)
                kind < 95 -> Step.Detach(owner)
                kind < 98 -> Step.ToggleResolver(owner)
                else -> Step.PublishConfig
            }
        }

        private fun generateEvent(ownerId: Int): Step.Event {
            val action = actions.random(random)
            val entry = entries.random(random)
            val token = when (action) {
                GestureAction.DOWN -> chooseDownToken(ownerId)
                GestureAction.UP, GestureAction.CANCEL -> chooseUpOrCancelToken(ownerId)
                else -> chooseNonDownToken(ownerId)
            }

            val pointerCount = random.nextInt(1, 4) // 1..3
            val x = random.nextFloat() * 1080f
            val y = random.nextFloat() * 200f

            time += random.nextLong(5L, 51L)

            val event = GestureEvent(
                entry = entry,
                actionMasked = action,
                downTime = token.downTime,
                eventTime = time,
                x = x,
                y = y,
                pointerCount = pointerCount,
                ownerId = ownerId,
                deviceId = token.deviceId,
                source = token.source,
            )
            return Step.Event(ownerId, event)
        }

        private fun chooseDownToken(ownerId: Int): PhysicalGestureArbiter.Token {
            // Starting a new gesture for an already-active owner: use a brand-new token.
            // Starting a new gesture for an idle owner: sometimes deliberately reuse another
            // owner"s active token to exercise the arbiter conflict path.
            if (activeToken[ownerId] != null) {
                return freshToken()
            }
            val other = otherOwner(ownerId)
            val otherToken = activeToken[other]
            if (otherToken != null && random.nextInt(0, 4) == 0) {
                return otherToken
            }
            return freshToken()
        }

        private fun chooseUpOrCancelToken(ownerId: Int): PhysicalGestureArbiter.Token {
            // A UP/CANCEL for an active gesture must use the active token, otherwise the
            // machine would not end the tracked session.  For an idle owner any token works
            // because a stray UP/CANCEL is rejected by the arbiter.
            return activeToken[ownerId] ?: run {
                val other = otherOwner(ownerId)
                activeToken[other] ?: freshToken()
            }
        }

        private fun chooseNonDownToken(ownerId: Int): PhysicalGestureArbiter.Token {
            // For an active owner, prefer the active token.  Occasionally inject another
            // owner"s active token to make sure the arbiter rejects cross-owner events.
            // For an idle owner, use another owner"s active token (rejected if still held)
            // or a fresh token.
            val active = activeToken[ownerId]
            if (active != null) {
                val other = otherOwner(ownerId)
                val otherToken = activeToken[other]
                if (otherToken != null && random.nextInt(0, 5) == 0) {
                    return otherToken
                }
                return active
            }
            val other = otherOwner(ownerId)
            val otherToken = activeToken[other]
            if (otherToken != null && random.nextInt(0, 3) == 0) {
                return otherToken
            }
            return freshToken()
        }

        private fun freshToken(): PhysicalGestureArbiter.Token = PhysicalGestureArbiter.Token(
            downTime = time,
            deviceId = random.nextInt(0, 1000),
            source = random.nextInt(0, 1000),
        )

        // ----------------------------------------------------------------------------------------
        // Step execution
        // ----------------------------------------------------------------------------------------

        private fun executeAndReturnContext(step: Step): EventContext? {
            return when (step) {
                is Step.Event -> {
                    val ctx = runEvent(step)
                    updateModel(step.ownerId)
                    ctx
                }
                is Step.Attach -> {
                    attachOwner(step.ownerId)
                    null
                }
                is Step.Detach -> {
                    detachOwner(step.ownerId)
                    null
                }
                is Step.ToggleResolver -> {
                    toggleResolver(step.ownerId)
                    null
                }
                is Step.PublishConfig -> {
                    publishConfig()
                    null
                }
            }
        }

        private fun runEvent(step: Step.Event): EventContext {
            val (ownerId, event) = step
            val machine = machines.getValue(ownerId)
            val before = machine.snapshot(ownerId)
            val beforeCount = executor.commands.size
            val arbiterBefore = arbiter.heldTokens()

            val observed: List<GestureCommand> = if (event.entry == GestureEntry.STATUS_BAR_INTERCEPT) {
                machine.observe(event, event)
            } else {
                machine.dispatch(event, event)
                emptyList()
            }

            val after = machine.snapshot(ownerId)
            val emitted = if (event.entry == GestureEntry.STATUS_BAR_INTERCEPT) {
                emptyList()
            } else {
                executor.commands.subList(beforeCount, executor.commands.size).toList()
            }
            val arbiterAfter = arbiter.heldTokens()

            return EventContext(ownerId, event, before, after, observed, emitted, arbiterBefore, arbiterAfter)
        }

        private fun attachOwner(ownerId: Int) {
            attached[ownerId] = true
            machines.getValue(ownerId).prepare(ownerId, dummyContext)
        }

        private fun detachOwner(ownerId: Int) {
            attached[ownerId] = false
            machines.getValue(ownerId).clear(ownerId)
            activeToken[ownerId] = null
            gestureConfig[ownerId] = null
        }

        private fun toggleResolver(ownerId: Int) {
            val resolver = resolvers.getValue(ownerId)
            resolver.ready = !resolver.ready
            if (attached.getValue(ownerId)) {
                machines.getValue(ownerId).prepare(ownerId, dummyContext)
            }
        }

        private fun publishConfig() {
            baseConfig = randomConfig()
            publisher.publish()
        }

        private fun updateModel(ownerId: Int) {
            val machine = machines.getValue(ownerId)
            val after = machine.snapshot(ownerId)

            if (after.state == GestureState.IDLE) {
                activeToken[ownerId] = null
                gestureConfig[ownerId] = null
                return
            }

            // A non-IDLE snapshot must be backed by exactly one token owned by this owner.
            val token = arbiter.heldTokens().entries.find { it.value == ownerId }?.key
            if (token == null) {
                activeToken[ownerId] = null
                gestureConfig[ownerId] = null
                return
            }

            activeToken[ownerId] = token
            gestureConfig[ownerId] = machine.resolvedConfig(ownerId)
        }

        // ----------------------------------------------------------------------------------------
        // Invariant checks
        // ----------------------------------------------------------------------------------------

        private fun checkInvariants(step: Step, context: EventContext?) {
            checkArbiterIntegrity(step)
            checkConfigConsistency(step)
            if (step is Step.Event && context != null) {
                checkEventInvariants(step, context)
            }
            if (step is Step.Detach) {
                checkDetachInvariant(step)
            }
        }

        private fun checkArbiterIntegrity(step: Step) {
            val tokens = arbiter.heldTokens()

            // No token can be owned by two different owners simultaneously.  The map itself
            // enforces this, but we verify the contents explicitly.
            val reverse = mutableMapOf<PhysicalGestureArbiter.Token, Int>()
            for ((token, owner) in tokens) {
                if (reverse.put(token, owner) != null) {
                    fail(step, message = "Invariant: token $token is owned by more than one owner")
                }
            }

            for (ownerId in listOf(1, 2)) {
                val snapshot = machines.getValue(ownerId).snapshot(ownerId)
                val tokenCount = arbiter.tokensForOwner(ownerId)

                if (tokenCount > 1) {
                    fail(step, ownerId = ownerId, after = snapshot, message = "Invariant: owner $ownerId holds $tokenCount tokens")
                }

                if (snapshot.state != GestureState.IDLE && tokenCount == 0) {
                    fail(
                        step,
                        ownerId = ownerId,
                        after = snapshot,
                        message = "Invariant: owner $ownerId has an active snapshot but no arbiter token",
                    )
                }

                if (snapshot.state == GestureState.IDLE && tokenCount > 0) {
                    fail(
                        step,
                        ownerId = ownerId,
                        after = snapshot,
                        message = "Invariant: owner $ownerId has an IDLE snapshot but still holds a token",
                    )
                }
            }
        }

        private fun checkConfigConsistency(step: Step) {
            for (ownerId in listOf(1, 2)) {
                val machine = machines.getValue(ownerId)
                val snapshot = machine.snapshot(ownerId)
                val resolved = machine.resolvedConfig(ownerId)
                val expected = gestureConfig[ownerId]
                if (snapshot.state != GestureState.IDLE) {
                    if (resolved == null) {
                        fail(
                            step,
                            ownerId = ownerId,
                            after = snapshot,
                            message = "Invariant: active snapshot has no resolved config",
                        )
                    }
                    if (expected == null) {
                        fail(
                            step,
                            ownerId = ownerId,
                            after = snapshot,
                            message = "Invariant: active snapshot has no recorded gesture config",
                        )
                    }
                    if (resolved != expected) {
                        fail(
                            step,
                            ownerId = ownerId,
                            after = snapshot,
                            message = "Invariant: config changed while a gesture is in progress. " +
                                "resolved=$resolved, gestureConfig=$expected",
                        )
                    }
                }
            }
        }

        private fun checkDetachInvariant(step: Step.Detach) {
            val snapshot = machines.getValue(step.ownerId).snapshot(step.ownerId)
            if (snapshot.state != GestureState.IDLE) {
                fail(step, ownerId = step.ownerId, after = snapshot, message = "Detach did not leave snapshot IDLE")
            }
            if (arbiter.tokensForOwner(step.ownerId) > 0) {
                fail(step, ownerId = step.ownerId, after = snapshot, message = "Detach left an arbiter token for the owner")
            }
        }

        private fun checkEventInvariants(step: Step.Event, context: EventContext) {
            val ownerId = context.ownerId
            val event = context.event
            val before = context.before
            val after = context.after
            val emitted = context.emitted
            val machine = machines.getValue(ownerId)
            val token = tokenOf(event)
            val arbiterBefore = context.arbiterBefore
            val arbiterAfter = context.arbiterAfter

            // Invariant 1: observe() may not produce business side-effects and must not
            // change the authoritative snapshot.
            if (event.entry == GestureEntry.STATUS_BAR_INTERCEPT) {
                val business = context.observed.filter(::isBusiness)
                if (business.isNotEmpty()) {
                    fail(
                        step,
                        ownerId = ownerId,
                        event = event,
                        before = before,
                        after = after,
                        commands = emitted,
                        arbiterBefore = arbiterBefore,
                        arbiterAfter = arbiterAfter,
                        message = "Invariant 1: observe() produced business side-effects $business",
                    )
                }
                if (before != after) {
                    fail(
                        step,
                        ownerId = ownerId,
                        event = event,
                        before = before,
                        after = after,
                        commands = emitted,
                        arbiterBefore = arbiterBefore,
                        arbiterAfter = arbiterAfter,
                        message = "Invariant 1: observe() changed the authoritative snapshot",
                    )
                }
                return
            }

            // Non-DOWN events must never create a new token.
            if (event.actionMasked != GestureAction.DOWN && arbiterAfter.size > arbiterBefore.size) {
                fail(
                    step,
                    ownerId = ownerId,
                    event = event,
                    before = before,
                    after = after,
                    commands = emitted,
                    arbiterBefore = arbiterBefore,
                    arbiterAfter = arbiterAfter,
                    message = "Invariant: non-DOWN event increased the held token count",
                )
            }

            // DOWN-specific arbiter invariants.
            if (event.actionMasked == GestureAction.DOWN) {
                val acquired = arbiterAfter[token] == ownerId
                val startedGesture = after.state != GestureState.IDLE

                if (acquired != startedGesture) {
                    fail(
                        step,
                        ownerId = ownerId,
                        event = event,
                        before = before,
                        after = after,
                        commands = emitted,
                        arbiterBefore = arbiterBefore,
                        arbiterAfter = arbiterAfter,
                        message = "Invariant: DOWN acquired=$acquired but startedGesture=$startedGesture",
                    )
                }

                if (arbiterAfter.size > arbiterBefore.size && !acquired) {
                    fail(
                        step,
                        ownerId = ownerId,
                        event = event,
                        before = before,
                        after = after,
                        commands = emitted,
                        arbiterBefore = arbiterBefore,
                        arbiterAfter = arbiterAfter,
                        message = "Invariant: token count increased but this owner did not acquire the DOWN token",
                    )
                }

                if (!startedGesture) {
                    val business = emitted.filter { isBusiness(it.command) }
                    if (business.isNotEmpty()) {
                        fail(
                            step,
                            ownerId = ownerId,
                            event = event,
                            before = before,
                            after = after,
                            commands = emitted,
                            arbiterBefore = arbiterBefore,
                            arbiterAfter = arbiterAfter,
                            message = "Invariant: invalid/failed DOWN emitted business side-effects $business",
                        )
                    }
                    if (arbiter.tokensForOwner(ownerId) > 0) {
                        fail(
                            step,
                            ownerId = ownerId,
                            event = event,
                            before = before,
                            after = after,
                            commands = emitted,
                            arbiterBefore = arbiterBefore,
                            arbiterAfter = arbiterAfter,
                            message = "Invariant: invalid/failed DOWN left an arbiter token for the owner",
                        )
                    }
                }
            }

            // Invariant 2: CANCEL always leaves the machine in IDLE and releases the token.
            if (event.actionMasked == GestureAction.CANCEL) {
                if (after.state != GestureState.IDLE) {
                    fail(
                        step,
                        ownerId = ownerId,
                        event = event,
                        before = before,
                        after = after,
                        commands = emitted,
                        arbiterBefore = arbiterBefore,
                        arbiterAfter = arbiterAfter,
                        message = "Invariant 2: CANCEL did not leave snapshot IDLE (after=$after)",
                    )
                }
                if (arbiter.tokensForOwner(ownerId) > 0) {
                    fail(
                        step,
                        ownerId = ownerId,
                        event = event,
                        before = before,
                        after = after,
                        commands = emitted,
                        arbiterBefore = arbiterBefore,
                        arbiterAfter = arbiterAfter,
                        message = "Invariant 2: CANCEL left an arbiter token for owner $ownerId",
                    )
                }
            }

            // Invariant 3: UP after a tracked gesture releases the arbiter token.
            if (event.actionMasked == GestureAction.UP && before.state != GestureState.IDLE) {
                if (after.state != GestureState.IDLE) {
                    fail(
                        step,
                        ownerId = ownerId,
                        event = event,
                        before = before,
                        after = after,
                        commands = emitted,
                        arbiterBefore = arbiterBefore,
                        arbiterAfter = arbiterAfter,
                        message = "Invariant 3: UP after tracked gesture did not leave snapshot IDLE (after=$after)",
                    )
                }
                if (arbiter.tokensForOwner(ownerId) > 0) {
                    fail(
                        step,
                        ownerId = ownerId,
                        event = event,
                        before = before,
                        after = after,
                        commands = emitted,
                        arbiterBefore = arbiterBefore,
                        arbiterAfter = arbiterAfter,
                        message = "Invariant 3: UP after tracked gesture left an arbiter token for owner $ownerId",
                    )
                }
            }

            // Invariants 4 & 5: at most one CommitBrightness, TriggerDoubleTap and
            // TriggerLongPress per physical token.
            for (recorded in emitted) {
                val cmd = recorded.command
                val t = tokenOf(event)
                when (cmd) {
                    is GestureCommand.CommitBrightness -> {
                        commitCounts[t] = commitCounts.getOrDefault(t, 0) + 1
                        if (commitCounts.getValue(t) > 1) {
                            fail(
                                step,
                                ownerId = ownerId,
                                event = event,
                                before = before,
                                after = after,
                                commands = emitted,
                                arbiterBefore = arbiterBefore,
                                arbiterAfter = arbiterAfter,
                                message = "Invariant 4: more than one CommitBrightness for token $t",
                            )
                        }
                    }
                    is GestureCommand.TriggerDoubleTap -> {
                        doubleTapCounts[t] = doubleTapCounts.getOrDefault(t, 0) + 1
                        if (doubleTapCounts.getValue(t) > 1) {
                            fail(
                                step,
                                ownerId = ownerId,
                                event = event,
                                before = before,
                                after = after,
                                commands = emitted,
                                arbiterBefore = arbiterBefore,
                                arbiterAfter = arbiterAfter,
                                message = "Invariant 5: more than one TriggerDoubleTap for token $t",
                            )
                        }
                    }
                    is GestureCommand.TriggerLongPress -> {
                        longPressCounts[t] = longPressCounts.getOrDefault(t, 0) + 1
                        if (longPressCounts.getValue(t) > 1) {
                            fail(
                                step,
                                ownerId = ownerId,
                                event = event,
                                before = before,
                                after = after,
                                commands = emitted,
                                arbiterBefore = arbiterBefore,
                                arbiterAfter = arbiterAfter,
                                message = "Invariant 5: more than one TriggerLongPress for token $t",
                            )
                        }
                    }
                    else -> {}
                }
            }

            // Invariant 6: sliding state can only appear after a DOWN and a threshold-crossing
            // MOVE, and never without a preceding non-IDLE state for the same owner.
            if (after.state == GestureState.SLIDING_BRIGHTNESS || after.state == GestureState.SLIDING_VOLUME) {
                if (before.state == GestureState.IDLE) {
                    fail(
                        step,
                        ownerId = ownerId,
                        event = event,
                        before = before,
                        after = after,
                        commands = emitted,
                        arbiterBefore = arbiterBefore,
                        arbiterAfter = arbiterAfter,
                        message = "Invariant 6: sliding state ${after.state} appeared from IDLE without a preceding DOWN",
                    )
                }
                if (before.state != after.state) {
                    if (event.actionMasked != GestureAction.MOVE) {
                        fail(
                            step,
                            ownerId = ownerId,
                            event = event,
                            before = before,
                            after = after,
                            commands = emitted,
                            arbiterBefore = arbiterBefore,
                            arbiterAfter = arbiterAfter,
                            message = "Invariant 6: transition to ${after.state} was caused by action ${event.actionMasked}, not MOVE",
                        )
                    }
                    if (before.state != GestureState.TRACKING) {
                        fail(
                            step,
                            ownerId = ownerId,
                            event = event,
                            before = before,
                            after = after,
                            commands = emitted,
                            arbiterBefore = arbiterBefore,
                            arbiterAfter = arbiterAfter,
                            message = "Invariant 6: transition to ${after.state} came from ${before.state}, not TRACKING",
                        )
                    }
                    val config = machine.resolvedConfig(ownerId)
                        ?: fail(
                            step,
                            ownerId = ownerId,
                            event = event,
                            before = before,
                            after = after,
                            commands = emitted,
                            arbiterBefore = arbiterBefore,
                            arbiterAfter = arbiterAfter,
                            message = "Invariant 6: sliding state requires a resolved config",
                        )
                    val effectiveAction = if (before.session.startPointerCount >= 2) {
                        config.dualAction
                    } else {
                        config.singleAction
                    }
                    val threshold = 1080f / 10f
                    val delta = event.x - before.session.startX
                    if (abs(delta) <= threshold) {
                        fail(
                            step,
                            ownerId = ownerId,
                            event = event,
                            before = before,
                            after = after,
                            commands = emitted,
                            arbiterBefore = arbiterBefore,
                            arbiterAfter = arbiterAfter,
                            message = "Invariant 6: transition to ${after.state} but |x-startX|=$delta <= threshold $threshold",
                        )
                    }
                    val expected = when (effectiveAction) {
                        2 -> if (before.session.startBrightnessRatio >= 0f) GestureState.SLIDING_BRIGHTNESS else null
                        3 -> GestureState.SLIDING_VOLUME
                        else -> null
                    }
                    if (expected == null) {
                        fail(
                            step,
                            ownerId = ownerId,
                            event = event,
                            before = before,
                            after = after,
                            commands = emitted,
                            arbiterBefore = arbiterBefore,
                            arbiterAfter = arbiterAfter,
                            message = "Invariant 6: effectiveAction=$effectiveAction cannot produce a sliding state",
                        )
                    }
                    if (expected != after.state) {
                        fail(
                            step,
                            ownerId = ownerId,
                            event = event,
                            before = before,
                            after = after,
                            commands = emitted,
                            arbiterBefore = arbiterBefore,
                            arbiterAfter = arbiterAfter,
                            message = "Invariant 6: expected $expected from effectiveAction=$effectiveAction, got ${after.state}",
                        )
                    }
                    if (event.y - before.session.startY > 80f) {
                        fail(
                            step,
                            ownerId = ownerId,
                            event = event,
                            before = before,
                            after = after,
                            commands = emitted,
                            arbiterBefore = arbiterBefore,
                            arbiterAfter = arbiterAfter,
                            message = "Invariant 6: MOVE crossed vertical reset boundary yet produced ${after.state}",
                        )
                    }
                }
            }

            // Invariant 8: no business effect may be emitted for a detached owner.
            for (recorded in emitted) {
                if (isBusiness(recorded.command) && attached.getValue(recorded.ownerId).not()) {
                    fail(
                        step,
                        ownerId = ownerId,
                        event = event,
                        before = before,
                        after = after,
                        commands = emitted,
                        arbiterBefore = arbiterBefore,
                        arbiterAfter = arbiterAfter,
                        message = "Invariant 8: business effect ${recorded.command} produced for detached owner ${recorded.ownerId}",
                    )
                }
            }
        }

        // ----------------------------------------------------------------------------------------
        // Failure reporting
        // ----------------------------------------------------------------------------------------

        private fun fail(
            step: Step,
            ownerId: Int? = null,
            event: GestureEvent? = null,
            before: GestureSnapshot? = null,
            after: GestureSnapshot? = null,
            commands: List<RecordingExecutor.RecordedCommand> = emptyList(),
            arbiterBefore: Map<PhysicalGestureArbiter.Token, Int> = emptyMap(),
            arbiterAfter: Map<PhysicalGestureArbiter.Token, Int> = emptyMap(),
            message: String,
        ): Nothing {
            val token = event?.let { tokenOf(it) }

            val detail = buildString {
                appendLine("Behavioral stress invariant failure")
                appendLine("  seed = 0x${seed.toString(16)}L ($seed)")
                appendLine("  step index = $stepIndex")
                appendLine("  step = $step")
                appendLine("  violated = $message")
                if (ownerId != null) appendLine("  owner = $ownerId")
                if (event != null) {
                    appendLine("  event = $event")
                    appendLine("  token = $token")
                }
                if (before != null) appendLine("  snapshot before = $before")
                if (after != null) appendLine("  snapshot after = $after")
                if (commands.isNotEmpty()) {
                    appendLine("  commands emitted = $commands")
                }
                appendLine("  arbiter before = $arbiterBefore")
                appendLine("  arbiter after = $arbiterAfter")
                appendLine("  current arbiter state = ${arbiter.heldTokens()}")
            }
            throw AssertionError(detail)
        }
    }
}
