package com.aqaqeer.app.sync

import com.aqaqeer.app.security.files.EnvelopeDomain
import com.aqaqeer.app.security.files.EnvelopeException
import com.aqaqeer.app.security.files.StrictJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.CodingErrorAction
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.CoroutineContext

/** Official-host read methods only. No redirects, custom trust manager, token logs, or write endpoints. */
class TelegramSnapshotReadTransport internal constructor(private val openConnection: (URL) -> HttpsURLConnection) : SnapshotReadTransport {
    constructor() : this({ it.openConnection() as HttpsURLConnection })

    override suspend fun bot(token: String): BotIdentity = onIo { context ->
        val result = api(token, "getMe", emptyMap(), context)
        if (!result.boolean("is_bot")) invalidResponse()
        BotIdentity(result.integer("id").also { if (it <= 0) invalidResponse() })
    }

    override suspend fun pinned(token: String, groupId: Long, bot: BotIdentity): SnapshotDocument = onIo { context ->
        if (groupId >= 0 || bot.id <= 0) invalidResponse()
        val chat = api(token, "getChat", mapOf("chat_id" to groupId.toString()), context)
        val type = chat.text("type")
        if (chat.integer("id") != groupId || type !in setOf("group", "supergroup")) unavailable()
        if (chat["username"] != null || chat["active_usernames"]?.let { it !is JsonArray || it.isNotEmpty() } == true) unavailable()
        val message = chat["pinned_message"] as? JsonObject ?: unavailable()
        val sender = message["from"] as? JsonObject ?: unavailable()
        val messageChat = message["chat"] as? JsonObject ?: unavailable()
        if (sender.integer("id") != bot.id || !sender.boolean("is_bot") ||
            messageChat.integer("id") != groupId || messageChat.text("type") != type ||
            message["sender_chat"] != null || message["forward_origin"] != null) unavailable()
        val document = message["document"] as? JsonObject ?: unavailable()
        val id = message.integer("message_id").also { if (it <= 0) unavailable() }
        val publication = try { SnapshotCaption.decode(message.text("caption")) }
            catch (_: SnapshotReadException) { unavailable() }
        SnapshotDocument(groupId, id, document.text("file_id"), document.text("file_unique_id"), publication)
    }

    override suspend fun document(token: String, document: SnapshotDocument): InputStream {
        val owner = currentCoroutineContext()
        var opened: InputStream? = null
        try {
            return onIo { context ->
                val file = api(token, "getFile", mapOf("file_id" to document.fileId), context)
                if (file.text("file_id") != document.fileId || file.text("file_unique_id") != document.fileUniqueId) invalidResponse()
                val path = file.text("file_path")
                // API paths are opaque until checked; never accept traversal, query, host, or escaped segments.
                if (path.split('/').any { it.isEmpty() || it == "." || it == ".." || !PATH_PART.matches(it) }) invalidResponse()
                val connection = connect("https://api.telegram.org/file/bot${validatedToken(token)}/$path")
                try {
                    connection.requestMethod = "GET"
                    val check = deadline(owner)
                    check(); response(connection); check()
                    HttpDocumentStream(connection, connection.inputStream, check).also { opened = it }
                } catch (error: Throwable) { connection.disconnect(); throw error }
            }
        } catch (error: Throwable) {
            // A withContext result can be discarded by cancellation before the stream reaches its owner.
            runCatching { opened?.close() }
            throw error
        }
    }

    private fun api(token: String, method: String, parameters: Map<String, String>, context: CoroutineContext): JsonObject {
        if (method !in setOf("getMe", "getChat", "getFile") || parameters.size > 1 ||
            parameters.any { (name, value) -> name !in setOf("chat_id", "file_id") || value.length > 4096 }) invalidResponse()
        val connection = connect("https://api.telegram.org/bot${validatedToken(token)}/$method")
        try {
            val check = deadline(context)
            check()
            val body = parameters.entries.joinToString("&") { (name, value) ->
                URLEncoder.encode(name, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
            }.toByteArray(Charsets.UTF_8)
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            } finally { body.fill(0) }
            check(); response(connection); check()
            val bytes = connection.inputStream.use { input -> boundedMetadata(input, check) }
            try {
                val runtime = Runtime.getRuntime()
                val available = (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()) - 16L * 1024 * 1024).coerceAtLeast(0)
                if (bytes.size * 64L + 65_536 > available / 4 * 3) throw SnapshotReadException(SnapshotReadError.RESOURCE_LIMIT)
                InputStreamReader(bytes.inputStream(), Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)).use {
                    StrictJson(it, check).validateObject()
                }
                val envelope = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject ?: invalidResponse()
                check()
                if (!envelope.boolean("ok")) {
                    when (envelope.integer("error_code")) {
                        401L -> throw SnapshotReadException(SnapshotReadError.INVALID_CREDENTIALS)
                        403L -> throw SnapshotReadException(SnapshotReadError.ACCESS_DENIED)
                        429L -> throw SnapshotReadException(SnapshotReadError.RATE_LIMITED)
                        else -> throw SnapshotReadException(SnapshotReadError.INVALID_RESPONSE)
                    }
                }
                return envelope["result"] as? JsonObject ?: invalidResponse()
            } finally { bytes.fill(0) }
        } finally { connection.disconnect() }
    }

    private fun boundedMetadata(input: InputStream, check: () -> Unit): ByteArray {
        val output = object : ByteArrayOutputStream(METADATA_LIMIT + 1) { fun clear() { buf.fill(0) } }
        try {
            val buffer = ByteArray(8192)
            while (true) {
                check()
                val count = input.read(buffer, 0, minOf(buffer.size, METADATA_LIMIT + 1 - output.size()))
                if (count < 0) break
                if (count == 0) {
                    val byte = input.read()
                    if (byte < 0) break
                    output.write(byte)
                } else output.write(buffer, 0, count)
                if (output.size() > METADATA_LIMIT) throw SnapshotReadException(SnapshotReadError.RESPONSE_TOO_LARGE)
            }
            check()
            return output.toByteArray()
        } finally { output.clear(); output.close() }
    }

    private suspend fun <T> onIo(block: (CoroutineContext) -> T): T {
        try { return withContext(Dispatchers.IO) { block(currentCoroutineContext()) } }
        catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: SnapshotReadException) { throw error }
        catch (_: java.nio.charset.CharacterCodingException) { invalidResponse() }
        catch (_: IOException) { throw SnapshotReadException(SnapshotReadError.NETWORK) }
        catch (_: SecurityException) { throw SnapshotReadException(SnapshotReadError.NETWORK) }
        catch (_: SerializationException) { invalidResponse() }
        catch (_: EnvelopeException) { invalidResponse() }
        catch (_: IllegalArgumentException) { invalidResponse() }
    }

    private class HttpDocumentStream(private val connection: HttpsURLConnection,
        private val input: InputStream, private val check: () -> Unit) : InputStream() {
        private var count = 0L
        private var closed = false
        override fun read(): Int = reading {
            input.read().also { if (it >= 0) counted(1) }
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (offset < 0 || length < 0 || offset > buffer.size - length) throw IndexOutOfBoundsException()
            if (length == 0) return 0
            return reading {
                input.read(buffer, offset, minOf(length.toLong(), EnvelopeDomain.SNAPSHOT.byteLimit - count + 1).toInt())
                    .also { if (it > 0) counted(it) }
            }
        }
        private fun counted(length: Int) {
            count += length
            if (count > EnvelopeDomain.SNAPSHOT.byteLimit) throw SnapshotReadException(SnapshotReadError.RESPONSE_TOO_LARGE)
        }
        private fun <T> reading(block: () -> T): T {
            if (closed) throw SnapshotReadException(SnapshotReadError.NETWORK)
            try {
                if (count > EnvelopeDomain.SNAPSHOT.byteLimit) throw SnapshotReadException(SnapshotReadError.RESPONSE_TOO_LARGE)
                check()
                return block().also { check() }
            } catch (error: Throwable) {
                runCatching { close() }
                if (error is IOException || error is SecurityException) throw SnapshotReadException(SnapshotReadError.NETWORK)
                throw error
            }
        }
        override fun close() {
            if (closed) return
            closed = true
            try { input.close() }
            catch (_: IOException) { throw SnapshotReadException(SnapshotReadError.NETWORK) }
            catch (_: SecurityException) { throw SnapshotReadException(SnapshotReadError.NETWORK) }
            finally { connection.disconnect() }
        }
    }

    private fun connect(address: String): HttpsURLConnection = openConnection(URL(address)).apply {
        instanceFollowRedirects = false
        connectTimeout = 15_000
        readTimeout = 15_000
        useCaches = false
        setRequestProperty("Accept-Encoding", "identity")
    }

    companion object {
        private const val METADATA_LIMIT = 256 * 1024
        private val TOKEN = Regex("[0-9]+:[A-Za-z0-9_-]+")
        private val PATH_PART = Regex("[A-Za-z0-9_.-]+")
        private fun validatedToken(token: String): String {
            if (token.length > 1024 || !TOKEN.matches(token)) throw SnapshotReadException(SnapshotReadError.INVALID_CREDENTIALS)
            return token
        }
        private fun deadline(context: CoroutineContext): () -> Unit {
            val started = System.nanoTime()
            return {
                context.ensureActive()
                if (System.nanoTime() - started > 60_000_000_000L) throw SnapshotReadException(SnapshotReadError.NETWORK)
            }
        }
        private fun response(connection: HttpsURLConnection) {
            when (connection.responseCode) {
                200 -> Unit
                401 -> throw SnapshotReadException(SnapshotReadError.INVALID_CREDENTIALS)
                403 -> throw SnapshotReadException(SnapshotReadError.ACCESS_DENIED)
                429 -> throw SnapshotReadException(SnapshotReadError.RATE_LIMITED)
                else -> throw SnapshotReadException(SnapshotReadError.NETWORK)
            }
            val encoding = connection.contentEncoding
            if (encoding != null && !encoding.equals("identity", ignoreCase = true)) invalidResponse()
        }
        private fun JsonObject.text(name: String): String {
            val value = this[name] as? JsonPrimitive ?: invalidResponse()
            if (!value.isString || value.content.isBlank() || value.content.length > 4096) invalidResponse()
            return value.content
        }
        private fun JsonObject.integer(name: String): Long {
            val value = this[name] as? JsonPrimitive ?: invalidResponse()
            if (value.isString) invalidResponse()
            return value.content.toLongOrNull() ?: invalidResponse()
        }
        private fun JsonObject.boolean(name: String): Boolean {
            val value = this[name] as? JsonPrimitive ?: invalidResponse()
            if (value.isString) invalidResponse()
            return value.booleanOrNull ?: invalidResponse()
        }
        private fun unavailable(): Nothing = throw SnapshotReadException(SnapshotReadError.SNAPSHOT_UNAVAILABLE)
    }
}
