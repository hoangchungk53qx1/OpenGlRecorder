package com.vn.chungha.openglrecoder.encode

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * This class is responsible for encoding audio data.
 * It uses the MediaCodec class to make the encoding process easier.
 * It receives raw internal audio (PCM) and encodes it to AAC / Opus.
 */
class AudioEncoder {

    /** MediaCodec encoder instance */
    private var mediaCodec: MediaCodec? = null

    /**
     * Initializes the encoder with the provided parameters.
     *
     * @param samplingRate The sampling rate for the audio data. Default is 44,100 Hz.
     * @param channelCount The number of audio channels. Default is 2 (stereo).
     * @param bitRate The bit rate for the audio data. Default is 192,000 bps.
     */
    fun prepareEncoder(
        samplingRate: Int = 44_100,
        channelCount: Int = 2,
        bitRate: Int = 192_000,
    ) {
        val codec = MediaFormat.MIMETYPE_AUDIO_AAC
        val audioEncodeFormat =
            MediaFormat.createAudioFormat(codec, samplingRate, channelCount).apply {
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC
                )
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            }
        mediaCodec = MediaCodec.createEncoderByType(codec).apply {
            configure(audioEncodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
    }

    /**
     * Starts the audio encoding process.
     * This function suspends the coroutine until it is cancelled.
     *
     * @param onRecordInput A function that takes a ByteArray, fills it with audio data, and returns the size of the data.
     * @param onOutputBufferAvailable A function that is called when encoded data is available. It receives a ByteBuffer and a MediaCodec.BufferInfo.
     * @param onOutputFormatAvailable A function that is called when the output format is available after encoding. It receives a MediaFormat.
     */
    suspend fun startAudioEncode(
        onRecordInput: suspend (ByteArray) -> Int,
        onOutputBufferAvailable: suspend (ByteBuffer, MediaCodec.BufferInfo) -> Unit,
        onOutputFormatAvailable: suspend (MediaFormat) -> Unit,
    ) = withContext(Dispatchers.Default) {
        val bufferInfo = MediaCodec.BufferInfo()
        mediaCodec!!.start()

        try {
            while (isActive) {
                val inputBufferId = mediaCodec!!.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferId >= 0) {
                    val inputBuffer = mediaCodec!!.getInputBuffer(inputBufferId)!!
                    val capacity = inputBuffer.capacity()
                    val byteArray = ByteArray(capacity)
                    val readByteSize = onRecordInput(byteArray)
                    if (readByteSize > 0) {
                        inputBuffer.put(byteArray, 0, readByteSize)
                        mediaCodec!!.queueInputBuffer(
                            inputBufferId,
                            0,
                            readByteSize,
                            System.nanoTime() / 1000,
                            0
                        )
                    }
                }

                val outputBufferId = mediaCodec!!.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

                if (outputBufferId >= 0) {
                    val outputBuffer = mediaCodec!!.getOutputBuffer(outputBufferId)!!
                    if (bufferInfo.size > 1) {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            onOutputBufferAvailable(outputBuffer, bufferInfo)
                        }
                    }
                    mediaCodec!!.releaseOutputBuffer(outputBufferId, false)
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // MediaFormat、MediaMuxer
                    onOutputFormatAvailable(mediaCodec!!.outputFormat)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaCodec?.stop()
            mediaCodec?.release()
        }
    }

    companion object {

        /** Timeout for MediaCodec operations */
        private const val TIMEOUT_US = 10_000L

    }
}