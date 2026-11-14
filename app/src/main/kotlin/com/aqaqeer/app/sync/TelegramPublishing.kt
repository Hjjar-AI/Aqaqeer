package com.aqaqeer.app.sync

import com.aqaqeer.app.security.files.StrictJson
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

internal data class SetupGroup(val id: Long, val title: String, val pin: String?)
internal data class UploadedDocument(val messageId: Long, val fileId: String)
internal class TelegramFailure(val retryAfter: Int = 0, val code: Int = 0) : Exception("تعذر الاتصال بتيليغرام أو لا تتوفر الصلاحيات المطلوبة")
internal interface TelegramSetupTransport {
    suspend fun validate(token: String, groupId: Long): SetupGroup
    suspend fun discover(token: String, challenge: String): List<SetupGroup>
}
internal interface SnapshotWriteTransport {
    suspend fun inspect(token: String, group: Long): SetupGroup
    suspend fun upload(token: String, group: Long, bytes: ByteArray, caption: String): UploadedDocument
    suspend fun replace(token: String, group: Long, message: Long, file: String, caption: String)
    suspend fun pin(token: String, group: Long, message: Long)
}

/** Official HTTPS host only, bounded metadata, no redirects or platform exception text in UI. */
internal class TelegramPublishing internal constructor(
    private val openConnection: (URL) -> HttpsURLConnection,
) : TelegramSetupTransport, SnapshotWriteTransport {
    constructor() : this({ it.openConnection() as HttpsURLConnection })
    private suspend fun api(token: String, method: String, parameters: Map<String, String> = emptyMap(),
        document: ByteArray? = null): JsonElement = withContext(Dispatchers.IO) {
        require(token.length <= 1024 && token.matches(Regex("[0-9]+:[A-Za-z0-9_-]+"))) { "رمز البوت غير صالح" }
        require(method in setOf("getMe", "getChat", "getChatMember", "getWebhookInfo", "getUpdates", "sendDocument", "editMessageMedia", "pinChatMessage"))
        val connection = openConnection(URL("https://api.telegram.org/bot$token/$method"))
        connection.instanceFollowRedirects = false; connection.connectTimeout = 15000; connection.readTimeout = 15000; connection.useCaches = false
        val started = System.nanoTime()
        fun check() { if (System.nanoTime() - started > 60_000_000_000L) throw TelegramFailure() }
        // Capture the coroutine once; checks also run between bounded I/O chunks.
        val context = currentCoroutineContext()
        try {
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (document == null) {
                val body = parameters.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }.toByteArray()
                try {
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    connection.setFixedLengthStreamingMode(body.size)
                    connection.outputStream.use { it.write(body) }
                } finally { body.fill(0) }
            } else {
                require(document.size <= 10 * 1024 * 1024)
                val boundary = "aq-" + UUID.randomUUID()
                val header = (parameters.entries.joinToString("") { "--$boundary\r\nContent-Disposition: form-data; name=\"${it.key}\"\r\n\r\n${it.value}\r\n" } +
                    "--$boundary\r\nContent-Disposition: form-data; name=\"document\"; filename=\"snapshot.aqdata\"\r\nContent-Type: application/octet-stream\r\n\r\n").toByteArray()
                val tail = "\r\n--$boundary--\r\n".toByteArray()
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                connection.setFixedLengthStreamingMode(header.size + document.size + tail.size)
                connection.outputStream.use { out ->
                    out.write(header)
                    var offset = 0
                    while (offset < document.size) { context.ensureActive(); check(); val length = minOf(8192, document.size - offset); out.write(document, offset, length); offset += length }
                    out.write(tail)
                }
            }
            context.ensureActive(); check()
            val code = connection.responseCode
            if (code in 300..399 || connection.contentEncoding?.let { !it.equals("identity", true) } == true) throw TelegramFailure()
            val stream = if (code == 200) connection.inputStream else connection.errorStream ?: throw TelegramFailure()
            val bytes = stream.use { input ->
                val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) {
                    context.ensureActive(); check(); val n = input.read(buffer); if (n < 0) break
                    if (output.size() + n > 256 * 1024) throw TelegramFailure()
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            }
            try {
                java.io.InputStreamReader(bytes.inputStream(), Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)).use { StrictJson(it) { context.ensureActive(); check() }.validateObject() }
                val envelope = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
                if (envelope["ok"]?.jsonPrimitive?.booleanOrNull != true) {
                    val retry = envelope["parameters"]?.jsonObject?.get("retry_after")?.jsonPrimitive?.intOrNull ?: 0
                    throw TelegramFailure(retry.coerceIn(0, 86400), envelope["error_code"]?.jsonPrimitive?.intOrNull ?: code)
                }
                envelope["result"] ?: throw TelegramFailure()
            } finally { bytes.fill(0) }
        } catch (e: CancellationException) { throw e }
        catch (e: TelegramFailure) { throw e }
        catch (_: IOException) { throw TelegramFailure() }
        catch (_: SecurityException) { throw TelegramFailure() }
        finally { connection.disconnect() }
    }
    // I/O timeouts bound a blocked operation; coroutine checks above bound work between calls.
    private suspend fun bot(token: String): Long {
        val me = api(token, "getMe").jsonObject
        check(me["is_bot"]?.jsonPrimitive?.booleanOrNull == true)
        return me.getValue("id").jsonPrimitive.long
    }
    override suspend fun inspect(token: String, group: Long): SetupGroup {
        require(group < 0)
        val chat = api(token, "getChat", mapOf("chat_id" to group.toString())).jsonObject
        check(chat.getValue("id").jsonPrimitive.long == group)
        check(chat["type"]?.jsonPrimitive?.content in setOf("group", "supergroup")) { "اختر مجموعة خاصة" }
        check(chat["username"] == null && (chat["active_usernames"] as? JsonArray).orEmpty().isEmpty()) { "اختر مجموعة خاصة" }
        // The whole pin identity is compared before any remote mutation; unrelated pins also block bootstrap.
        return SetupGroup(group, chat["title"]?.jsonPrimitive?.content ?: group.toString(), chat["pinned_message"]?.toString())
    }
    override suspend fun validate(token: String, groupId: Long): SetupGroup {
        val id = bot(token); val group = inspect(token, groupId)
        val membership = api(token, "getChatMember", mapOf("chat_id" to groupId.toString(), "user_id" to id.toString())).jsonObject
        check(membership["status"]?.jsonPrimitive?.content == "administrator" && membership["can_pin_messages"]?.jsonPrimitive?.booleanOrNull == true) { "أضف البوت مشرفاً مع صلاحية تثبيت الرسائل" }
        return group
    }
    override suspend fun discover(token: String, challenge: String): List<SetupGroup> {
        require(challenge.matches(Regex("[a-z0-9]{24}")))
        bot(token)
        val hook = api(token, "getWebhookInfo").jsonObject
        check(hook["url"]?.jsonPrimitive?.content.orEmpty().isEmpty()) { "البوت مرتبط بخدمة أخرى عبر webhook؛ استخدم بوتاً مخصصاً أو أدخل المجموعة يدوياً" }
        val updates = api(token, "getUpdates", mapOf("timeout" to "0", "limit" to "100", "allowed_updates" to "[\"message\"]")).jsonArray
        return updates.mapNotNull { item ->
            val message = item.jsonObject["message"] as? JsonObject ?: return@mapNotNull null
            val from = message["from"] as? JsonObject ?: return@mapNotNull null
            if (from["is_bot"]?.jsonPrimitive?.booleanOrNull != false) return@mapNotNull null
            if (message["text"]?.jsonPrimitive?.content != "/aqsetup $challenge") return@mapNotNull null
            val chat = message["chat"] as? JsonObject ?: return@mapNotNull null
            chat["id"]?.jsonPrimitive?.longOrNull?.takeIf { it < 0 }
        }.distinct().map { validate(token, it) }
    }
    override suspend fun upload(token: String, group: Long, bytes: ByteArray, caption: String): UploadedDocument {
        val message = api(token, "sendDocument", mapOf("chat_id" to group.toString(), "caption" to caption, "disable_notification" to "true"), bytes).jsonObject
        check(message["chat"]?.jsonObject?.get("id")?.jsonPrimitive?.long == group)
        return UploadedDocument(message.getValue("message_id").jsonPrimitive.long,
            message.getValue("document").jsonObject.getValue("file_id").jsonPrimitive.content)
    }
    override suspend fun replace(token: String, group: Long, message: Long, file: String, caption: String) {
        val media = buildJsonObject { put("type", "document"); put("media", file); put("caption", caption) }
        api(token, "editMessageMedia", mapOf("chat_id" to group.toString(), "message_id" to message.toString(), "media" to media.toString()))
    }
    override suspend fun pin(token: String, group: Long, message: Long) {
        check(api(token, "pinChatMessage", mapOf("chat_id" to group.toString(), "message_id" to message.toString(), "disable_notification" to "true")).jsonPrimitive.boolean)
    }
}
