package com.illusion.app.data.backup

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class BackupPayloadTest {
    private val source = BackupSource("NAS", "nas", "media", "Movies", "", "reader")

    @Test fun distinguishesRootsAndAccountsOnOneShare() {
        assertTrue(matchesSource(source, "NAS", "MEDIA", "/Movies/", "", "reader"))
        assertFalse(matchesSource(source, "nas", "media", "Series", "", "reader"))
        assertFalse(matchesSource(source, "nas", "media", "Movies", "", "other"))
    }

    @Test fun portableReferenceSurvivesRoundTripWithoutLocalSourceId() {
        val ref = BackupMediaRef(source, "Movies\\Film.mkv", 123456)
        val payload = BackupPayload(2, listOf(source), listOf(BackupFavorite("old-id", 1, ref)),
            listOf(BackupWatchProgress("old-id", 12, 100, false, 2, ref)))
        assertEquals(payload, Json.decodeFromString<BackupPayload>(Json.encodeToString(payload)))
    }

    @Test fun readsLegacyCopiesWithoutReferences() {
        val payload = Json.decodeFromString<BackupPayload>("""{"sources":[],"favorites":[{"stableId":"old","addedAt":1}],"watchProgress":[]}""")
        assertEquals(1, payload.version)
        assertNull(payload.favorites.single().media)
    }
}
