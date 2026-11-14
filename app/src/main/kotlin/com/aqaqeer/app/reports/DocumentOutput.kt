package com.aqaqeer.app.reports

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.OutputStream
import javax.inject.Inject

/** The caller reports success only after this function, including provider close, returns. */
class DocumentOutput @Inject constructor(@param:ApplicationContext private val context: Context) {
    internal suspend fun write(uri: Uri, writer: suspend (OutputStream) -> Unit) = withContext(Dispatchers.IO) {
        require(uri.scheme == "content")
        currentCoroutineContext().ensureActive()
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { output -> writer(output) }
        currentCoroutineContext().ensureActive()
    }
}
