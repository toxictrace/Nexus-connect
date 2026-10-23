package com.toxictrace.nexusconnect.data.backup

/**
 * Validates the structural length of an AES-CBC payload stored as IV + ciphertext.
 * AES uses a 16-byte IV and PKCS#5 padding, so ciphertext must contain at least
 * one 16-byte block and always be a multiple of the block size.
 */
internal object EncryptedBackupPayload {
    private const val BLOCK_SIZE_BYTES = 16
    private const val IV_SIZE_BYTES = BLOCK_SIZE_BYTES

    fun hasValidStructure(data: ByteArray): Boolean {
        if (data.size < IV_SIZE_BYTES + BLOCK_SIZE_BYTES) return false
        return (data.size - IV_SIZE_BYTES) % BLOCK_SIZE_BYTES == 0
    }
}
