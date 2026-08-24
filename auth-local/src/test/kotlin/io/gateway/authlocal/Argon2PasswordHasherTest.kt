package io.gateway.authlocal

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Argon2PasswordHasherTest {

    // Small parameters keep the test fast; the values still exercise the parser.
    private val hasher = Argon2PasswordHasher(iterations = 1, memoryKib = 8_192, parallelism = 1)

    @Test
    fun hashVerifiesAndDoesNotNeedRehashAtSameParams() {
        val encoded = hasher.hash("correcthorsebattery".toCharArray())
        assertTrue(hasher.verify("correcthorsebattery".toCharArray(), encoded))
        assertFalse(hasher.needsRehash(encoded))
    }

    @Test
    fun needsRehashWhenCostParametersDiffer() {
        val weak = Argon2PasswordHasher(iterations = 1, memoryKib = 8_192, parallelism = 1)
            .hash("correcthorsebattery".toCharArray())
        val stronger = Argon2PasswordHasher(iterations = 3, memoryKib = 65_536, parallelism = 1)
        assertTrue(stronger.needsRehash(weak))
    }

    @Test
    fun needsRehashForNonArgon2idOrGarbage() {
        assertTrue(hasher.needsRehash("\$argon2i\$v=19\$m=8192,t=1,p=1\$c2FsdA\$aGFzaA"))
        assertTrue(hasher.needsRehash("not-a-hash"))
    }
}
