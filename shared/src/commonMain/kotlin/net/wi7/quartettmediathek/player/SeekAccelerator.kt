package net.wi7.quartettmediathek.player

/**
 * Seek behaviour of the player's progress bar (see docs/SPEC.md, "Player").
 *
 * - A short press (released before [holdThresholdMs]) jumps [tapBackSeconds] back or [tapForwardSeconds] ahead.
 * - Holding the key scrubs with growing steps taken from [steps] (elapsed hold time in ms to step in seconds),
 *   one step every [repeatIntervalMs]. The jump is applied on release.
 *
 * Pure logic without Android dependencies so it can be unit tested.
 */
class SeekAccelerator(
    val tapBackSeconds: Int = 10,
    val tapForwardSeconds: Int = 30,
    val holdThresholdMs: Long = 350,
    val repeatIntervalMs: Long = 180,
    val steps: List<Pair<Long, Int>> = listOf(0L to 10, 1200L to 30, 2400L to 60, 3600L to 120, 5000L to 300),
) {
    /** Current state of a press; null while no key is held. */
    data class Press(
        val direction: Int,
        val startedAtMs: Long,
        val lastStepAtMs: Long,
        val basePositionSeconds: Long,
        val deltaSeconds: Long,
        val scrubbing: Boolean,
        val stepSeconds: Int,
    )

    fun start(direction: Int, nowMs: Long, positionSeconds: Long): Press {
        require(direction == 1 || direction == -1)
        return Press(direction, nowMs, nowMs, positionSeconds, 0, false, steps.first().second)
    }

    /** Call on every key repeat (or timer tick while a button is held). */
    fun repeat(press: Press, nowMs: Long): Press {
        val held = nowMs - press.startedAtMs
        if (held < holdThresholdMs || nowMs - press.lastStepAtMs < repeatIntervalMs) return press
        val step = steps.last { held >= it.first }.second
        return press.copy(
            lastStepAtMs = nowMs,
            deltaSeconds = press.deltaSeconds + press.direction * step,
            scrubbing = true,
            stepSeconds = step,
        )
    }

    /** Returns the target position in seconds, clamped to [0, durationSeconds]. */
    fun release(press: Press, durationSeconds: Long): Long {
        val delta = if (press.scrubbing) press.deltaSeconds
        else if (press.direction > 0) tapForwardSeconds.toLong() else -tapBackSeconds.toLong()
        return (press.basePositionSeconds + delta).coerceIn(0, durationSeconds)
    }
}
