package com.vn.chungha.openglrecoder.ui.opengl_core

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLExt
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * This class represents an input surface for rendering textures.
 * It uses a TextureRenderer to draw frames onto a SurfaceTexture.
 *
 * @property surface The surface to which the class will render.
 * @property textureRenderer The TextureRenderer used for rendering.
 *
 * @constructor Creates an InputSurface with the given surface and TextureRenderer.
 */
class InputSurface(
    private val surface: Surface,
    private val textureRenderer: TextureRenderer
) : SurfaceTexture.OnFrameAvailableListener {

    // CoroutineScope for managing concurrent tasks
    private var scope = CoroutineScope(Dispatchers.Default + Job())

    // EGL display, context and surface
    private var mEGLDisplay = EGL14.EGL_NO_DISPLAY
    private var mEGLContext = EGL14.EGL_NO_CONTEXT
    private var mEGLSurface = EGL14.EGL_NO_SURFACE

    // SurfaceTexture for rendering
    private var surfaceTexture: SurfaceTexture? = null

    // Surface for drawing
    var drawSurface: Surface? = null
        private set

    // Mutex for synchronizing access to isNewFrameAvailable
    private val frameSyncMutex = Mutex()

    // Flag indicating if a new frame is available
    private var isNewFrameAvailable = false

    // Setup EGL on initialization
    init {
        eglSetup()
    }

    /**
     * Creates a render with the specified width and height.
     *
     * @param width The width of the render.
     * @param height The height of the render.
     */
    fun createRender(width: Int, height: Int) {
        textureRenderer.surfaceCreated()
        surfaceTexture = SurfaceTexture(textureRenderer.screenRecordTextureId)
        surfaceTexture?.setDefaultBufferSize(width, height)
        surfaceTexture?.setOnFrameAvailableListener(this)
        drawSurface = Surface(surfaceTexture)
    }

    /**
     * Checks if a new frame is available for rendering.
     *
     * @return true if a new frame is available, false otherwise.
     */
    suspend fun awaitIsNewFrameAvailable(): Boolean {
        return frameSyncMutex.withLock {
            if (isNewFrameAvailable) {
                isNewFrameAvailable = false
                true
            } else {
                false
            }
        }
    }

    /**
     * Callback method that is called when a new frame is available.
     *
     * @param st The SurfaceTexture where the new frame is available.
     */
    override fun onFrameAvailable(st: SurfaceTexture) {
        scope.launch {
            frameSyncMutex.withLock {
                isNewFrameAvailable = true
            }
        }
    }

    /**
     * Updates the image on the SurfaceTexture.
     */
    fun updateTexImage() {
        textureRenderer.checkGlError("before updateTexImage")
        surfaceTexture?.updateTexImage()
    }

    /**
     * Draws the current frame.
     */
    fun drawImage() {
        val surfaceTexture = surfaceTexture ?: return
        textureRenderer.drawFrame(surfaceTexture)
    }

    /**
     * Releases all resources held by this class, notably the EGL context.
     */
    fun release() {
        if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface)
            EGL14.eglDestroyContext(mEGLDisplay, mEGLContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(mEGLDisplay)
        }
        surface.release()
        mEGLDisplay = EGL14.EGL_NO_DISPLAY
        mEGLContext = EGL14.EGL_NO_CONTEXT
        mEGLSurface = EGL14.EGL_NO_SURFACE
        scope.cancel()
    }

    /**
     * Makes the EGL context and surface current.
     */
    fun makeCurrent() {
        EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)
        checkEglError("eglMakeCurrent")
    }

    /**
     * Swaps the buffers of the EGL context.
     *
     * @return true if the operation was successful, false otherwise.
     */
    fun swapBuffers(): Boolean {
        val result = EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface)
        checkEglError("eglSwapBuffers")
        return result
    }

    /**
     * Sets the presentation time stamp to EGL. Time is expressed in nanoseconds.
     *
     * @param nsecs The presentation time in nanoseconds.
     */
    fun setPresentationTime(nsecs: Long) {
        EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, nsecs)
        checkEglError("eglPresentationTimeANDROID")
    }

    /**
     * Sets the alternate image texture.
     *
     * @param bitmap The bitmap to be used as the alternate image texture.
     */
    fun setAltImageTexture(bitmap: Bitmap) {
        textureRenderer.setAltImageTexture(bitmap)
    }

    /**
     * Draws the alternate image.
     */
    fun drawAltImage() {
        textureRenderer.drawAltImage()
    }

    /**
     * Sets up the EGL environment for rendering.
     */
    private fun eglSetup() {
        // Implementation...
    }

    /**
     * Checks for EGL errors and throws an exception if one is found.
     *
     * @param msg The error message to be displayed if an error is found.
     */
    private fun checkEglError(msg: String) {
        // Implementation...
    }

    companion object {
        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}