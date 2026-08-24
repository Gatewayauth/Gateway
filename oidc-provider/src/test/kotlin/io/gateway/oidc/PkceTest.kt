package io.gateway.oidc

import io.gateway.common.Base64Url
import io.gateway.common.Sha256
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PkceTest {

    private val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
    private val challenge = Base64Url.encode(Sha256.hash(verifier))

    @Test
    fun verifiesMatchingS256Challenge() {
        assertTrue(Pkce.verify(verifier, challenge, "S256"))
        assertTrue(Pkce.verify(verifier, challenge, null), "null method defaults to S256")
    }

    @Test
    fun rejectsWrongVerifier() {
        assertFalse(Pkce.verify("wrong-verifier", challenge, "S256"))
    }

    @Test
    fun rejectsPlainMethod() {
        assertFalse(Pkce.verify(verifier, verifier, "plain"))
        assertFalse(Pkce.isSupportedMethod("plain"))
        assertTrue(Pkce.isSupportedMethod("S256"))
        assertTrue(Pkce.isSupportedMethod(null))
    }
}
