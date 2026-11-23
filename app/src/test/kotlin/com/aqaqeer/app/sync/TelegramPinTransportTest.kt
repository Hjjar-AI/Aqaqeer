package com.aqaqeer.app.sync

import com.aqaqeer.app.BuildConfig
import com.aqaqeer.app.fixtures.M3Fixtures as F
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class TelegramPinTransportTest {
    @Test fun targetedUnpinSendsExactLongMessageIdAndNeverUsesAllPinsEndpoint(): Unit = runBlocking {
        Assume.assumeFalse(BuildConfig.IS_DEMO)
        val posted = ByteArrayOutputStream(); val connection = mockk<HttpsURLConnection>(relaxed = true)
        every { connection.outputStream } returns posted
        every { connection.responseCode } returns 200
        every { connection.contentEncoding } returns null
        every { connection.inputStream } returns "{\"ok\":true,\"result\":true}".byteInputStream()
        val addresses = mutableListOf<URL>()
        val transport = TelegramPublishing { addresses += it; connection }
        transport.unpin(F.token, F.groupId, Long.MAX_VALUE)
        assertEquals(1, addresses.size); assertEquals("api.telegram.org", addresses.single().host)
        assertTrue(addresses.single().path.endsWith("/unpinChatMessage"))
        assertEquals("chat_id=${F.groupId}&message_id=${Long.MAX_VALUE}", posted.toString("UTF-8"))
        verify(exactly = 1) { connection.disconnect() }
        verify { connection.instanceFollowRedirects = false }
    }
    @Test fun invalidUnpinMessageIdsNeverOpenConnection(): Unit = runBlocking {
        var opened = 0; val transport = TelegramPublishing { opened++; error("must not open") }
        for (id in listOf(0L, -1L)) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking { transport.unpin(F.token, F.groupId, id) } }
        }
        assertEquals(0, opened)
    }
    @Test fun demoBuildRejectsReadAndWriteHttpBeforeConnectionCreation(): Unit = runBlocking {
        Assume.assumeTrue(BuildConfig.IS_DEMO)
        var opened = 0
        val write = TelegramPublishing { opened++; error("must not open") }
        val read = TelegramSnapshotReadTransport { opened++; error("must not open") }
        assertThrows(IllegalStateException::class.java) { runBlocking { write.pin(F.token, F.groupId, 20) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { write.unpin(F.token, F.groupId, 20) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { read.bot(F.token) } }
        assertEquals(0, opened)
    }
}
