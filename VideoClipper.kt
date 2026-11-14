package ai.clipforge

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

data class ClipRange(val startMs: Long, val endMs: Long, val title: String)

object VideoClipper {
    /** Remuxes the selected time range without re-encoding; keeps original dimensions and codec. */
    fun export(context: Context, source: Uri, range: ClipRange): File {
        val extractor = MediaExtractor()
        val pfd = context.contentResolver.openFileDescriptor(source, "r")
            ?: error("Cannot open selected video")
        extractor.setDataSource(pfd.fileDescriptor)
        val out = File(context.getExternalFilesDir(null), "clipforge_${range.startMs}_${range.endMs}.mp4")
        if (out.exists()) out.delete()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val map = mutableMapOf<Int, Int>()
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/") || mime.startsWith("audio/")) map[i] = muxer.addTrack(format)
        }
        require(map.isNotEmpty()) { "No supported audio/video tracks found" }
        muxer.start()
        val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
        val info = android.media.MediaCodec.BufferInfo()
        for ((sourceTrack, _) in map) extractor.selectTrack(sourceTrack)
        extractor.seekTo(range.startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        var firstPts = -1L
        try {
            while (true) {
                val track = extractor.sampleTrackIndex
                if (track < 0) break
                val pts = extractor.sampleTime
                if (pts > range.endMs * 1000) break
                if (map.containsKey(track)) {
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    if (firstPts < 0) firstPts = pts
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = (pts - firstPts).coerceAtLeast(0)
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(map.getValue(track), buffer, info)
                }
                if (!extractor.advance()) break
            }
        } finally {
            muxer.stop()
            muxer.release()
            extractor.release()
            pfd.close()
        }
        return out
    }
}
