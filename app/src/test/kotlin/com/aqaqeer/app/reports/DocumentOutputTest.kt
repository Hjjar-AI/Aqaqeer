package com.aqaqeer.app.reports

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException

class DocumentOutputTest {
    @Test fun nullProviderAndOpenFailureNeverInvokeWriter(): Unit = runBlocking {
        val context = mockk<Context>(); val resolver = mockk<ContentResolver>(); val uri = mockk<Uri>()
        every { context.contentResolver } returns resolver; every { uri.scheme } returns "content"
        var invoked = false
        every { resolver.openOutputStream(uri, "wt") } returns null
        assertThrows(IllegalArgumentException::class.java) { runBlocking { DocumentOutput(context).write(uri) { invoked = true } } }
        val failure = IOException("provider unavailable")
        every { resolver.openOutputStream(uri, "wt") } throws failure
        assertSame(failure, assertThrows(IOException::class.java) { runBlocking { DocumentOutput(context).write(uri) { invoked = true } } })
        assertFalse(invoked)
    }
    @Test fun originalWriterFailureSurvivesProviderCloseFailure(): Unit = runBlocking {
        val context = mockk<Context>(); val resolver = mockk<ContentResolver>(); val uri = mockk<Uri>()
        every { context.contentResolver } returns resolver; every { uri.scheme } returns "content"
        val writing = IOException("writer failed"); val closing = IOException("close failed")
        val stream = object : ByteArrayOutputStream() { override fun close() { throw closing } }
        every { resolver.openOutputStream(uri, "wt") } returns stream
        val error = assertThrows(IOException::class.java) { runBlocking { DocumentOutput(context).write(uri) { throw writing } } }
        assertSame(writing, error); assertArrayEquals(arrayOf(closing), error.suppressed)
    }
    @Test fun cancellationDuringWritingClosesProviderAndPropagates(): Unit = runBlocking {
        val context = mockk<Context>(); val resolver = mockk<ContentResolver>(); val uri = mockk<Uri>()
        every { context.contentResolver } returns resolver; every { uri.scheme } returns "content"
        var closed = false
        val stream = object : ByteArrayOutputStream() { override fun close() { closed = true; super.close() } }
        every { resolver.openOutputStream(uri, "wt") } returns stream
        assertThrows(kotlinx.coroutines.CancellationException::class.java) { runBlocking {
            DocumentOutput(context).write(uri) { it.write(1); throw kotlinx.coroutines.CancellationException("cancelled") }
        } }
        assertTrue(closed); assertArrayEquals(byteArrayOf(1), stream.toByteArray())
    }
    @Test fun providerCloseFailurePropagatesInsteadOfReportingSuccess(): Unit = runBlocking {
        val context = mockk<Context>(); val resolver = mockk<ContentResolver>(); val uri = mockk<Uri>()
        every { context.contentResolver } returns resolver; every { uri.scheme } returns "content"
        var closed = false
        val stream = object : ByteArrayOutputStream() { override fun close() { closed = true; throw IOException("provider close") } }
        every { resolver.openOutputStream(uri, "wt") } returns stream
        val error = assertThrows(IOException::class.java) { runBlocking { DocumentOutput(context).write(uri) { it.write(42) } } }
        assertEquals("provider close", error.message); assertTrue(closed); assertArrayEquals(byteArrayOf(42), stream.toByteArray())
    }
    @Test fun writerFailureStillClosesProviderAndFileUrisNeverOpen(): Unit = runBlocking {
        val context = mockk<Context>(); val resolver = mockk<ContentResolver>(); val uri = mockk<Uri>()
        every { context.contentResolver } returns resolver; every { uri.scheme } returns "content"
        var closed = false
        val stream = object : ByteArrayOutputStream() { override fun close() { closed = true; super.close() } }
        every { resolver.openOutputStream(uri, "wt") } returns stream
        assertThrows(IOException::class.java) { runBlocking { DocumentOutput(context).write(uri) { throw IOException("write") } } }
        assertTrue(closed)
        every { uri.scheme } returns "file"
        clearMocks(resolver, answers = false)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { DocumentOutput(context).write(uri) {} } }
        verify { resolver wasNot Called }
    }
}
