package com.vn.chungha.openglrecoder.encode

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * This class is responsible for recording internal audio.
 * It uses the AudioRecord class to capture audio and the MediaMuxer class to write the audio data into a file.
 */
@SuppressLint("MissingPermission")
@RequiresApi(Build.VERSION_CODES.Q)
class InternalAudioRecorder {
    private var audioRecord: AudioRecord? = null
    private var audioEncoder: AudioEncoder? = null
    private var mediaMuxer: MediaMuxer? = null

    /** The file where the recorded audio will be stored */
    var audioRecordingFile: File? = null
        private set

    /**
     * Prepares the recorder with the provided parameters.
     *
     * @param context The application context.
     * @param mediaProjection The MediaProjection instance used to capture audio.
     * @param samplingRate The sampling rate for the audio data.
     * @param channelCount The number of audio channels.
     */
    fun prepareRecorder(
        context: Context,
        mediaProjection: MediaProjection,
        samplingRate: Int,
        channelCount: Int
    ) {
        val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection).apply {
            addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            addMatchingUsage(AudioAttributes.USAGE_GAME)
            addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
        }.build()

        val audioFormat = AudioFormat.Builder().apply {
            setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            setSampleRate(samplingRate)
            setChannelMask(if (channelCount == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO)
        }.build()

        audioRecord = AudioRecord.Builder().apply {
            setAudioPlaybackCaptureConfig(playbackConfig)
            setAudioFormat(audioFormat)
        }.build()

        audioEncoder = AudioEncoder().apply {
            prepareEncoder(
                samplingRate = samplingRate,
                channelCount = channelCount
            )
        }
        audioRecordingFile = context.getExternalFilesDir(null)?.resolve("audio_track.mp4")
        mediaMuxer =
            MediaMuxer(audioRecordingFile!!.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    /**
     * Starts the audio recording process.
     * This function suspends the coroutine until it is cancelled.
     */
    suspend fun startRecord() = withContext(Dispatchers.Default) {
        val audioRecord = audioRecord ?: return@withContext
        val audioEncoder = audioEncoder ?: return@withContext
        val mediaMuxer = mediaMuxer ?: return@withContext

        try {
            audioRecord.startRecording()
            var trackIndex = -1
            audioEncoder.startAudioEncode(
                onRecordInput = { byteArray ->
                    audioRecord.read(byteArray, 0, byteArray.size)
                },
                onOutputBufferAvailable = { byteBuffer, bufferInfo ->
                    mediaMuxer.writeSampleData(trackIndex, byteBuffer, bufferInfo)
                },
                onOutputFormatAvailable = { mediaFormat ->
                    trackIndex = mediaMuxer.addTrack(mediaFormat)
                    mediaMuxer.start()
                }
            )
        } finally {
            audioRecord.stop()
            audioRecord.release()
            mediaMuxer.stop()
            mediaMuxer.release()
        }
    }
}