package cc.pixp.slipstream.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class FloatplaneCookiesTest {
    private val cookies = FloatplaneCookies("synthetic-session")
    @Test fun mediaCookieIsOnlySentToItsSecureExactHost() {
        assertEquals("synthetic-session", cookies.loadForRequest("https://www.floatplane.com/media/key".toHttpUrl()).single().value)
        for(url in listOf("http://www.floatplane.com/", "https://cdn.floatplane.com/", "https://www.floatplane.com.evil.example/", "https://example.com/")) {
            assertTrue(cookies.loadForRequest(url.toHttpUrl()).isEmpty())
        }
    }
    @Test fun mediaResponsesCannotReplaceVerifiedSession() {
        val url="https://www.floatplane.com/".toHttpUrl()
        cookies.saveFromResponse(url,listOf(okhttp3.Cookie.Builder().name("sails.sid").value("unverified").hostOnlyDomain(url.host).build()))
        assertEquals("synthetic-session",cookies.loadForRequest(url).single().value)
    }
}
