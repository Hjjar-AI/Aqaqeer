package com.aqaqeer.app.security

import kotlinx.serialization.json.*
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class Pbkdf2Sha256Test {
    @Test fun platformAndLegacyMatchIndependentWireKeys() {
        val fixture = requireNotNull(javaClass.getResourceAsStream("/v1-vectors.json"))
        val vectors = fixture.bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject.getValue("vectors").jsonArray }
        val passwordVectors = vectors.map { it.jsonObject }.filter { it.getValue("publicTestPassword").jsonPrimitive.contentOrNull != null }
        assertEquals(2, passwordVectors.size)
        for (vector in passwordVectors) {
            val envelope = vector.getValue("envelope").jsonObject
            val password = vector.getValue("publicTestPassword").jsonPrimitive.content.toCharArray()
            val salt = Base64.getDecoder().decode(envelope.getValue("salt").jsonPrimitive.content)
            val expected = vector.getValue("keyHex").jsonPrimitive.content.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val iterations = envelope.getValue("iter").jsonPrimitive.int
            val snapshot = password.copyOf()
            assertArrayEquals(expected, Pbkdf2Sha256.platform(password, salt, iterations))
            assertArrayEquals(expected, Pbkdf2Sha256.legacy(password, salt, iterations))
            assertArrayEquals(snapshot, password)
        }
    }
}
