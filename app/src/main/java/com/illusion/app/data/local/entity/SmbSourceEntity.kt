package com.illusion.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "smb_sources")
data class SmbSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val host: String,
    val share: String,
    val rootPath: String,
    val domain: String,
    val username: String,
    val enabled: Boolean = true,
    /**
     * Запасные адреса того же диска. Один и тот же SSD пользователь подключает в разных местах к
     * разным роутерам, и там у него другой IP - без этого поля приходилось заводить второй
     * источник, а значит получать всю библиотеку в двух экземплярах: в stableId входит sourceId
     * (см. StableIdGenerator). Адрес выбирается по текущей сети - см. [com.illusion.app.data.smb.chooseReachableHost].
     */
    val alternateHosts: List<String> = emptyList()
)
