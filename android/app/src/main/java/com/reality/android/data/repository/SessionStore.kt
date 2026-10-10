package com.reality.android.data.repository

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.reality.android.data.remote.dto.StoredSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

private val Context.sessionDataStore by preferencesDataStore("reality_session")
data class SessionState(val loaded: Boolean = false, val session: StoredSession? = null, val notice: String? = null)

/** Only encrypted session bytes go to disk. The AES key stays in Android Keystore. */
@Singleton
class SessionStore @Inject constructor(@ApplicationContext private val context: Context, private val json: Json) {
    private val entry = stringPreferencesKey("encrypted_session")
    private val alias = "reality_session_aes_v1"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loaded = CompletableDeferred<Unit>()
    private val lock = Mutex()
    private val mutable = MutableStateFlow(SessionState())
    val state = mutable.asStateFlow()

    init {
        scope.launch {
            try {
                val bytes = context.sessionDataStore.data.first()[entry]
                val session = bytes?.let { json.decodeFromString<StoredSession>(decrypt(it)) }
                mutable.value = SessionState(loaded = true, session = session)
            } catch (_: Exception) {
                mutable.value = SessionState(loaded = true, notice = "Your saved sign-in could not be restored. Please sign in again.")
            } finally { loaded.complete(Unit) }
        }
    }

    suspend fun awaitLoaded() = loaded.await()

    suspend fun save(session: StoredSession) {
        awaitLoaded()
        lock.withLock {
            val ciphertext = encrypt(json.encodeToString(session))
            context.sessionDataStore.edit { it[entry] = ciphertext }
            mutable.value = SessionState(loaded = true, session = session)
        }
    }

    suspend fun clear(expectedToken: String? = null, notice: String? = null) {
        awaitLoaded()
        lock.withLock {
            if (expectedToken != null && mutable.value.session?.auth?.token != expectedToken) return
            mutable.value = SessionState(loaded = true, notice = notice)
            // A revoked or expired token remains unusable if a disk failure prevents its removal.
            runCatching { context.sessionDataStore.edit { it.remove(entry) } }
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val parts = value.split('.')
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        }
        return cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }
}
