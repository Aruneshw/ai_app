package com.example.domain

import android.util.Base64
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupEncryptor {

    private const val ITERATIONS = 1000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128

    // Derives a 256-bit AES key from a password and salt using PBKDF2WithHmacSHA1 (or SHA256)
    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    // Encrypts a plaintext string using AES-GCM with a password-derived key
    fun encrypt(plainText: String, password: String): String {
        val secureRandom = SecureRandom()
        
        // 1. Generate random salt and IV
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)
        
        val iv = ByteArray(IV_LENGTH)
        secureRandom.nextBytes(iv)

        // 2. Derive key
        val keySpec = deriveKey(password, salt)

        // 3. Initialize cipher
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        // 4. Encrypt
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // 5. Combine salt, IV, and ciphertext into a single byte array
        val buffer = ByteBuffer.allocate(SALT_LENGTH + IV_LENGTH + cipherText.size)
        buffer.put(salt)
        buffer.put(iv)
        buffer.put(cipherText)

        // 6. Return as Base64-encoded string
        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    }

    // Decrypts a Base64-encoded encrypted string using AES-GCM and the password
    fun decrypt(encryptedBase64: String, password: String): String {
        val encryptedData = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        
        if (encryptedData.size < SALT_LENGTH + IV_LENGTH) {
            throw IllegalArgumentException("Invalid encrypted backup file.")
        }

        val buffer = ByteBuffer.wrap(encryptedData)

        // 1. Extract salt
        val salt = ByteArray(SALT_LENGTH)
        buffer.get(salt)

        // 2. Extract IV
        val iv = ByteArray(IV_LENGTH)
        buffer.get(iv)

        // 3. Extract CipherText
        val cipherText = ByteArray(buffer.remaining())
        buffer.get(cipherText)

        // 4. Derive key
        val keySpec = deriveKey(password, salt)

        // 5. Initialize cipher
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        // 6. Decrypt
        val plainTextBytes = cipher.doFinal(cipherText)
        return String(plainTextBytes, Charsets.UTF_8)
    }
}
