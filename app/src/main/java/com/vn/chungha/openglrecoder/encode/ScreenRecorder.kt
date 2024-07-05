package com.vn.chungha.openglrecoder.encode

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import com.vn.chungha.openglrecoder.ui.opengl_core.InputSurface
import com.vn.chungha.openglrecoder.ui.opengl_core.TextureRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import java.io.File

/**
 * This class is responsible for recording the screen.
 * It uses the MediaProjection and MediaRecorder classes to capture screen and audio.
 */

class ScreenRecorder(
    private val context: Context,
    private val resultCode: Int,
    private val resultData: Intent
) {
    private val mediaProjectionManager by lazy { context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private var recordingJob: Job? = null
    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var videoRecordingFile: File? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var internalAudioRecorder: InternalAudioRecorder? = null
    private var inputOpenGlSurface: InputSurface? = null
    private var isDrawAltImage = false

    /**
     * Starts the screen recording process.
     */
    fun startRecord() {
        recordingJob = scope.launch {
            mediaRecorder =
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()).apply {
                    setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    setVideoEncodingBitRate(6_000_000)
                    setVideoFrameRate(60)
                    setVideoSize(VIDEO_WIDTH, VIDEO_HEIGHT)

                    videoRecordingFile =
                        context.getExternalFilesDir(null)?.resolve("video_track.mp4")
                    setOutputFile(videoRecordingFile!!.path)
                    prepare()
                }

            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)

            withContext(Dispatchers.Main) {
                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onCapturedContentResize(width: Int, height: Int) {
                        super.onCapturedContentResize(width, height)
                    }

                    override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
                        super.onCapturedContentVisibilityChanged(isVisible)
                        isDrawAltImage = !isVisible
                    }

                    override fun onStop() {
                        super.onStop()
                        // MediaProjection
                        // do nothing
                    }
                }, null)
            }

            withContext(openGlRelatedDispatcher) {
                inputOpenGlSurface = InputSurface(mediaRecorder?.surface!!, TextureRenderer())
                inputOpenGlSurface?.makeCurrent()
                inputOpenGlSurface?.createRender(VIDEO_WIDTH, VIDEO_HEIGHT)

                inputOpenGlSurface?.setAltImageTexture(createAltImage())
            }

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenRecorder",
                VIDEO_WIDTH,
                VIDEO_HEIGHT,
                context.resources.configuration.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputOpenGlSurface?.drawSurface,
                null,
                null
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                internalAudioRecorder = InternalAudioRecorder().apply {
                    prepareRecorder(
                        context,
                        mediaProjection!!,
                        AUDIO_SAMPLING_RATE,
                        AUDIO_CHANNEL_COUNT
                    )
                }
            }


            mediaRecorder?.start()

            listOf(
                launch(openGlRelatedDispatcher) {
                    // OpenGL
                    while (isActive) {
                        try {
                            if (isDrawAltImage) {
                                inputOpenGlSurface?.drawAltImage()
                                inputOpenGlSurface?.swapBuffers()
                                delay(16) // 60fp 16
                            } else {

                                val isNewFrameAvailable =
                                    inputOpenGlSurface?.awaitIsNewFrameAvailable()

                                if (isNewFrameAvailable == true) {
                                    inputOpenGlSurface?.updateTexImage()
                                    inputOpenGlSurface?.drawImage()
                                    inputOpenGlSurface?.swapBuffers()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                },
                launch {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        internalAudioRecorder?.startRecord()
                    }
                }
            ).joinAll()
        }
    }

    /**
     * Stops the screen recording process.
     */
    suspend fun stopRecord() = withContext(Dispatchers.IO) {
        recordingJob?.cancelAndJoin()
        mediaRecorder?.stop()
        mediaRecorder?.release()
        mediaProjection?.stop()
        virtualDisplay?.release()

        //mp4
        val resultFile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getExternalFilesDir(null)?.resolve("mix_track.mp4")!!.also { mixFile ->
                MediaMuxerTool.mixAvTrack(
                    audioTrackFile = internalAudioRecorder?.audioRecordingFile!!,
                    videoTrackFile = videoRecordingFile!!,
                    resultFile = mixFile
                )
            }
        } else {
            videoRecordingFile!!
        }


        MediaStoreTool.copyToVideoFolder(
            context = context,
            file = resultFile,
            fileName = "AndroidPartialScreenInternalAudioRecorder_${System.currentTimeMillis()}.mp4"
        )


        videoRecordingFile!!.delete()
        resultFile.delete()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            internalAudioRecorder?.audioRecordingFile!!.delete()
        }
    }

    /**
     * Creates an alternative image to be displayed when the specified single application moves off the screen during single application screen recording.
     *
     * @return The created Bitmap image.
     */
    private fun createAltImage(): Bitmap =
        Bitmap.createBitmap(VIDEO_WIDTH, VIDEO_HEIGHT, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 50f
        }

        canvas.drawColor(Color.BLACK)
        canvas.drawText("Test", 100f, 100f, paint)
        canvas.drawText("Test1。", 100f, 200f, paint)
        }

    companion object {

        /**
         * OpenGL identifies context by thread, so OpenGL related calls are made from this openGlRelatedDispatcher.
         * In other words, OpenGL functions cannot be called from threads other than the one that called makeCurrent.
         * (Only the thread that called makeCurrent can do swapBuffers, etc.).
         *
         * By creating a custom Dispatcher, you can specify the thread to process.
         */
        @OptIn(DelicateCoroutinesApi::class)
        private val openGlRelatedDispatcher = newSingleThreadContext("OpenGLContextRelatedThread")

        private const val VIDEO_WIDTH = 1280
        private const val VIDEO_HEIGHT = 720
        private const val AUDIO_SAMPLING_RATE = 44_100
        private const val AUDIO_CHANNEL_COUNT = 2
    }
}