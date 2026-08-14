package com.seance.app.data.local.entity

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
    val enabled: Boolean = true
)
