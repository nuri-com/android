package com.bitwarden.data.datasource.disk

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.bitwarden.annotation.OmitFromCoverage
import com.bitwarden.core.data.manager.encryption.EncryptionManager
import java.util.Base64

private const val ALIAS: String = "KeystoreEncryptedSharedPreferences"

/**
 * An implementation of [SharedPreferences] that encrypts the values using the AndroidKeystore.
 */
@OmitFromCoverage
@Suppress("TooManyFunctions")
internal class KeystoreEncryptedSharedPreferences(
    app: Application,
    private val encryptionManager: EncryptionManager,
) : SharedPreferences {
    private val sharedPreferences: SharedPreferences = app.getSharedPreferences(
        "${app.packageName}_keystore_encrypted_preferences",
        Context.MODE_PRIVATE,
    )

    override fun contains(key: String): Boolean = sharedPreferences.contains(key)

    override fun edit(): SharedPreferences.Editor = Editor(
        encryptionManager = encryptionManager,
        editor = sharedPreferences.edit(),
    )

    override fun getAll(): Map<String, *> = sharedPreferences
        .all
        .mapValues { (_, value) ->
            // Value is always a string since we always encode data to a string.
            (value as? String)
                ?.let { Base64.getDecoder().decode(it) }
                ?.let { encryptionManager.decrypt(alias = ALIAS, bytes = it).getOrNull() }
        }

    override fun getString(
        key: String,
        defValue: String?,
    ): String? = decryptAndGetByteArray(key = key)?.decodeToString() ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        unsupportedFeature("getBoolean")
    }

    override fun getFloat(key: String, defValue: Float): Float {
        unsupportedFeature("getFloat")
    }

    override fun getInt(key: String, defValue: Int): Int {
        unsupportedFeature("getInt")
    }

    override fun getLong(key: String, defValue: Long): Long {
        unsupportedFeature("getLong")
    }

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        unsupportedFeature("getStringSet")
    }

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun decryptAndGetByteArray(
        key: String,
    ): ByteArray? = sharedPreferences
        .getString(key, null)
        ?.let { Base64.getDecoder().decode(it) }
        ?.let { encryptionManager.decrypt(alias = ALIAS, bytes = it).getOrNull() }
}

@Suppress("TooManyFunctions")
private class Editor(
    private val encryptionManager: EncryptionManager,
    private val editor: SharedPreferences.Editor,
) : SharedPreferences.Editor {
    override fun apply(): Unit = editor.apply()

    override fun clear(): SharedPreferences.Editor {
        editor.clear()
        // Always return `this` to ensure any chaining uses the editor that handles encryption.
        return this
    }

    override fun commit(): Boolean = editor.commit()

    override fun putString(
        key: String,
        value: String?,
    ): SharedPreferences.Editor = value
        ?.let { encryptAndPutByteArray(key = key, value = it.encodeToByteArray()) }
        ?: remove(key = key)

    override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
        unsupportedFeature("putBoolean")
    }

    override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
        unsupportedFeature("putFloat")
    }

    override fun putInt(key: String, value: Int): SharedPreferences.Editor {
        unsupportedFeature("putInt")
    }

    override fun putLong(key: String, value: Long): SharedPreferences.Editor {
        unsupportedFeature("putLong")
    }

    override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
        unsupportedFeature("putStringSet")
    }

    override fun remove(key: String): SharedPreferences.Editor {
        editor.remove(key)
        // Always return `this` to ensure any chaining uses the editor that handles encryption.
        return this
    }

    private fun encryptAndPutByteArray(
        key: String,
        value: ByteArray,
    ): SharedPreferences.Editor {
        editor.putString(
            key,
            encryptionManager
                .encrypt(alias = ALIAS, bytes = value)
                .map { Base64.getEncoder().encodeToString(it) }
                .getOrNull(),
        )
        // Always return `this` to ensure any chaining uses the editor that handles encryption.
        return this
    }
}

private fun unsupportedFeature(name: String): Nothing {
    throw UnsupportedOperationException(
        "$name is not supported by KeystoreEncryptedSharedPreferences",
    )
}
