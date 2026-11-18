package space.nbtca.app.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class LogtoAuthRedirectTest {
    @Test
    fun acceptsRegisteredHttpsCallback() {
        assertTrue(LogtoAuth.isRedirect("https://nbtca.space/callback?code=test&state=test"))
        assertTrue(LogtoAuth.isRedirect("https://nbtca.space:443/callback?code=test"))
    }

    @Test
    fun rejectsDifferentCallbackOriginsAndPaths() {
        listOf(
            "http://nbtca.space/callback",
            "https://nbtca.space:444/callback",
            "https://auth.app.nbtca.space/callback",
            "https://nbtca.space/callback/other",
            "https://nbtca.space/%63allback",
            "https://user@nbtca.space/callback"
        ).forEach { assertFalse(it, LogtoAuth.isRedirect(it)) }
    }

    @Test
    fun nonceValidationIgnoresOtherClaimsAndRequiresTheExpectedValue() {
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"nonce":"expected","aud":["app"]}""".toByteArray(Charsets.UTF_8))
        val token = "header.$payload.signature"
        assertTrue(LogtoAuth.matchesNonce(token, "expected"))
        assertFalse(LogtoAuth.matchesNonce(token, "different"))
        assertFalse(LogtoAuth.matchesNonce(null, "expected"))
    }
}
