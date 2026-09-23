package com.illusion.app.data.update

import com.illusion.app.data.update.UpdateChecker.Companion.parseMandatory
import com.illusion.app.data.update.UpdateChecker.Companion.selectApkForDevice
import com.illusion.app.data.update.UpdateChecker.Companion.versionCodeFromTag
import com.illusion.app.data.update.UpdateChecker.Companion.versionNameFromRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private fun asset(name: String) = GitHubReleaseAsset(name, "https://example/$name")

    private val perAbi = listOf(
        asset("illusion-170-armeabi-v7a.apk"),
        asset("illusion-170-arm64-v8a.apk"),
        asset("illusion-170-x86_64.apk"),
        asset("illusion-170-x86.apk"),
        asset("checksums.txt")
    )

    @Test
    fun picksMostPreferredSupportedAbi() {
        val picked = selectApkForDevice(perAbi, listOf("arm64-v8a", "armeabi-v7a", "armeabi"))
        assertEquals("illusion-170-arm64-v8a.apk", picked?.name)
    }

    @Test
    fun x86DeviceDoesNotGetX86_64Build() {
        assertEquals("illusion-170-x86.apk", selectApkForDevice(perAbi, listOf("x86"))?.name)
    }

    @Test
    fun armeabiDoesNotMatchArmeabiV7a() {
        val assets = listOf(asset("illusion-armeabi-v7a.apk"), asset("illusion-universal.apk"))
        // No exact match - falls back to the first .apk rather than wrongly "matching".
        assertEquals("illusion-armeabi-v7a.apk", selectApkForDevice(assets, listOf("armeabi"))?.name)
        assertEquals(
            "illusion-armeabi-v7a.apk",
            selectApkForDevice(assets.reversed(), listOf("armeabi-v7a"))?.name
        )
    }

    @Test
    fun singleOrNoApk() {
        assertEquals("app.apk", selectApkForDevice(listOf(asset("app.apk"), asset("notes.txt")), listOf("arm64-v8a"))?.name)
        assertNull(selectApkForDevice(listOf(asset("notes.txt")), listOf("arm64-v8a")))
    }

    @Test
    fun unknownAbiFallsBackToFirstApk() {
        assertEquals("illusion-170-armeabi-v7a.apk", selectApkForDevice(perAbi, listOf("riscv64"))?.name)
    }

    @Test
    fun mandatoryMarkerIsStripped() {
        val (mandatory, notes) = parseMandatory("[ОБЯЗАТЕЛЬНОЕ]\r\n\r\nИсправлен вылет")
        assertTrue(mandatory)
        assertEquals("Исправлен вылет", notes)
        assertTrue(parseMandatory("  [mandatory]  \nx").first)
    }

    @Test
    fun markerAloneLeavesNoNotes() {
        assertEquals(true to "", parseMandatory("[MANDATORY]"))
    }

    @Test
    fun ordinaryBodyUntouched() {
        val body = "Что нового\n[ОБЯЗАТЕЛЬНОЕ]"
        val (mandatory, notes) = parseMandatory(body)
        assertFalse(mandatory)
        assertEquals(body, notes)
    }

    @Test
    fun versionFromTagAndTitle() {
        assertEquals(170, versionCodeFromTag("v170"))
        assertEquals(70, versionCodeFromTag("70"))
        assertNull(versionCodeFromTag("latest"))
        val release = GitHubRelease(tagName = "v170", name = "v170 (0.1.0-beta98)", htmlUrl = "")
        assertEquals("0.1.0-beta98", versionNameFromRelease(release))
        assertEquals("v170", versionNameFromRelease(release.copy(name = "Бета")))
        assertEquals("v170", versionNameFromRelease(release.copy(name = null)))
        // The format releases are actually published with: versionCode in the parentheses.
        assertEquals("0.1.0-beta99", versionNameFromRelease(release.copy(name = "0.1.0-beta99 (172)")))
    }
}
