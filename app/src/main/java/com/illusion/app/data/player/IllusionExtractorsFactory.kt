package com.illusion.app.data.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.text.SubtitleParser
import com.illusion.app.data.player.asf.AsfExtractor
import com.illusion.app.data.player.mkv.OutOfBandCuesExtractor

/**
 * Media3's own extractors plus the app's [AsfExtractor] for .wmv, which Media3 doesn't ship.
 *
 * ASF goes first: its sniff is a single 16-byte GUID compare, while some of Media3's sniffers scan
 * forward for sync patterns and have claimed files that weren't theirs before (an XviD/MP3 .avi parsed
 * as bare MP3 - see SmbMediaUri).
 *
 * The configuration calls DefaultMediaSourceFactory makes on its extractors factory (subtitle
 * parsing during extraction) are forwarded to the wrapped DefaultExtractorsFactory - dropping them
 * would silently break embedded subtitles in MKV/MP4.
 *
 * [disableCuesSeek] is set only for .mkv files known to hang on Media3's own jump to the Cues (see
 * PlayerViewModel's stall watchdog): their MatroskaExtractor gets that jump turned off and is wrapped
 * in [OutOfBandCuesExtractor], which reads the Cues through [dataSourceFactory] instead so seeking
 * still works.
 */
@OptIn(UnstableApi::class)
class IllusionExtractorsFactory(
    private val disableCuesSeek: Boolean,
    private val dataSourceFactory: DataSource.Factory
) : ExtractorsFactory {

    private val defaults = DefaultExtractorsFactory().apply {
        // See PlayerViewModel.createPlayer: only for files already known to hang on their Cues table.
        if (disableCuesSeek) setMatroskaExtractorFlags(MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES)
    }

    override fun createExtractors(): Array<Extractor> =
        arrayOf<Extractor>(AsfExtractor()) + defaults.createExtractors()

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> {
        val extractors = defaults.createExtractors(uri, responseHeaders).map { extractor ->
            if (disableCuesSeek && extractor is MatroskaExtractor) {
                OutOfBandCuesExtractor(extractor, dataSourceFactory, uri)
            } else {
                extractor
            }
        }
        return arrayOf<Extractor>(AsfExtractor()) + extractors
    }

    override fun setSubtitleParserFactory(subtitleParserFactory: SubtitleParser.Factory): ExtractorsFactory {
        defaults.setSubtitleParserFactory(subtitleParserFactory)
        return this
    }

    @Deprecated("Deprecated in Java")
    override fun experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled: Boolean): ExtractorsFactory {
        @Suppress("DEPRECATION")
        defaults.experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled)
        return this
    }

    override fun experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParseWithinGopSampleDependencies: Int): ExtractorsFactory {
        defaults.experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParseWithinGopSampleDependencies)
        return this
    }
}
