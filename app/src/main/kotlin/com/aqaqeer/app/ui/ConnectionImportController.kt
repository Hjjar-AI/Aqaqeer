package com.aqaqeer.app.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.aqaqeer.app.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class ConnectionImportController(private val connectionImports: ConnectionImportRepository,
    private val scope: kotlinx.coroutines.CoroutineScope, private val navigate: (Route) -> Unit) {
    private val importState = MutableStateFlow(ConnectionImportUiState())
    val connectionImport: StateFlow<ConnectionImportUiState> = importState.asStateFlow()
    private var importOperation: Job? = null

    fun onBack() {
        if (importState.value.busy) importOperation?.cancel()
        importState.update { it.copy(selectedUri = null, error = null) }
    }

    fun openConnectionImport(uri: Uri? = null) {
        if (importState.value.busy) return
        importState.update { it.copy(selectedUri = uri, error = null, verification = null) }
        navigate(Route.Connection)
    }

    fun reloadConnectionImport() {
        if (importOperation?.isActive == true) return
        importState.update { it.copy(loading = true, error = null) }
        importOperation = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { restorePendingConnection() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { importState.update { it.copy(storageReady = false, error = connectionImportMessage(error)) } }
            finally { importState.update { it.copy(loading = false) } }
        }
    }

    /** Takes ownership of password; never place it in SavedStateHandle or a UI state flow. */
    fun stageConnection(password: CharArray) {
        val current = importState.value
        val uri = current.selectedUri
        if (current.loading || current.busy || !current.storageReady || current.pending != null || uri == null) {
            password.fill('\u0000'); return
        }
        importState.update { it.copy(busy = true, error = null) }
        importOperation = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val pending = connectionImports.stage(uri, password)
                importState.update { it.copy(pending = pending.info, requirements = pending.requirements,
                    verification = null, selectedUri = null) }
            } catch (error: CancellationException) {
                // A protected atomic write may have finished before dispatch-back cancellation.
                withContext(NonCancellable) {
                    try { restorePendingConnection() }
                    catch (failure: Exception) { importState.update { it.copy(storageReady = false, error = connectionImportMessage(failure)) } }
                }
                throw error
            } catch (error: Exception) {
                importState.update { it.copy(error = connectionImportMessage(error)) }
            } finally {
                password.fill('\u0000')
                importState.update { it.copy(busy = false) }
            }
        }.also { operation -> operation.invokeOnCompletion { password.fill('\u0000') } }
    }

    fun cancelConnectionImport() { if (importState.value.busy) importOperation?.cancel() }

    fun verifyPendingConnection(id: String) {
        val current = importState.value
        if (current.loading || current.busy || !current.storageReady || current.pending?.id != id) return
        importState.update { it.copy(busy = true, verifying = true, verification = null, error = null) }
        importOperation = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val verification = connectionImports.verify(id)
                importState.update { if (it.pending?.id == id)
                    it.copy(verification = verification, requirements = verification.requirements) else it }
            } catch (error: CancellationException) {
                withContext(NonCancellable) {
                    try { restorePendingConnection() }
                    catch (failure: Exception) { importState.update { it.copy(storageReady = false, error = connectionImportMessage(failure)) } }
                }
                throw error
            } catch (error: Exception) {
                withContext(NonCancellable) {
                    try {
                        restorePendingConnection()
                        importState.update { it.copy(error = connectionVerificationMessage(error)) }
                    } catch (failure: Exception) {
                        importState.update { it.copy(storageReady = false, error = connectionImportMessage(failure)) }
                    }
                }
            } finally { importState.update { it.copy(busy = false, verifying = false) } }
        }
    }

    fun activatePendingConnection(id: String) {
        val current = importState.value
        if (current.loading || current.busy || !current.storageReady || current.pending?.id != id) return
        importState.update { it.copy(busy = true, activating = true, verification = null, error = null) }
        importOperation = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                connectionImports.activate(id)
                restorePendingConnection()
                navigate(Route.Home)
            } catch (error: CancellationException) {
                withContext(NonCancellable) {
                    try { restorePendingConnection() }
                    catch (failure: Exception) { importState.update { it.copy(storageReady = false, error = connectionImportMessage(failure)) } }
                }
                throw error
            } catch (error: Exception) {
                withContext(NonCancellable) {
                    try {
                        restorePendingConnection()
                        // A completed transaction may only have needed pending-marker cleanup.
                        if (importState.value.pending == null) navigate(Route.Home)
                        else importState.update { it.copy(error = connectionVerificationMessage(error)) }
                    } catch (failure: Exception) {
                        importState.update { it.copy(storageReady = false, error = connectionImportMessage(failure)) }
                    }
                }
            } finally { importState.update { it.copy(busy = false, activating = false) } }
        }
    }

    fun discardPendingConnection(id: String) {
        if (importState.value.loading || importState.value.busy || !importState.value.storageReady) return
        importState.update { it.copy(busy = true, error = null) }
        importOperation = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                connectionImports.discard(id)
                restorePendingConnection()
                importState.update { it.copy(selectedUri = null) }
            } catch (error: CancellationException) {
                withContext(NonCancellable) {
                    try { restorePendingConnection() }
                    catch (failure: Exception) { importState.update { it.copy(storageReady = false, error = connectionImportMessage(failure)) } }
                }
                throw error
            } catch (error: Exception) { importState.update { it.copy(error = connectionImportMessage(error)) } }
            finally { importState.update { it.copy(busy = false) } }
        }
    }

    private suspend fun restorePendingConnection() {
        val pending = connectionImports.loadPending()
        importState.update { it.copy(pending = pending.info, requirements = pending.requirements,
            verification = null, storageReady = true) }
    }
}
