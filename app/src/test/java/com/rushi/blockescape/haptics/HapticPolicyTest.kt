package com.rushi.blockescape.haptics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticPolicyTest {

    @Test
    fun `dragging the primary block out through a clear exit is not a blocked move`() {
        // This is the final step: the win buzz follows on release, so no extra "no" tick before it.
        assertFalse(HapticFeedback.shouldBuzzForBlockedDrag(isPrimary = true, clampedReachesExit = true))
    }

    @Test
    fun `a primary block stopped short of the exit still gets the blocked tick`() {
        assertTrue(HapticFeedback.shouldBuzzForBlockedDrag(isPrimary = true, clampedReachesExit = false))
    }

    @Test
    fun `any other block stopped at its limit gets the blocked tick`() {
        assertTrue(HapticFeedback.shouldBuzzForBlockedDrag(isPrimary = false, clampedReachesExit = false))
        // A non-primary block can never be heading out of the exit.
        assertTrue(HapticFeedback.shouldBuzzForBlockedDrag(isPrimary = false, clampedReachesExit = true))
    }
}
