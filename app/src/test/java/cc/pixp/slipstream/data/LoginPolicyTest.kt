package cc.pixp.slipstream.data

import org.junit.Assert.*
import org.junit.Test

class LoginPolicyTest {
    @Test fun onlyHttpsSignInHostsCanNavigateTheMainFrame() {
        assertTrue(LoginPolicy.allows("https://www.floatplane.com/login", true))
        assertTrue(LoginPolicy.allows("https://discord.com/oauth2/authorize", true))
        assertFalse(LoginPolicy.allows("https://floatplane.com.evil.example/login", true))
        assertFalse(LoginPolicy.allows("https://evilfloatplane.com/login", true))
        assertFalse(LoginPolicy.allows("http://www.floatplane.com/login", true))
        assertFalse(LoginPolicy.allows("javascript:alert(1)", true))
        assertFalse(LoginPolicy.allows("file:///sdcard/example.html", false))
    }
    @Test fun verificationFramesCanLoadWithoutAllowingMainFrameEscape() {
        assertTrue(LoginPolicy.allows("https://challenges.cloudflare.com/turnstile", false))
        assertTrue(LoginPolicy.allows("about:blank", false))
        assertFalse(LoginPolicy.allows("about:blank", true))
    }
    @Test fun cookieParserKeepsEncodedValueAndIgnoresOtherCookies() {
        assertEquals("s%3Aabc.signature=", LoginPolicy.sessionCookie("other=abc; sails.sid=s%3Aabc.signature=; setting=1"))
        assertNull(LoginPolicy.sessionCookie("other=abc"))
        assertNull(LoginPolicy.sessionCookie("sails.sid="))
        assertNull(LoginPolicy.sessionCookie("sails.sid=bad\r\nInjected: value"))
    }
}
