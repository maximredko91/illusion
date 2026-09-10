package com.illusion.app.ui.player

/**
 * Переводит ошибку воспроизведения в объяснение для человека.
 *
 * Раньше в плеер попадало то, что написано в самом исключении: «STATUS_BAD_NETWORK_NAME»,
 * «Connection reset by peer», «Source error» и подобное. Для пользователя это ничего не значит —
 * он видит техническую строку вместо ответа на единственный интересующий его вопрос: что случилось
 * и что теперь делать. Здесь каждая узнаваемая причина превращается в обычную фразу с подсказкой,
 * а неузнанные остаются с общим текстом (техническая деталь при этом не показывается вовсе -
 * пользы от неё на экране нет, а в logcat она остаётся).
 */
fun humanPlaybackError(cause: Throwable?, fallbackMessage: String?): String {
    val text = buildString {
        append(cause?.message.orEmpty())
        append(' ')
        append(cause?.javaClass?.name.orEmpty())
        append(' ')
        append(fallbackMessage.orEmpty())
    }

    fun has(vararg needles: String) = needles.any { text.contains(it, ignoreCase = true) }

    return when {
        // Диск отвалился от роутера или шару переименовали.
        has("STATUS_BAD_NETWORK_NAME") ->
            "Папка с фильмами сейчас недоступна. Обычно это значит, что диск отключился от роутера — " +
                "проверьте, что он подключён, и попробуйте снова"

        // Файл есть в библиотеке, но на диске его больше нет.
        has("STATUS_OBJECT_NAME_NOT_FOUND", "STATUS_OBJECT_PATH_NOT_FOUND", "FileNotFound") ->
            "Файл не найден на сервере — скорее всего, его переименовали или удалили. " +
                "Пересканируйте библиотеку в настройках"

        has("STATUS_LOGON_FAILURE") ->
            "Сервер не принял логин или пароль. Проверьте их в настройках, раздел «Источники SMB»"

        has("STATUS_ACCESS_DENIED") ->
            "Нет доступа к этому файлу: у пользователя, под которым подключается приложение, " +
                "не хватает прав на эту папку"

        has("UnknownHostException") ->
            "Не удалось найти сервер по адресу из настроек. Проверьте адрес и что телефон в домашней сети"

        has("SocketTimeout", "TimeoutCancellation", "ETIMEDOUT", "timed out") ->
            "Сервер не отвечает. Проверьте, что он включён, а телефон подключён к домашней сети"

        has("ConnectException", "ECONNREFUSED", "Connection refused") ->
            "Сервер отказал в подключении. Возможно, он ещё загружается или общий доступ выключен"

        has("ECONNRESET", "Connection reset", "Broken pipe", "SocketException", "EHOSTUNREACH", "ENETUNREACH") ->
            "Связь с сервером прервалась. Проверьте Wi-Fi и попробуйте снова"

        has("Permission denied", "EACCES") ->
            "Нет доступа к файлу на устройстве"

        has("ENOSPC", "No space left") ->
            "На устройстве закончилось место"

        // Медиа-часть: контейнер/кодек, а не сеть.
        has("Decoder init failed", "DecoderInitializationException", "MediaCodec") ->
            "Это видео не удалось воспроизвести: устройство не поддерживает его кодек. " +
                "Попробуйте открыть файл во внешнем плеере"

        has("UnrecognizedInputFormat", "ParserException", "Unsupported", "MalformedContainer") ->
            "Формат этого файла не распознан — возможно, файл повреждён или записан не до конца"

        has("Unable to connect", "Source error", "IOException") ->
            "Не удалось прочитать файл с сервера. Проверьте, что он включён и доступен по сети"

        else -> "Не удалось воспроизвести файл. Проверьте, что сервер включён и телефон в домашней сети"
    }
}
