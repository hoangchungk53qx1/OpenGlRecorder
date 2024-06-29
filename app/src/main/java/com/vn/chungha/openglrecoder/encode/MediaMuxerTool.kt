package com.vn.chungha.openglrecoder.encode

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * This object is a utility for handling media muxing operations.
 */
object MediaMuxerTool {

    /**
     * This function extracts audio and video tracks from separate files and combines them into a single mp4 file.
     *
     * @param audioTrackFile The file containing the audio track.
     * @param videoTrackFile The file containing the video track.
     * @param resultFile The output file where the combined audio and video tracks will be written.
     *
     * @return Unit This function is a suspend function and does not return a value.
     */
    @SuppressLint("WrongConstant")
    suspend fun mixAvTrack(
        audioTrackFile: File,
        videoTrackFile: File,
        resultFile: File
    ) = withContext(Dispatchers.IO) {
        // Create a MediaMuxer for the output file
        val mediaMuxer = MediaMuxer(resultFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // Extract the audio and video tracks from the input files
        val (
            audioPair,
            videoPair
        ) = listOf(
            audioTrackFile to "audio/",
            videoTrackFile to "video/"
        ).map { (file, mimeType) ->
            // Create a MediaExtractor for the input file
            val mediaExtractor = MediaExtractor().apply {
                setDataSource(file.path)
            }
            // Find the track in the input file that matches the specified MIME type
            val trackIndex = (0 until mediaExtractor.trackCount)
                .map { index -> mediaExtractor.getTrackFormat(index) }
                .indexOfFirst { mediaFormat -> mediaFormat.getString(MediaFormat.KEY_MIME)?.startsWith(mimeType) == true }
            mediaExtractor.selectTrack(trackIndex)
            // Return a pair of the MediaExtractor and the MediaFormat for the track
            mediaExtractor to mediaExtractor.getTrackFormat(trackIndex)
        }

        val (audioExtractor, audioFormat) = audioPair
        val (videoExtractor, videoFormat) = videoPair

        // Add the audio and video tracks to the MediaMuxer and start it
        val audioTrackIndex = mediaMuxer.addTrack(audioFormat)
        val videoTrackIndex = mediaMuxer.addTrack(videoFormat)
        mediaMuxer.start()

        // Extract the data from the audio and video tracks and write it to the MediaMuxer
        listOf(
            audioExtractor to audioTrackIndex,
            videoExtractor to videoTrackIndex,
        ).forEach { (extractor, trackIndex) ->
            val byteBuffer = ByteBuffer.allocate(1024 * 4096)
            val bufferInfo = MediaCodec.BufferInfo()
            // Continue until there is no more data in the track
            while (isActive) {
                // Read the data from the track
                val offset = byteBuffer.arrayOffset()
                bufferInfo.size = extractor.readSampleData(byteBuffer, offset)
                // If there is no more data, break the loop
                if (bufferInfo.size < 0) break
                // Write the data to the MediaMuxer
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                mediaMuxer.writeSampleData(trackIndex, byteBuffer, bufferInfo)
                // Advance to the next sample in the track
                extractor.advance()
            }
            // Release the extractor
            extractor.release()
        }

        // Stop and release the MediaMuxer
        mediaMuxer.stop()
        mediaMuxer.release()
    }
}