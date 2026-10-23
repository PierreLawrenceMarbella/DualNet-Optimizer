package com.dualnet.optimizer.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore by preferencesDataStore("dualnet_preferences")

/** Credentials are encrypted by an Android Keystore key; backups are disabled. */
class PreferenceRepository(context: Context) {
    private val store = context.dataStore
    private val vault = CredentialVault()
    val preferences: Flow<Preferences> = store.data.map { p ->
        Preferences(
            mode = runCatching { ConnectionMode.valueOf(p[stringPreferencesKey("mode")] ?: "SMART") }.getOrDefault(ConnectionMode.SMART),
            autoStart = p[booleanPreferencesKey("autoStart")] ?: false,
            showSpeed = p[booleanPreferencesKey("showSpeed")] ?: false,
            notifications = p[booleanPreferencesKey("notifications")] ?: true,
            connectionLock = p[booleanPreferencesKey("connectionLock")] ?: false,
            serverHost = p[stringPreferencesKey("host")] ?: "",
            serverPort = p[intPreferencesKey("port")] ?: 4433,
            token = vault.decrypt(p[stringPreferencesKey("credential")] ?: ""),
            dns = p[stringPreferencesKey("dns")] ?: "1.1.1.1",
            routedApps = p[stringSetPreferencesKey("apps")] ?: emptySet(),
            mtu = p[intPreferencesKey("mtu")] ?: 1400,
            probeInterval = p[intPreferencesKey("probeInterval")] ?: 30
        )
    }
    suspend fun mode(mode: ConnectionMode) = store.edit { it[stringPreferencesKey("mode")] = mode.name }
    suspend fun toggle(key: String, value: Boolean) = store.edit { it[booleanPreferencesKey(key)] = value }
    suspend fun gateway(host: String, port: Int, token: String, mtu: Int, interval: Int) {
        val encrypted = vault.encrypt(token)
        store.edit { p -> p[stringPreferencesKey("host")] = host; p[intPreferencesKey("port")] = port; p[stringPreferencesKey("credential")] = encrypted; p[intPreferencesKey("mtu")] = mtu; p[intPreferencesKey("probeInterval")] = interval }
    }
    suspend fun dns(address: String) = store.edit { it[stringPreferencesKey("dns")] = address }
    suspend fun apps(packages: Set<String>) = store.edit { it[stringSetPreferencesKey("apps")] = packages }
}

private class CredentialVault {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("dualnet_token_v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("dualnet_token_v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun encrypt(text: String): String {
        if (text.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    fun decrypt(text: String): String = if (text.isEmpty()) "" else runCatching {
        val raw = Base64.decode(text, Base64.NO_WRAP)
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12))) }.doFinal(raw.copyOfRange(12, raw.size)).toString(Charsets.UTF_8)
    }.getOrDefault("")
}
