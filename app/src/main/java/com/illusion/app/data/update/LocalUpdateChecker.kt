package com.illusion.app.data.update

import android.os.Build
import com.illusion.app.data.repository.SmbSourceRepository
import com.illusion.app.data.smb.SmbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Counterpart to [UpdateChecker], reading the same [UpdateInfo]/[UpdateCheckResult] contract from
 * a manifest published by hand to `IllusionUpdates/manifest.json` on a configured SMB source,
 * instead of GitHub's API - for updating a device with flaky/no internet but a working connection
 * to the home NAS. The APK itself is downloaded by [com.illusion.app.work.UpdateDownloadWorker]
 * over the same SMB connection, via [LocalUpdateUri]-encoded (sourceId, path) carried in
 * [UpdateInfo.apkDownloadUrl] - this class never downloads the APK itself, only the small
 * manifest, matching how little [UpdateChecker] itself does beyond one GitHub API call.
 */
class LocalUpdateChecker(
    private val smbSourceRepository: SmbSourceRepository,
    private val smbClient: SmbClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(sourceId: Long, currentVersionCode: Int): UpdateCheckResult = withContext(Dispatchers.IO) {
        val info = smbSourceRepository.connectionInfoById(sourceId)
            ?: return@withContext UpdateCheckResult.Failed("Источник обновлений не найден")
        val connection = runCatching { smbClient.connect(info) }.getOrElse {
            return@withContext UpdateCheckResult.Failed(it.message ?: "Не удалось подключиться к NAS")
        }
        try {
            val manifestText = runCatching {
                connection.openInputStream(MANIFEST_PATH).use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrElse {
                return@withContext UpdateCheckResult.Failed("Файл $MANIFEST_PATH не найден на источнике")
            }
            val manifest = runCatching { json.decodeFromString(LocalUpdateManifest.serializer(), manifestText) }
                .getOrElse { return@withContext UpdateCheckResult.Failed("Некорректный manifest.json") }
            val manifestInfo = "манифест: v${manifest.versionCode} (${manifest.versionName})"
            if (manifest.versionCode <= currentVersionCode) return@withContext UpdateCheckResult.UpToDate(manifestInfo)
            val asset = selectAssetForDevice(manifest.assets) ?: return@withContext UpdateCheckResult.UpToDate(manifestInfo)
            UpdateCheckResult.Available(
                UpdateInfo(
                    versionCode = manifest.versionCode,
                    versionName = manifest.versionName,
                    releaseNotes = manifest.releaseNotes,
                    apkDownloadUrl = LocalUpdateUri.build(sourceId, "$UPDATE_DIR\\${asset.fileName}"),
                    apkSizeBytes = asset.sizeBytes,
                    releasePageUrl = "",
                    mandatory = manifest.mandatory
                )
            )
        } finally {
            connection.close()
        }
    }

    /** Same preference order as [UpdateChecker.selectApkForDevice] - most-preferred ABI first. */
    private fun selectAssetForDevice(assets: List<LocalUpdateManifestAsset>): LocalUpdateManifestAsset? {
        if (assets.size <= 1) return assets.firstOrNull()
        return Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi ->
            assets.firstOrNull { it.abi.equals(abi, ignoreCase = true) }
        } ?: assets.first()
    }

    companion object {
        const val UPDATE_DIR = "IllusionUpdates"
        const val MANIFEST_PATH = "$UPDATE_DIR\\manifest.json"
    }
}
