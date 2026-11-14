package com.aqaqeer.app.security.files

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.*
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class EnvelopeReaderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val reader get() = EnvelopeReader(temporary.root) { 2L * 1024 * 1024 * 1024 }
    private val vectors get() = Json.parseToJsonElement(javaClass.getResource("/v1-vectors.json")!!.readText()).jsonObject["vectors"]!!.jsonArray
    private val key = ByteArray(32) { it.toByte() }
    private fun vector(domain: String) = vectors.map { it.jsonObject }.single { it["domain"]!!.jsonPrimitive.content == domain }
    private fun encoded(domain: String) = vector(domain)["envelope"]!!.toString().toByteArray()
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun encodePlain(plain: ByteArray): ByteArray {
        val nonce = ByteArray(12) { (64 + it).toByte() } // Public test-only nonce/key.
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD("aqsnapshot:v1".toByteArray() + nonce)
        val encrypted = cipher.doFinal(plain)
        return buildJsonObject {
            put("v", 1); put("nonce", Base64.getEncoder().encodeToString(nonce))
            put("ct", Base64.getEncoder().encodeToString(encrypted.dropLast(16).toByteArray()))
            put("tag", Base64.getEncoder().encodeToString(encrypted.takeLast(16).toByteArray()))
        }.toString().toByteArray()
    }
    private fun fails(code: EnvelopeError, action: suspend () -> Unit) = runBlocking {
        try { action(); fail("Expected $code") } catch (e: EnvelopeException) { assertEquals(code, e.error) }
        assertTrue(temporary.root.walk().none { it.name.endsWith(".stage") })
    }

    @Test fun fixedConnectionBackupAndSnapshotVectorsMatchAndPreserveCallerCredentials() = runBlocking {
        for (v in vectors.map { it.jsonObject }) {
            val domain = EnvelopeDomain.entries.single { it.marker == v["domain"]!!.jsonPrimitive.content }
            val bytes = v["envelope"]!!.toString().toByteArray()
            val originalKey = hex(v["keyHex"]!!.jsonPrimitive.content)
            val password = v["publicTestPassword"]?.jsonPrimitive?.contentOrNull?.toCharArray()
            val expectedPassword = password?.copyOf()
            val payload = if (password == null) reader.snapshot(bytes.inputStream(), originalKey)
                else reader.password(bytes.inputStream(), domain, password)
            payload.use { assertEquals(v["plaintextUtf8"]!!.jsonPrimitive.content, it.withUtf8Reader { r -> r.readText() }) }
            assertArrayEquals(hex(v["keyHex"]!!.jsonPrimitive.content), originalKey)
            if (password != null) assertArrayEquals(expectedPassword, password)
        }
        assertTrue(temporary.root.walk().none { it.name.endsWith(".stage") })
    }
    @Test fun connectionNormalizesArabicAndPersianDigits() = runBlocking {
        for (password in listOf("١٢٣٤٥٦", "۱۲۳۴۵۶")) {
            reader.password(encoded("aqz:v1").inputStream(), EnvelopeDomain.CONNECTION, password.toCharArray()).close()
        }
    }
    @Test fun wrongPasswordAndChangedTagShareAuthenticationFailure() {
        fails(EnvelopeError.AUTHENTICATION) { reader.password(encoded("aqz:v1").inputStream(), EnvelopeDomain.CONNECTION, "654321".toCharArray()) }
        val v = vector("aqsnapshot:v1")["envelope"]!!.jsonObject
        val changed = JsonObject(v + ("tag" to JsonPrimitive("AAAAAAAAAAAAAAAAAAAAAA==")))
        fails(EnvelopeError.AUTHENTICATION) { reader.snapshot(changed.toString().byteInputStream(), key) }
    }
    @Test fun backupPassphraseIsNotTrimmedOrUnicodeNormalized() {
        fails(EnvelopeError.AUTHENTICATION) { reader.password(encoded("aqbak:v1").inputStream(), EnvelopeDomain.BACKUP, "اختبار Passé".toCharArray()) }
        fails(EnvelopeError.AUTHENTICATION) { reader.password(encoded("aqbak:v1").inputStream(), EnvelopeDomain.BACKUP, "  اختبار Passe\u0301  ".toCharArray()) }
    }
    @Test fun connectionCannotBeReadAsBackupEvenWithSamePasswordAndDerivedKey() {
        fails(EnvelopeError.AUTHENTICATION) { reader.password(encoded("aqbak:v1").inputStream(), EnvelopeDomain.CONNECTION,
            "12345678".toCharArray()) }
        // Eight digits pass either password rule; changing only the authenticated prefix must fail.
        val salt = ByteArray(16)
        val spec = javax.crypto.spec.PBEKeySpec("12345678".toCharArray(), salt, 200_000, 256)
        val derived = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        val nonce = ByteArray(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD("aqz:v1".toByteArray() + salt + nonce)
        val result = cipher.doFinal("{}".toByteArray())
        val envelope = buildJsonObject {
            put("v",1); put("kdf","PBKDF2-HMAC-SHA256"); put("iter",200_000)
            put("salt",Base64.getEncoder().encodeToString(salt)); put("nonce",Base64.getEncoder().encodeToString(nonce))
            put("ct",Base64.getEncoder().encodeToString(result.dropLast(16).toByteArray()))
            put("tag",Base64.getEncoder().encodeToString(result.takeLast(16).toByteArray()))
        }
        fails(EnvelopeError.AUTHENTICATION) { reader.password(envelope.toString().byteInputStream(), EnvelopeDomain.BACKUP, "12345678".toCharArray()) }
    }
    @Test fun duplicateEnvelopeKeysIncludingEscapedAliasesAreRejected() {
        val input = String(encoded("aqsnapshot:v1"))
        for (prefix in listOf("\"v\":1,", "\"\\u0076\":1,")) {
            fails(EnvelopeError.INVALID_FORMAT) { reader.snapshot(("{" + prefix + input.drop(1)).byteInputStream(), key) }
        }
    }
    @Test fun nestedPayloadDuplicateKeysAreRejectedAfterAuthentication() {
        for (json in listOf("{\"items\":[{\"a\":1,\"\\u0061\":2}]}", "{\"a\":1,\"a\":2}")) {
            fails(EnvelopeError.INVALID_FORMAT) { reader.snapshot(encodePlain(json.toByteArray()).inputStream(), key) }
        }
    }
    @Test fun distinctObjectsMayReuseKeysAndAllJsonValueKindsAreSupported() = runBlocking {
        val json = "{\"items\":[{\"a\":1},{\"a\":2}],\"v\":[true,false,null,-1.25e+2,\"\\ud83d\\udc8a\"]}"
        reader.snapshot(encodePlain(json.toByteArray()).inputStream(), key).use {
            assertEquals(json,it.withUtf8Reader { r -> r.readText() })
        }
    }
    @Test fun malformedPayloadSyntaxSurrogatesAndUtf8AreRejected() {
        for (json in listOf("[]", "{}{}", "{\"a\":01}", "{\"a\":true,}", "{\"a\":\"\\ud800\"}", "{\"a\":NaN}", "{\"a\":\"\\u٠٠٤١\"}")) {
            fails(EnvelopeError.INVALID_FORMAT) { reader.snapshot(encodePlain(json.toByteArray()).inputStream(), key) }
        }
        fails(EnvelopeError.INVALID_FORMAT) { reader.snapshot(encodePlain(byteArrayOf(123,34,97,34,58,34,0xc3.toByte(),0x28,34,125)).inputStream(), key) }
    }
    @Test fun jsonDepthLimitIncludesRootObject() = runBlocking {
        val valid = "{\"a\":" + "[".repeat(63) + "0" + "]".repeat(63) + "}"
        reader.snapshot(encodePlain(valid.toByteArray()).inputStream(), key).close()
        val deep = "{\"a\":" + "[".repeat(64) + "0" + "]".repeat(64) + "}"
        fails(EnvelopeError.INVALID_FORMAT) { reader.snapshot(encodePlain(deep.toByteArray()).inputStream(), key) }
    }
    @Test fun missingExtraWrongTypedAndTruncatedFieldsAreRejected() {
        val v = vector("aqsnapshot:v1")["envelope"]!!.jsonObject
        val invalid = listOf(JsonObject(v - "tag"), JsonObject(v + ("extra" to JsonPrimitive(1))),
            JsonObject(v + ("v" to JsonPrimitive("1"))), JsonObject(v + ("v" to JsonPrimitive(1.0))),
            JsonObject(v + ("nonce" to JsonPrimitive("AA=="))), JsonObject(v + ("salt" to JsonPrimitive("AA=="))))
        for (item in invalid) fails(EnvelopeError.INVALID_FORMAT) { reader.snapshot(item.toString().byteInputStream(),key) }
        fails(EnvelopeError.INVALID_FORMAT) { reader.snapshot(encoded("aqsnapshot:v1").dropLast(2).toByteArray().inputStream(), key) }
    }
    @Test fun unsupportedVersionKdfOrIterationCountNeverUsesUnboundedWork() {
        val v = vector("aqz:v1")["envelope"]!!.jsonObject
        for ((field,value) in listOf("v" to JsonPrimitive(2), "kdf" to JsonPrimitive("OTHER"), "iter" to JsonPrimitive(Long.MAX_VALUE))) {
            fails(EnvelopeError.UNSUPPORTED_FORMAT) { reader.password(JsonObject(v + (field to value)).toString().byteInputStream(), EnvelopeDomain.CONNECTION, "123456".toCharArray()) }
        }
    }
    @Test fun base64RejectsMissingPaddingWhitespaceUrlSafeAndNonzeroPadBits() {
        val v = vector("aqsnapshot:v1")["envelope"]!!.jsonObject
        for (tag in listOf("M2XjA/1d/TMAzSAyUuW/WQ", "M2XjA/1d/TMAzSAyUuW/WQ== ", "M2XjA_1d_TMAzSAyUuW_WQ==", "M2XjA/1d/TMAzSAyUuW/WR==", "====")) {
            fails(EnvelopeError.INVALID_FORMAT) { reader.snapshot(JsonObject(v + ("tag" to JsonPrimitive(tag))).toString().byteInputStream(),key) }
        }
    }
    @Test fun malformedEnvelopeUtf8AndTrailingContentAreRejected() {
        fails(EnvelopeError.INVALID_FORMAT) { reader.snapshot(byteArrayOf(0xc3.toByte(),0x28).inputStream(),key) }
        fails(EnvelopeError.INVALID_FORMAT) { reader.snapshot((String(encoded("aqsnapshot:v1")) + "x").byteInputStream(),key) }
    }
    private class PaddedInput(private val prefix: ByteArray, private val size: Long) : InputStream() {
        var count = 0L; var closed = false
        override fun available() = 0 // No reliable length is available.
        override fun read(): Int = if (count >= size) -1 else {
            val byte = if (count < prefix.size) prefix[count.toInt()].toInt() and 255 else 32
            count++; byte
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (count >= size) return -1
            val amount = minOf(length.toLong(),size-count).toInt()
            repeat(amount) { buffer[offset+it] = read().toByte() }
            return amount
        }
        override fun close() { closed = true }
    }
    @Test fun exactSnapshotCapSucceedsAndOneExtraByteStopsReadingAndClosesStream() = runBlocking {
        val cap = EnvelopeDomain.SNAPSHOT.byteLimit
        val exact = PaddedInput(encoded("aqsnapshot:v1"),cap)
        reader.snapshot(exact,key).close()
        assertTrue(exact.closed)
        val excess = PaddedInput(encoded("aqsnapshot:v1"),cap+5000)
        fails(EnvelopeError.TOO_LARGE) { reader.snapshot(excess,key) }
        assertEquals(cap+1, excess.count); assertTrue(excess.closed)
    }
    @Test fun backupHasSeparate64MiBCapAndSnapshotLimitRemains10MiB() = runBlocking {
        val bytes = encoded("aqbak:v1")
        val input = PaddedInput(bytes,EnvelopeDomain.BACKUP.byteLimit)
        reader.password(input,EnvelopeDomain.BACKUP,"  اختبار Passé  ".toCharArray()).close()
        val excess = PaddedInput(bytes,EnvelopeDomain.BACKUP.byteLimit+2)
        fails(EnvelopeError.TOO_LARGE) { reader.password(excess,EnvelopeDomain.BACKUP,"  اختبار Passé  ".toCharArray()) }
        assertEquals(EnvelopeDomain.BACKUP.byteLimit+1, excess.count)
    }
    @Test fun resourcePreflightRejectsBeforeDerivingOrAllocatingPayloadAndCleansStage() {
        fails(EnvelopeError.RESOURCE_LIMIT) { EnvelopeReader(temporary.root) { 1 }.snapshot(encoded("aqsnapshot:v1").inputStream(),key) }
    }
    @Test fun cleanupRemovesOnlyOwnedStagingFiles() = runBlocking {
        val directory = File(temporary.root,"aqaqeer-envelope-stage").apply { mkdirs() }
        val old = File(directory,"envelope-abandoned.stage").apply { writeText("encrypted fixture") }
        val other = File(directory,"other-file").apply { writeText("keep") }
        reader.cleanupAbandoned()
        assertFalse(old.exists()); assertTrue(other.exists())
    }
    @Test fun cancelledReadClosesInputRemovesStageAndReleasesGlobalLock() = runBlocking {
        val parent = Job()
        val stream = object : InputStream() {
            var closed = false
            override fun read(): Int { parent.cancel(); return 32 }
            override fun close() { closed = true }
        }
        val result = CoroutineScope(parent + Dispatchers.Default).async { reader.snapshot(stream,key) }
        try { result.await(); fail("Expected cancellation") } catch (_: CancellationException) { }
        parent.join()
        assertTrue(stream.closed)
        assertTrue(temporary.root.walk().none { it.name.endsWith(".stage") })
        reader.snapshot(encoded("aqsnapshot:v1").inputStream(),key).close()
    }
    @Test fun ioFailureIsSanitizedAndInputIsClosed() {
        var closed = false
        val input = object : InputStream() {
            override fun read(): Int = throw IOException("sensitive provider detail")
            override fun close() { closed = true }
        }
        fails(EnvelopeError.IO) { reader.snapshot(input,key) }
        assertTrue(closed)
    }
    @Test fun closedPayloadCannotBeReadAgain() = runBlocking {
        val payload = reader.snapshot(encoded("aqsnapshot:v1").inputStream(),key)
        payload.close()
        assertThrows(IllegalStateException::class.java) { payload.withUtf8Reader { it.read() } }
        Unit
    }
    @Test fun fragmentedZeroLengthReadsAndMisreportedAvailableDoNotChangeTheResult() = runBlocking {
        val input = object : ByteArrayInputStream(encoded("aqsnapshot:v1")) {
            var zero = true
            override fun available() = Int.MAX_VALUE
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (zero) { zero = false; return 0 }
                return super.read(buffer, offset, minOf(1,length))
            }
        }
        reader.snapshot(input,key).close()
    }
    @Test fun invalidCredentialInputsAreRejectedWithoutChangingCallerArrays() {
        for (password in listOf("12345", "12 3456", "abcdef")) {
            val chars = password.toCharArray()
            fails(EnvelopeError.INVALID_CREDENTIAL) { reader.password(encoded("aqz:v1").inputStream(), EnvelopeDomain.CONNECTION,chars) }
            assertArrayEquals(password.toCharArray(),chars)
        }
        fails(EnvelopeError.INVALID_CREDENTIAL) { reader.snapshot(encoded("aqsnapshot:v1").inputStream(),ByteArray(31)) }
        fails(EnvelopeError.INVALID_CREDENTIAL) { reader.password(encoded("aqbak:v1").inputStream(),EnvelopeDomain.BACKUP,"short".toCharArray()) }
    }
    @Test fun cancellingAQueuedReadDoesNotDeleteTheActiveReadersStage() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val firstInput = object : ByteArrayInputStream(encoded("aqsnapshot:v1")) {
            var first = true
            override fun read(buffer: ByteArray,offset: Int,length: Int): Int {
                if (first) {
                    first = false; entered.complete(Unit)
                    check(release.await(5,TimeUnit.SECONDS))
                }
                return super.read(buffer,offset,length)
            }
        }
        val first = async(Dispatchers.Default) { reader.snapshot(firstInput,key) }
        try {
            withTimeout(5_000) { entered.await() }
            var secondClosed = false
            val secondInput = object : ByteArrayInputStream(encoded("aqsnapshot:v1")) {
                override fun close() { secondClosed = true; super.close() }
            }
            val second = async(start = CoroutineStart.UNDISPATCHED) { reader.snapshot(secondInput,key) }
            second.cancelAndJoin()
            assertTrue(secondClosed)
            assertTrue(temporary.root.walk().any { it.name.endsWith(".stage") })
        } finally { release.countDown() }
        first.await().close()
        assertTrue(temporary.root.walk().none { it.name.endsWith(".stage") })
    }
    @Test fun inputCloseFailuresAreSanitizedEvenAfterSuccessfulAuthentication() {
        val input = object : ByteArrayInputStream(encoded("aqsnapshot:v1")) {
            override fun close() { throw IOException("sensitive close detail") }
        }
        fails(EnvelopeError.IO) { reader.snapshot(input,key) }
    }
}
