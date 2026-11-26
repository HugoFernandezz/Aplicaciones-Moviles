package com.example.gestorempresa.auth

import com.example.gestorempresa.data.auth.*
import org.junit.Assert.*
import org.junit.Test

class PasswordHasherTest {
    private val hasher = PasswordHasher()
    @Test fun verificaYRechazaClaveIncorrecta() {
        val digest = hasher.crear("mi contraseña muy larga")
        assertTrue(hasher.comprobar("mi contraseña muy larga", digest))
        assertFalse(hasher.comprobar("otra contraseña", digest))
        assertNotEquals("mi contraseña muy larga", digest.hash)
    }
    @Test fun cadaCuentaTieneSaltDistinta() {
        val first = hasher.crear("mi contraseña muy larga")
        val second = hasher.crear("mi contraseña muy larga")
        assertNotEquals(first.salt, second.salt)
        assertNotEquals(first.hash, second.hash)
    }
    @Test fun rechazaDigestCorrupto() { assertFalse(hasher.comprobar("clave", PasswordDigest("?", "?", 600_000))) }
    @Test fun rechazaParametrosManipulados() { assertFalse(hasher.comprobar("clave", PasswordDigest("", "", Int.MAX_VALUE))) }
}
