package com.seance.app.data.smb

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SmbErrorClassifierTest {

    @Test
    fun socketTimeoutIsClassifiedAsUnreachable() {
        val message = classifySmbError(SocketTimeoutException())
        assertTrue(message.contains("не отвечает"))
    }

    @Test
    fun coroutineTimeoutIsClassifiedAsUnreachable() {
        // TimeoutCancellationException's constructor is internal to kotlinx.coroutines - the only
        // supported way to obtain a real instance is to let withTimeout throw one.
        val thrown = try {
            runBlocking { withTimeout(1) { delay(100) } }
            null
        } catch (e: TimeoutCancellationException) {
            e
        }
        val message = classifySmbError(requireNotNull(thrown))
        assertTrue(message.contains("не отвечает"))
    }

    @Test
    fun unknownHostIncludesTheAttemptedHost() {
        val message = classifySmbError(UnknownHostException("nas.local"))
        assertTrue(message.contains("nas.local"))
    }

    @Test
    fun connectExceptionMentionsPort() {
        val message = classifySmbError(ConnectException("refused"))
        assertTrue(message.contains("445"))
    }

    @Test
    fun logonFailureIsClassifiedAsBadCredentials() {
        val message = classifySmbError(RuntimeException("STATUS_LOGON_FAILURE (0xc000006d)"))
        assertEquals("Неверный логин или пароль", message)
    }

    @Test
    fun accessDeniedIsClassifiedAsBadCredentials() {
        val message = classifySmbError(RuntimeException("STATUS_ACCESS_DENIED"))
        assertEquals("Неверный логин или пароль", message)
    }

    @Test
    fun badNetworkNameIsClassifiedAsMissingShare() {
        val message = classifySmbError(RuntimeException("STATUS_BAD_NETWORK_NAME"))
        assertEquals("Шара с таким именем не найдена на сервере", message)
    }

    @Test
    fun statusCodeMatchingIsCaseInsensitive() {
        val message = classifySmbError(RuntimeException("status_access_denied"))
        assertEquals("Неверный логин или пароль", message)
    }

    @Test
    fun unrecognizedExceptionFallsBackToItsMessage() {
        val message = classifySmbError(RuntimeException("something odd happened"))
        assertEquals("Не удалось подключиться: something odd happened", message)
    }

    @Test
    fun nullMessageFallsBackToClassName() {
        val message = classifySmbError(object : RuntimeException() {
            override val message: String? = null
        })
        assertTrue(message.contains("Не удалось подключиться"))
    }
}
