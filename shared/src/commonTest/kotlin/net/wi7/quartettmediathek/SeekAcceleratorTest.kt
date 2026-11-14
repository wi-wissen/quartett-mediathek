package net.wi7.quartettmediathek

import net.wi7.quartettmediathek.player.SeekAccelerator
import kotlin.test.Test
import kotlin.test.assertEquals

class SeekAcceleratorTest {
    private val seek = SeekAccelerator()

    @Test
    fun tapForwardJumps30Seconds() {
        val p = seek.start(1, nowMs = 0, positionSeconds = 100)
        assertEquals(130, seek.release(p, durationSeconds = 3000))
    }

    @Test
    fun tapBackJumps10SecondsAndClampsAtZero() {
        assertEquals(90, seek.release(seek.start(-1, 0, 100), 3000))
        assertEquals(0, seek.release(seek.start(-1, 0, 4), 3000))
    }

    @Test
    fun holdingAcceleratesSteps() {
        var p = seek.start(1, 0, 0)
        var t = 0L
        while (t <= 1100) { p = seek.repeat(p, t); t += 60 }
        // after about one second the step size is already one minute
        assertEquals(60, p.stepSeconds)
        while (t <= 2500) { p = seek.repeat(p, t); t += 60 }
        // after 2.2 s it jumps five minutes per step
        assertEquals(300, p.stepSeconds)
        assertEquals(true, p.scrubbing)
    }
}
