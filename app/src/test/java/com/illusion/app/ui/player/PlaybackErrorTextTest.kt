package com.illusion.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Ошибки плеера уходят прямо на экран, поэтому важно, что в них не просачивается техническая
 * строка исключения - именно её пользователь и видел раньше вместо объяснения.
 */
class PlaybackErrorTextTest {

    @Test
    fun `отвалившийся диск объясняется без кода состояния`() {
        val message = humanPlaybackError(IOException("SMBApiException: STATUS_BAD_NETWORK_NAME (0xc00000cc)"), "Source error")
        assertTrue(message.contains("диск"))
        assertFalse(message.contains("STATUS_BAD_NETWORK_NAME"))
    }

    @Test
    fun `пропавший файл предлагает пересканировать библиотеку`() {
        val message = humanPlaybackError(IOException("STATUS_OBJECT_NAME_NOT_FOUND"), null)
        assertTrue(message.contains("Пересканируйте"))
    }

    @Test
    fun `неверный пароль отправляет в настройки источников`() {
        val message = humanPlaybackError(IOException("STATUS_LOGON_FAILURE"), null)
        assertTrue(message.contains("Источники SMB"))
    }

    @Test
    fun `таймаут говорит проверить сеть, а не про сокет`() {
        val message = humanPlaybackError(SocketTimeoutException("Read timed out"), null)
        assertTrue(message.contains("не отвечает"))
        assertFalse(message.contains("timed out"))
    }

    @Test
    fun `неизвестный хост не показывает имя класса исключения`() {
        val message = humanPlaybackError(UnknownHostException("nas.local"), null)
        assertTrue(message.contains("адрес"))
        assertFalse(message.contains("UnknownHostException"))
    }

    @Test
    fun `неподдерживаемый кодек предлагает внешний плеер`() {
        val message = humanPlaybackError(IllegalStateException("Decoder init failed: c2.android.mpeg4.decoder"), null)
        assertTrue(message.contains("внешнем плеере"))
    }

    @Test
    fun `незнакомая ошибка не превращается в пустую строку`() {
        val message = humanPlaybackError(null, null)
        assertEquals("Не удалось воспроизвести файл. Проверьте, что сервер включён и телефон в домашней сети", message)
    }

    private fun assertFalse(condition: Boolean) = assertTrue(!condition)
}
