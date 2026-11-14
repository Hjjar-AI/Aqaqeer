package com.aqaqeer.app.security.files

import com.aqaqeer.app.data.supported
import com.aqaqeer.app.fixtures.M3Fixtures as F
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class EnvelopeWriterTest {
    @Test fun productionSnapshotRoundTripsAndUsesFreshNonces() = runBlocking {
        val folder = Files.createTempDirectory("writer").toFile()
        try {
            val wire = Json.decodeFromString<SnapshotWire>(F.snapshot().toString())
            val a = EnvelopeWriter.snapshot(wire, F.key); val b = EnvelopeWriter.snapshot(wire, F.key)
            assertFalse(a.contentEquals(b))
            val authenticated = EnvelopeReader(folder).snapshot(a.inputStream(), F.key)
            val decoded = PayloadReader { Long.MAX_VALUE }.snapshot(authenticated, F.pharmacyId, 1, 10) as SnapshotCandidate.Supported
            assertEquals(wire.supported().confirmedJson(), decoded.confirmedJson())
        } finally { folder.deleteRecursively() }
    }
    @Test fun exportedConnectionRoundTripsWithoutAuthorizationAndClearsPassword() = runBlocking {
        val folder = Files.createTempDirectory("connection-writer").toFile()
        try {
            val wire = Json.decodeFromString<ConnectionWire>(F.connection().toString())
            val password = "١٢٣٤٥٦".toCharArray()
            val a = EnvelopeWriter.connection(wire, password)
            assertTrue(password.all { it == '\u0000' })
            val b = EnvelopeWriter.connection(wire, "123456".toCharArray())
            assertFalse(a.contentEquals(b))
            val payload = EnvelopeReader(folder).password(a.inputStream(), EnvelopeDomain.CONNECTION, "123456".toCharArray())
            PayloadReader { Long.MAX_VALUE }.connection(payload).use { candidate ->
                assertEquals(F.pharmacyId, candidate.pharmacyId)
                val secrets = candidate.copySecrets()
                try { assertArrayEquals(F.key, secrets.snapshotKey) } finally { secrets.snapshotKey.fill(0) }
            }
            val json = kotlinx.serialization.json.Json.encodeToString(ConnectionWire.serializer(), wire)
            assertFalse(json.contains("adminMode")); assertFalse(json.contains("adminPin")); assertFalse(json.contains("history"))
        } finally { folder.deleteRecursively() }
    }
    @Test fun writerRejectsOversizedSnapshotBeforeProducingAnUnreadableFile() {
        val wire = Json.decodeFromString<SnapshotWire>(F.snapshot(F.catalog().copy(medications = F.catalog().medications.map { it.copy(note = "x".repeat(9 * 1024 * 1024)) } )).toString())
        assertThrows(IllegalArgumentException::class.java) { EnvelopeWriter.snapshot(wire, F.key) }
    }
}
