package io.gateway.mfa

import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.code.DefaultCodeVerifier
import dev.samstevens.totp.secret.DefaultSecretGenerator
import dev.samstevens.totp.time.SystemTimeProvider
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * TOTP (RFC 6238) secret generation, provisioning-URI building, and code
 * verification. A ±1 time-step discrepancy is allowed to tolerate clock skew.
 */
class TotpService(private val issuer: String) {

    private val secretGenerator = DefaultSecretGenerator()
    private val verifier = DefaultCodeVerifier(DefaultCodeGenerator(), SystemTimeProvider()).apply {
        setAllowedTimePeriodDiscrepancy(1)
    }

    fun generateSecret(): String = secretGenerator.generate()

    fun verify(secret: String, code: String): Boolean = verifier.isValidCode(secret, code)

    /** Builds an `otpauth://` URI for authenticator apps / QR codes. */
    fun provisioningUri(secret: String, account: String): String {
        val label = encode("$issuer:$account")
        val params = "secret=$secret&issuer=${encode(issuer)}&algorithm=SHA1&digits=6&period=30"
        return "otpauth://totp/$label?$params"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
