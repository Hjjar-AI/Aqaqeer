package com.aqaqeer.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aqaqeer.app.security.files.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class EnvelopeInstrumentedTest {
    private fun providerReader(root: File): EnvelopeReader {
        // These cases verify Android crypto providers, not runtime memory policy.
        // The three public envelopes are <= 266 bytes; retain a bounded 1 MiB
        // workspace through the existing seam rather than depending on heap reserve.
        val runtime = Runtime.getRuntime()
        val headroom = runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()
        assertTrue("Public vector fixture needs 2 MiB headroom; observed $headroom", headroom >= 2L * 1024 * 1024)
        android.util.Log.i("EnvelopeProviderFixture", "maxHeap=${runtime.maxMemory()} headroom=$headroom fixtureBudget=1048576")
        return EnvelopeReader(root) { 1024L * 1024 }
    }

    @Test fun androidProviderReadsAllPublicVectorsIncludingExactUnicodePassphrase(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = File(instrumentation.targetContext.noBackupFilesDir,"envelope-test-${UUID.randomUUID()}")
        try {
            val reader = providerReader(root)
            val vectors = instrumentation.context.assets.open("v1-vectors.json").bufferedReader().use {
                Json.parseToJsonElement(it.readText()).jsonObject["vectors"]!!.jsonArray
            }
            for (value in vectors) {
                val v = value.jsonObject
                val domain = EnvelopeDomain.entries.single { it.marker == v["domain"]!!.jsonPrimitive.content }
                val password = v["publicTestPassword"]!!.jsonPrimitive.contentOrNull?.toCharArray()
                val bytes = v["envelope"]!!.toString().toByteArray(Charsets.UTF_8)
                val key = v["keyHex"]!!.jsonPrimitive.content.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val payload = if (password == null) reader.snapshot(bytes.inputStream(),key)
                    else reader.password(bytes.inputStream(),domain,password)
                payload.use { assertEquals(v["plaintextUtf8"]!!.jsonPrimitive.content,it.withUtf8Reader { r -> r.readText() }) }
            }
            assertTrue(root.walk().none { it.name.endsWith(".stage") })
        } finally { root.deleteRecursively() } // Only this randomly named test-owned directory.
    }

    @Test fun androidAuthenticationFailureLeavesNoStagingFileOrPayload(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = File(instrumentation.targetContext.noBackupFilesDir,"envelope-test-${UUID.randomUUID()}")
        try {
            val vector = instrumentation.context.assets.open("v1-vectors.json").bufferedReader().use {
                Json.parseToJsonElement(it.readText()).jsonObject["vectors"]!!.jsonArray[0].jsonObject
            }
            try {
                providerReader(root).password(vector["envelope"]!!.toString().byteInputStream(),EnvelopeDomain.CONNECTION,"654321".toCharArray())
                fail("Wrong password accepted")
            } catch (error: EnvelopeException) { assertEquals(EnvelopeError.AUTHENTICATION,error.error) }
            assertTrue(root.walk().none { it.name.endsWith(".stage") })
        } finally { root.deleteRecursively() }
    }
}
