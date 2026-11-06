package com.fitnesslink.fit.profile

import com.fitnesslink.fit.network.ApiError
import com.fitnesslink.fit.ui.navigation.DeepLinkResolver
import com.fitnesslink.fit.ui.profile.TvPairingCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectTvTests {
    @Test fun sanitizeUppercasesAndDropsSeparators() {
        assertEquals(TvPairingCode.Sanitized("ABC234", false), TvPairingCode.sanitize("abc-2 34"))
    }

    @Test fun sanitizeRejectsLookAlikes() {
        val result = TvPairingCode.sanitize("A0O1I2")
        assertEquals("A2", result.code)
        assertTrue(result.rejected)
    }

    @Test fun sanitizeCapsAtSixCharacters() {
        assertEquals("ABCDEF", TvPairingCode.sanitize("ABCDEFGH").code)
    }

    @Test fun validMeansSixAlphabetCharacters() {
        assertTrue(TvPairingCode.isValid("ABC234"))
        assertFalse(TvPairingCode.isValid("ABC23"))
        assertFalse(TvPairingCode.isValid("ABC230"))
    }

    @Test fun qrLinkOpensConnectTvWithTheCode() {
        assertEquals("connectTV?code=ABC234", DeepLinkResolver.resolve("fitnesslink://tv-pair?code=abc234"))
    }

    @Test fun qrLinkWithoutACodeOpensTheEmptyScreen() {
        assertEquals("connectTV", DeepLinkResolver.resolve("fitnesslink://tv-pair"))
    }

    @Test fun expiredCodeGetsItsOwnMessage() {
        assertTrue(TvPairingCode.errorMessage(ApiError.ServerError(410, "")).contains("expired"))
        assertTrue(TvPairingCode.errorMessage(ApiError.NotFound).contains("couldn't find"))
    }
}
