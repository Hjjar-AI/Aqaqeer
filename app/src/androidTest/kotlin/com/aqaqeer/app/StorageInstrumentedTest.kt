package com.aqaqeer.app

import android.content.Context
import android.security.keystore.KeyInfo
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqaqeer.app.data.*
import com.aqaqeer.app.security.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.SecretKeyFactory

@RunWith(AndroidJUnit4::class)
class StorageInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val namespace = "m1test_${UUID.randomUUID()}"
    private val store = KeystoreSecureStore(context, namespace)
    private val factory = DatabaseFactory(context, namespace)
    private var db: AqaqeerDatabase? = null

    @After fun cleanOnlyThisTestProfile() {
        db?.close()
        context.deleteDatabase(factory.databaseName)
        File(context.noBackupFilesDir, "secure-$namespace").deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(store.keyAlias) }
    }

    @Test fun encryptedRoomDatabaseReopensWithTheSameKeystoreWrappedKey() = runBlocking {
        val first = factory.open().also { db = it }
        val seed = CatalogSeeds.unconfigured()
        first.withTransaction {
            first.catalogDao().insertCategories(seed.categories)
            first.catalogDao().insertForms(seed.forms)
            first.catalogDao().insertSections(seed.sections)
        }
        val keyBefore = DatabaseKeyProvider(store).get(databaseExists = true)
        first.close()
        val header = context.getDatabasePath(factory.databaseName).inputStream().use { input ->
            ByteArray(16).also { assertEquals(16, input.read(it)) }
        }
        assertNotEquals("SQLite format 3\u0000", header.toString(Charsets.ISO_8859_1))
        val reopened = factory.open().also { db = it }
        assertEquals(15, reopened.catalogDao().forms().size)
        assertEquals(CatalogSeeds.UNCATEGORIZED_ID, reopened.catalogDao().categories().single().id)
        val keyAfter = DatabaseKeyProvider(KeystoreSecureStore(context, namespace)).get(true)
        assertTrue(keyBefore.contentEquals(keyAfter))
        keyBefore.fill(0); keyAfter.fill(0)
    }

    @Test fun secureStoreUsesFreshNoncesAndNeedsNoUserAuthentication() {
        val secret = ByteArray(32) { it.toByte() }
        store.write("example", secret)
        val path = File(context.noBackupFilesDir, "secure-$namespace/example.bin")
        val first = path.readBytes()
        store.write("example", secret)
        assertFalse(first.contentEquals(path.readBytes()))
        assertTrue(secret.contentEquals(KeystoreSecureStore(context, namespace).read("example")))
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = keyStore.getKey(store.keyAlias, null)
        val info = SecretKeyFactory.getInstance("AES", "AndroidKeyStore").getKeySpec(key as javax.crypto.SecretKey, KeyInfo::class.java) as KeyInfo
        assertFalse(info.isUserAuthenticationRequired)
    }

    @Test fun aWrongDatabaseKeyCannotDeleteExistingData() = runBlocking {
        val original = factory.open().also { db = it }
        original.withTransaction {
            original.catalogDao().insertCategories(CatalogSeeds.categories)
            original.catalogDao().insertForms(CatalogSeeds.forms)
        }
        original.close()
        val correctKey = DatabaseKeyProvider(store).get(true)
        store.write("database-key", ByteArray(32) { 11 })
        val wrong = factory.open().also { db = it }
        var rejected = false
        try { wrong.catalogDao().forms() } catch (_: Exception) { rejected = true }
        finally { wrong.close() }
        assertTrue(rejected)
        assertTrue(context.getDatabasePath(factory.databaseName).isFile)
        store.write("database-key", correctKey)
        correctKey.fill(0)
        val recovered = factory.open().also { db = it }
        assertEquals(15, recovered.catalogDao().forms().size)
        assertEquals(CatalogSeeds.UNCATEGORIZED_ID, recovered.catalogDao().categories().single().id)
    }

    @Test fun missingKeystoreKeyDoesNotOverwriteWrappedDataOrGenerateAnotherKey() {
        store.write("example", ByteArray(32) { 7 })
        val file = File(context.noBackupFilesDir, "secure-$namespace/example.bin")
        val retained = file.readBytes()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.deleteEntry(store.keyAlias)
        assertThrows(StorageRecoveryRequired::class.java) { store.read("example") }
        assertTrue(retained.contentEquals(file.readBytes()))
        assertFalse(keyStore.containsAlias(store.keyAlias))
    }
}
