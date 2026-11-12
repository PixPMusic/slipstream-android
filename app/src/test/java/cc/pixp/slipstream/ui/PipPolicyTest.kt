package cc.pixp.slipstream.ui

import androidx.media3.common.Player
import org.junit.Assert.*
import org.junit.Test

class PipPolicyTest {
    @Test fun onlyActiveVideoEntersPip() {
        assertTrue(shouldAutoPip(true,true,Player.STATE_READY))
        assertTrue(shouldAutoPip(true,true,Player.STATE_BUFFERING))
        assertFalse(shouldAutoPip(false,true,Player.STATE_READY))
        assertFalse(shouldAutoPip(true,false,Player.STATE_READY))
        assertFalse(shouldAutoPip(true,true,Player.STATE_IDLE))
        assertFalse(shouldAutoPip(true,true,Player.STATE_ENDED))
    }
}
