package com.example.geodouro_backend.auth

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class AuthTokenService(
    @Value("\${app.auth.token-secret:geodouro-dev-token-secret}")
    private val tokenSecret: String
) {
    fun createToken(userId: Int): String {
        val payload = userId.toString()
        val signature = sign(payload)
        return "$payload.$signature"
    }

    fun resolveUserId(authorizationHeader: String?): Int? {
        val token = authorizationHeader
            ?.trim()
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substringAfter(' ')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val parts = token.split('.')
        if (parts.size != 2) {
            throw invalidToken()
        }

        val payload = parts[0]
        val signature = parts[1]
        if (!constantTimeEquals(sign(payload), signature)) {
            throw invalidToken()
        }

        return payload.toIntOrNull() ?: throw invalidToken()
    }

    private fun sign(payload: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val secretKey = SecretKeySpec(tokenSecret.toByteArray(StandardCharsets.UTF_8), HMAC_ALGORITHM)
        mac.init(secretKey)
        val digest = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(left: String, right: String): Boolean {
        return MessageDigest.isEqual(
            left.toByteArray(StandardCharsets.UTF_8),
            right.toByteArray(StandardCharsets.UTF_8)
        )
    }

    private fun invalidToken(): ResponseStatusException {
        return ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de autenticacao invalido")
    }

    private companion object {
        const val HMAC_ALGORITHM = "HmacSHA256"
    }
}
