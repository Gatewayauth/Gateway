package io.gateway.authlocal

import de.mkammerer.argon2.Argon2Factory
import io.gateway.domain.auth.PasswordHasher

/**
 * Argon2id password hasher. Parameters follow OWASP guidance (64 MiB memory,
 * 3 iterations). The encoded output embeds the salt and parameters, so verify
 * is self-describing.
 */
class Argon2PasswordHasher(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val memoryKib: Int = DEFAULT_MEMORY_KIB,
    private val parallelism: Int = DEFAULT_PARALLELISM,
) : PasswordHasher {

    private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)

    override fun hash(password: CharArray): String =
        try {
            argon2.hash(iterations, memoryKib, parallelism, password)
        } finally {
            argon2.wipeArray(password)
        }

    override fun verify(password: CharArray, encodedHash: String): Boolean =
        try {
            argon2.verify(encodedHash, password)
        } finally {
            argon2.wipeArray(password)
        }

    override fun needsRehash(encodedHash: String): Boolean {
        // Encoded form: $argon2id$v=19$m=<mem>,t=<iter>,p=<par>$<salt>$<hash>. A hash
        // made with a different algorithm or weaker cost parameters must be upgraded.
        val segments = encodedHash.split('$')
        if (segments.size < 4 || segments[1] != "argon2id") return true
        val params = segments[3].split(',').associate { pair ->
            val kv = pair.split('=')
            kv.getOrNull(0).orEmpty() to kv.getOrNull(1)?.toIntOrNull()
        }
        return params["m"] != memoryKib || params["t"] != iterations || params["p"] != parallelism
    }

    companion object {
        const val DEFAULT_ITERATIONS = 3
        const val DEFAULT_MEMORY_KIB = 65_536
        const val DEFAULT_PARALLELISM = 1
    }
}
