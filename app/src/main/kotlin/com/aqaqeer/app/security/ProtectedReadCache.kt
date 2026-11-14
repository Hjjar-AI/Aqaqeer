package com.aqaqeer.app.security

/** Only decoded maintenance/compatibility metadata is cached; never connection secrets or DB keys. */
internal object ProtectedReadCache {
    val lock = Any()
    private data class Key(val scope: Any, val name: String)
    private data class Value(val decoded: Any?)
    private val values = mutableMapOf<Key, Value>()
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> read(store: SecureStore, name: String, decode: (ByteArray) -> T?): T? = synchronized(lock) {
        val key = Key(store.cacheScope, name)
        values[key]?.let { return@synchronized it.decoded as T? }
        try {
            val bytes = store.read(name)
            val result = if (bytes == null) null else try { decode(bytes) } finally { bytes.fill(0) }
            values[key] = Value(result)
            result
        } catch (error: Exception) { values.remove(key); throw error }
    }
    fun invalidate(store: SecureStore, name: String) = synchronized(lock) { values.remove(Key(store.cacheScope, name)); Unit }
    fun invalidateAll() = synchronized(lock) { values.clear() }
}
