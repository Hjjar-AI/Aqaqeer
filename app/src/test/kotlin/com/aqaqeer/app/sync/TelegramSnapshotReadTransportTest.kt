package com.aqaqeer.app.sync

import com.aqaqeer.app.fixtures.M3Fixtures as F
import com.aqaqeer.app.security.files.SnapshotPublication
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.net.URL
import java.security.Principal
import java.security.cert.Certificate
import java.util.ArrayDeque
import javax.net.ssl.HttpsURLConnection

class TelegramSnapshotReadTransportTest {
    private val doc get() = SnapshotDocument(F.groupId,12,"public-file","public-unique",SnapshotPublication(1,F.publishId,F.time))
    private class Response(val bytes: ByteArray, val status: Int = 200, val encoding: String? = null, val failure: Boolean = false,
        val stream: InputStream? = null)
    private class Connection(url: URL, private val response: Response) : HttpsURLConnection(url) {
        var disconnected = false; var inputClosed = false
        val posted = ByteArrayOutputStream()
        override fun connect() { }
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getCipherSuite() = "TEST"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getPeerPrincipal(): Principal? = null
        override fun getLocalPrincipal(): Principal? = null
        override fun getResponseCode() = response.status
        override fun getContentEncoding() = response.encoding
        override fun getOutputStream(): OutputStream = posted
        override fun getInputStream(): InputStream {
            if (response.failure) throw IOException("https://secret-token-in-error")
            response.stream?.let { return it }
            return object : ByteArrayInputStream(response.bytes) {
                override fun close() { inputClosed = true; super.close() }
            }
        }
    }
    private class Harness(vararg responses: Response) {
        val queue = ArrayDeque(responses.toList()); val connections = mutableListOf<Connection>()
        val transport = TelegramSnapshotReadTransport { url ->
            assertEquals("https",url.protocol); assertEquals("api.telegram.org",url.host)
            Connection(url,queue.removeFirst()).also { connections += it }
        }
    }
    private fun response(result: JsonObject) = Response(buildJsonObject { put("ok",true); put("result",result) }.toString().toByteArray())
    private fun bot() = buildJsonObject { put("id",123456); put("is_bot",true) }
    private fun chat(): JsonObject = buildJsonObject {
        put("id",F.groupId); put("type","supergroup")
        put("pinned_message", buildJsonObject {
            put("message_id",12); put("from",bot())
            put("chat", buildJsonObject { put("id",F.groupId); put("type","supergroup") })
            put("document",buildJsonObject { put("file_id","public-file"); put("file_unique_id","public-unique") })
            put("caption",SnapshotCaption.encode(doc.publication))
        })
    }
    private fun file(path: String = "documents/file_0.json") = buildJsonObject {
        put("file_id","public-file"); put("file_unique_id","public-unique"); put("file_path",path)
    }
    private suspend fun rejects(code: SnapshotReadError, action: suspend () -> Unit) {
        try { action(); fail("Expected $code") } catch (e: SnapshotReadException) { assertEquals(code,e.error); assertNull(e.cause); assertEquals(code.name,e.message) }
    }
    @Test fun realAdapterParsesBotAndPrivateGroupAndSetsSafeConnectionOptions() = runBlocking {
        val h = Harness(response(bot()),response(chat()))
        val identity = h.transport.bot(F.token)
        assertEquals(doc,h.transport.pinned(F.token,F.groupId,identity))
        for (connection in h.connections) {
            assertFalse(connection.instanceFollowRedirects); assertFalse(connection.useCaches)
            assertEquals(15000,connection.connectTimeout); assertEquals(15000,connection.readTimeout)
            assertEquals("identity",connection.getRequestProperty("Accept-Encoding")); assertEquals("POST",connection.requestMethod)
            assertTrue(connection.disconnected); assertTrue(connection.inputClosed)
        }
        assertEquals("chat_id=-1001234567890",h.connections.last().posted.toString("UTF-8"))
    }
    @Test fun downloadRemainsReadableAfterIoDispatcherReturnsAndDisconnectsOnClose() = runBlocking {
        val h = Harness(response(file()),Response("encrypted bytes".toByteArray()))
        val input = h.transport.document(F.token,doc)
        assertFalse(h.connections.last().disconnected)
        assertEquals("encrypted bytes",withContext(Dispatchers.IO) { input.readBytes().toString(Charsets.UTF_8) })
        input.close(); assertTrue(h.connections.last().disconnected)
    }
    @Test fun downloadCapStopsAtOneExcessByteAndDisconnects() = runBlocking {
        val h = Harness(response(file()),Response(ByteArray(10*1024*1024+2)))
        val input = h.transport.document(F.token,doc)
        rejects(SnapshotReadError.RESPONSE_TOO_LARGE) { input.use { it.copyTo(ByteArrayOutputStream()) } }
        assertTrue(h.connections.last().disconnected)
    }
    @Test fun metadataCapAndStrictJsonRejectOversizeDuplicateKeysAndMalformedUtf8() = runBlocking {
        for (bytes in listOf(ByteArray(256*1024+1){32}, "{\"ok\":true,\"\\u006fk\":false,\"result\":{}}".toByteArray(), byteArrayOf(0xc3.toByte(),0x28))) {
            val h = Harness(Response(bytes))
            rejects(if (bytes.size > 256*1024) SnapshotReadError.RESPONSE_TOO_LARGE else SnapshotReadError.INVALID_RESPONSE) { h.transport.bot(F.token) }
            assertTrue(h.connections.single().disconnected)
        }
    }
    @Test fun exactMetadataCapIsAcceptedWithUnknownReportedLength() = runBlocking {
        val prefix = response(bot()).bytes
        val bytes = ByteArray(256*1024) { if (it < prefix.size) prefix[it] else 32 }
        assertEquals(BotIdentity(123456),Harness(Response(bytes)).transport.bot(F.token))
    }
    @Test fun cancellationOnReturnedStreamClosesConnection() = runBlocking {
        val h = Harness(response(file()),Response(byteArrayOf(1)))
        val operation = launch {
            val input = h.transport.document(F.token,doc)
            currentCoroutineContext().cancel()
            try { input.read(); fail("Cancelled stream read accepted") } catch (_: CancellationException) { }
        }
        operation.join(); assertTrue(h.connections.last().disconnected)
    }
    @Test fun returnedStreamReadErrorsAreSanitizedAndDisconnected() = runBlocking {
        var closed = false
        val stream = object : InputStream() {
            override fun read(): Int = throw IOException("secret-token-in-error")
            override fun close() { closed = true }
        }
        val h = Harness(response(file()),Response(byteArrayOf(),stream=stream))
        val input = h.transport.document(F.token,doc)
        rejects(SnapshotReadError.NETWORK) { input.read() }
        assertTrue(closed); assertTrue(h.connections.last().disconnected)
    }
    @Test fun quotedNumericAndBooleanFieldsAreRejectedButUnknownApiFieldsAreAllowed() = runBlocking {
        for (bad in listOf(JsonObject(bot() + ("id" to JsonPrimitive("123456"))),JsonObject(bot() + ("is_bot" to JsonPrimitive("true"))))) {
            rejects(SnapshotReadError.INVALID_RESPONSE) { Harness(response(bad)).transport.bot(F.token) }
        }
        assertEquals(BotIdentity(123456),Harness(response(JsonObject(bot() + ("future_field" to JsonPrimitive(true))))).transport.bot(F.token))
    }
    @Test fun httpErrorsAndApiErrorsAreSanitizedWithoutForwardingDescriptions() = runBlocking {
        for ((status,code) in listOf(401 to SnapshotReadError.INVALID_CREDENTIALS,403 to SnapshotReadError.ACCESS_DENIED,429 to SnapshotReadError.RATE_LIMITED,302 to SnapshotReadError.NETWORK)) {
            val h = Harness(Response("secret response".toByteArray(),status))
            rejects(code) { h.transport.bot(F.token) }; assertTrue(h.connections.single().disconnected)
        }
        val error = "{\"ok\":false,\"error_code\":429,\"description\":\"secret\"}"
        rejects(SnapshotReadError.RATE_LIMITED) { Harness(Response(error.toByteArray())).transport.bot(F.token) }
        rejects(SnapshotReadError.NETWORK) { Harness(Response(byteArrayOf(),failure=true)).transport.bot(F.token) }
    }
    @Test fun compressedMetadataAndPathInjectionAreRejectedBeforeDownload() = runBlocking {
        rejects(SnapshotReadError.INVALID_RESPONSE) { Harness(Response(response(bot()).bytes,encoding="gzip")).transport.bot(F.token) }
        for (path in listOf("../secret","/absolute","a//b","a/%2e%2e/b","https://elsewhere/file","file?token=secret")) {
            val h = Harness(response(file(path)))
            rejects(SnapshotReadError.INVALID_RESPONSE) { h.transport.document(F.token,doc) }
            assertEquals(1,h.connections.size)
        }
    }
    @Test fun invalidTokenCannotOpenAnyConnection() = runBlocking {
        val h = Harness()
        for (token in listOf("bad", "123:token/../other", "123:token?x=1")) rejects(SnapshotReadError.INVALID_CREDENTIALS) { h.transport.bot(token) }
        assertTrue(h.connections.isEmpty())
    }
    @Test fun missingPublicWrongSenderForwardedAndNonDocumentPinsAreUnavailable() = runBlocking {
        val valid = chat(); val pin = valid["pinned_message"]!!.jsonObject
        val badPins = listOf(JsonObject(valid - "pinned_message"),JsonObject(valid + ("username" to JsonPrimitive("public_group"))),
            JsonObject(valid + ("pinned_message" to JsonObject(pin - "document"))),
            JsonObject(valid + ("pinned_message" to JsonObject(pin + ("sender_chat" to JsonObject(emptyMap()))))),
            JsonObject(valid + ("pinned_message" to JsonObject(pin + ("forward_origin" to JsonObject(emptyMap()))))),
            JsonObject(valid + ("pinned_message" to JsonObject(pin + ("from" to JsonObject(bot() + ("id" to JsonPrimitive(999))))))))
        for (value in badPins) rejects(SnapshotReadError.SNAPSHOT_UNAVAILABLE) { Harness(response(value)).transport.pinned(F.token,F.groupId,BotIdentity(123456)) }
    }
}
