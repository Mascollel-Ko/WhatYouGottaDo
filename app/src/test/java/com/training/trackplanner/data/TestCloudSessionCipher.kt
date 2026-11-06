package com.training.trackplanner.data

internal object TestCloudSessionCipher : CloudSessionCipher {
    override fun encrypt(cleartext: String): String = "test-only:" + cleartext.reversed()
    override fun decrypt(ciphertext: String): String {
        require(ciphertext.startsWith("test-only:"))
        return ciphertext.removePrefix("test-only:").reversed()
    }
}
