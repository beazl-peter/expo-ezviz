package com.poseidon

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.MediaStore
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.videogo.errorlayer.ErrorInfo
import com.videogo.openapi.EZConstants
import com.videogo.openapi.EZOpenSDK
import com.videogo.openapi.EZOpenSDKListener
import com.videogo.openapi.EZPlayer
import com.videogo.openapi.bean.EZDeviceRecordFile
import com.videogo.stream.EZDeviceStreamDownload
import com.videogo.util.VideoTransUtil
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.viewevent.EventDispatcher
import expo.modules.kotlin.views.ExpoView
import java.io.File
import java.io.IOException
import java.util.Calendar

class ExpoEzvizView(context: Context, appContext: AppContext) : ExpoView(context, appContext) {

    private val playerView = SurfaceView(context)
    var player: EZPlayer? = null
    private var downloader: EZDeviceStreamDownload? = null

    // Props
    var deviceSerial: String? = null
        set(value) {
            if (field != value) {
                field = value
                Log.d("ExpoEzvizView", "deviceSerial changed.")
                // createPlayer is now handled by onAttachedToWindow to avoid race conditions.
            }
        }
    var cameraNo: Int = 1
        set(value) {
            if (field != value) {
                field = value
                createPlayer()
            }
        }

    var verifyCode: String? = null
    var autoplay: Boolean = false
    private var hasAutoplayStarted: Boolean = false
    private var defaultSoundOn: Boolean? = null
    private var isSoundOn: Boolean = false // Android SDK defaults to sound off

    // Event Dispatchers
    val onLoad by EventDispatcher()
    val onPlayFailed by EventDispatcher()
    val onPictureCaptured by EventDispatcher()
    val onDownloadSuccess by EventDispatcher()
    val onDownloadError by EventDispatcher()
    val onPlayerMessage by EventDispatcher()
    val onPlaybackProgress by EventDispatcher()

    private val playerHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                EZConstants.EZRealPlayConstants.MSG_REALPLAY_PLAY_SUCCESS -> {
                    Log.d("ExpoEzvizView", "onPlaySuccess")
                    // Android SDK defaults to sound OFF. Only open it if requested.
                    if (defaultSoundOn == true) {
                        openSound()
                    }
                    onLoad(emptyMap())
                }
                EZConstants.EZRealPlayConstants.MSG_REALPLAY_PLAY_FAIL -> {
                    val errorInfo = msg.obj as? ErrorInfo
                    val errorMessage = errorInfo?.description ?: "Unknown error"
                    Log.e("ExpoEzvizView", "Play failed with error: $errorMessage")
                    onPlayFailed(mapOf("error" to errorMessage))
                }
                EZConstants.EZPlaybackConstants.MSG_REMOTEPLAYBACK_PLAY_SUCCUSS -> {
                    Log.d("ExpoEzvizView", "onPlaySuccess")
                    // Android SDK defaults to sound OFF. Only open it if requested.
                    if (defaultSoundOn == true) {
                        openSound()
                    }
                    onLoad(emptyMap())
                    startPlaybackTimer()
                }
                EZConstants.EZPlaybackConstants.MSG_REMOTEPLAYBACK_PLAY_FAIL -> {
                    val errorInfo = msg.obj as? ErrorInfo
                    val errorMessage = errorInfo?.description ?: "Unknown error"
                    Log.e("ExpoEzvizView", "Play failed with error: $errorMessage")
                    onPlayFailed(mapOf("error" to errorMessage))
                }

                else -> {
                     Log.d("ExpoEzvizView", "Received player message: ${msg.what}")
                    onPlayerMessage(mapOf("messageCode" to msg.what))
                }
            }
        }
    }

    private val playbackTimerHandler = Handler(Looper.getMainLooper())
    private val playbackTimerRunnable = object : Runnable {
        override fun run() {
            player?.osdTime?.let { osdTime ->
                onPlaybackProgress(mapOf("currentTime" to osdTime.timeInMillis.toDouble()))
            }
            // Poll every second
            playbackTimerHandler.postDelayed(this, 1000)
        }
    }

    private fun startPlaybackTimer() {
        stopPlaybackTimer() // Ensure no multiple timers are running
        playbackTimerHandler.post(playbackTimerRunnable)
    }

    private fun stopPlaybackTimer() {
        playbackTimerHandler.removeCallbacks(playbackTimerRunnable)
    }

    init {
        playerView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.d("ExpoEzvizView", "Surface created. Autoplay: $autoplay, HasStarted: $hasAutoplayStarted")
                player?.setSurfaceHold(holder)
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Log.d("ExpoEzvizView", "Surface destroyed.")
            }
        })
        addView(playerView)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.d("ExpoEzvizView", "onAttachedToWindow called. Creating player.")
        // It's crucial to re-create the player here, as the view instance is reused
        // when navigating back to the screen.
        createPlayer()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        playerView.layout(0, 0, width, height)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.d("ExpoEzvizView", "onDetachedFromWindow called. Stopping and destroying player.")
        stopPlaybackTimer()
        downloader?.stop()
        downloader = null
        player?.stopRealPlay()
        player?.release()
        player = null
        hasAutoplayStarted = false // Reset the autoplay flag for the next mount
    }

    // --- Public methods callable from JS ---

//    fun destroyPlayer() {
//        Log.d("ExpoEzvizView", "destroyPlayer() called")
//        player?.release()
//        player = null
//    }

    fun getDeviceDetailInfo() {
        val info = player?.deviceDetailInfo
        Log.d("ExpoEzvizView", info.toString())
    }

    fun openSound() {
        player?.openSound()
        isSoundOn = true
    }

    fun closeSound() {
        player?.closeSound()
        isSoundOn = false
    }

    fun setDefaultSoundOn(defaultSoundOn: Boolean?) {
        // If prop is null, use Android's SDK default (sound off).
        this.defaultSoundOn = defaultSoundOn
        Log.d("ExpoEzvizView", "Default sound prop set to: ${this.defaultSoundOn}")
    }

    fun startRealPlay() {
        player?.startRealPlay()
    }

    fun stopRealPlay() {
        player?.stopRealPlay()
    }

    fun capturePicture() {
        Log.d("ExpoEzvizView", "capturePicture() called")
        Handler(Looper.getMainLooper()).post {
            val bitmap = player?.capturePicture()
            if (bitmap == null) {
                Log.e("ExpoEzvizView", "capturePicture() failed, returned null bitmap.")
                onPictureCaptured(mapOf("success" to false, "error" to "Failed to capture image from player."))
                return@post
            }

            Log.d("ExpoEzvizView", "Picture captured successfully. Now saving to gallery.")
            saveBitmapToGallery(bitmap)
        }
    }

    fun startPlayback(recordFileDict: Map<String, Any>): Boolean {
        val recordFile = createDeviceRecordFile(recordFileDict)
        return player?.startPlayback(recordFile) ?: false
    }

    fun stopPlayback(): Boolean {
        stopPlaybackTimer()
        return player?.stopPlayback() ?: false
    }

    fun startLocalRecordWithFile(path: String): Boolean {
        return player?.startLocalRecordWithFile(path) ?: false
    }

    fun stopLocalRecord() {
        player?.stopLocalRecord()
    }

    fun pausePlayback(): Boolean {
        return player?.pausePlayback() ?: false
    }

    fun resumePlayback(): Boolean {
        return player?.resumePlayback() ?: false
    }

    fun seekPlayback(timestamp: Double): Boolean {
        val calendar = Calendar.getInstance()
        // JS sends timestamp in milliseconds
        calendar.timeInMillis = timestamp.toLong()
        Handler(Looper.getMainLooper()).post {
            player?.seekPlayback(calendar)
        }
        return true
    }

    fun downloadRecordFile(recordFileDict: Map<String, Any>) {
        val recordFile = createDeviceRecordFile(recordFileDict)
        val currentDeviceSerial = deviceSerial
        val currentVerifyCode = verifyCode

        if (currentDeviceSerial.isNullOrEmpty() || currentVerifyCode.isNullOrEmpty()) {
            onDownloadError(mapOf("error" to "Device serial or verify code is not set."))
            return
        }

        val downloadPath = getDownloadPath()
        if (downloadPath == null) {
            onDownloadError(mapOf("error" to "Could not create download directory."))
            return
        }

        val fileName = "${currentDeviceSerial}_${System.currentTimeMillis()}.ps"
        val fullPath = File(downloadPath, fileName).absolutePath

        Log.d("ExpoEzvizView", "Starting download to path: $fullPath")

        downloader = EZDeviceStreamDownload(fullPath, currentDeviceSerial, cameraNo, recordFile).apply {
            setStreamDownloadCallback(object : EZOpenSDKListener.EZStreamDownloadCallback {
                override fun onSuccess(path: String) {
                    val mp4FilePath = path.replace(".ps", ".mp4")
                    Log.d("ExpoEzvizView", "Download successful. Starting conversion of $path to $mp4FilePath")
                    VideoTransUtil.TransPsToMp4(path, currentVerifyCode, mp4FilePath, object: EZOpenSDKListener.EZStreamDownloadCallback{
                        override fun onSuccess(convertedPath: String) {
                            Handler(Looper.getMainLooper()).post {
                                Log.d("ExpoEzvizView", "Conversion successful. Saving to gallery.")
                                saveVideoToGallery(convertedPath)
                                // Clean up original .ps file
                                File(path).delete()
                            }
                        }

                        override fun onError(error: EZOpenSDKListener.EZStreamDownloadError?) {
                            Handler(Looper.getMainLooper()).post {
                                val errorMessage = "Conversion failed: $error"
                                Log.e("ExpoEzvizView", errorMessage)
                                onDownloadError(mapOf("error" to errorMessage))
                                // Clean up original .ps file even if conversion fails
                                File(path).delete()
                            }
                        }
                    })
                }

                override fun onError(error: EZOpenSDKListener.EZStreamDownloadError?) {
                    Handler(Looper.getMainLooper()).post {
                        val errorMessage = "Download failed: $error"
                        Log.e("ExpoEzvizView", errorMessage)
                        onDownloadError(mapOf("error" to errorMessage))
                    }
                }
            })
            start()
        }
    }

    // --- Private Helper Functions ---
    private fun createDeviceRecordFile(recordFileDict: Map<String, Any>): EZDeviceRecordFile {
        val recordFile = EZDeviceRecordFile()
        val startTimeMillis = (recordFileDict["startTime"] as? Double)?.toLong()
        val stopTimeMillis = (recordFileDict["stopTime"] as? Double)?.toLong()

        if (startTimeMillis != null) {
            val startCal = Calendar.getInstance()
            startCal.timeInMillis = startTimeMillis
            recordFile.startTime = startCal
        }
        if (stopTimeMillis != null) {
            val stopCal = Calendar.getInstance()
            stopCal.timeInMillis = stopTimeMillis
            recordFile.stopTime = stopCal
        }
        return recordFile
    }

    private fun getDownloadPath(): String? {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (dir != null && (dir.exists() || dir.mkdirs())) {
            return dir.absolutePath
        }
        return null
    }

    fun createPlayer() {
        Log.d("ExpoEzvizView", "createPlayer() called")
        val currentDeviceSerial = deviceSerial
        if (currentDeviceSerial.isNullOrEmpty()) {
            Log.d("ExpoEzvizView", "createPlayer() aborted, deviceSerial is nil or empty.")
            return
        }

        Handler(Looper.getMainLooper()).post {
            player?.release()
            Log.d("ExpoEzvizView", "Executing player creation on main thread.")
            player = EZOpenSDK.getInstance().createPlayer(currentDeviceSerial, cameraNo)
            Log.d("ExpoEzvizView", "Player instance created.")

            player?.setHandler(playerHandler)

            val currentVerifyCode = verifyCode
            if (!currentVerifyCode.isNullOrEmpty()) {
                player?.setPlayVerifyCode(currentVerifyCode)
                Log.d("ExpoEzvizView", "Verify code set.")
            }

            player?.setSurfaceHold(playerView.holder)
            Log.d("ExpoEzvizView", "Player delegate and surface holder set.")

            // Handle autoplay here to avoid race conditions.
            if (autoplay && !hasAutoplayStarted) {
                Log.d("ExpoEzvizView", "Autoplay is enabled, starting real play.")
                startRealPlay()
                hasAutoplayStarted = true
            }
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val fileName = "IMG_${System.currentTimeMillis()}.jpg"
        var imageUri: android.net.Uri? = null

        try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IOException("Failed to create new MediaStore record.")

            resolver.openOutputStream(imageUri)?.use { fos ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)) {
                    throw IOException("Failed to save bitmap.")
                }
            } ?: throw IOException("Failed to get output stream.")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }

            Log.d("ExpoEzvizView", "Image saved successfully to gallery.")
            onPictureCaptured(mapOf("success" to true))

        } catch (e: Exception) {
            if (imageUri != null) {
                context.contentResolver.delete(imageUri, null, null)
            }
            Log.e("ExpoEzvizView", "Save error: ${e.localizedMessage}")
            onPictureCaptured(mapOf("success" to false, "error" to "Failed to save image: ${e.localizedMessage}"))
        }
    }

    private fun saveVideoToGallery(videoPath: String) {
        val file = File(videoPath)
        if (!file.exists()) {
            onDownloadError(mapOf("error" to "Converted MP4 file not found.", "path" to videoPath))
            return
        }

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.TITLE, file.name)
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)

        try {
            uri?.let { uriValue ->
                resolver.openOutputStream(uriValue)?.use { out ->
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(out)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uriValue, values, null, null)
                }
                Log.d("ExpoEzvizView", "Video saved to photo album successfully.")
                onDownloadSuccess(mapOf("path" to uriValue.toString(), "savedToAlbum" to true))
                file.delete() // Clean up the temporary MP4 file
            } ?: throw IOException("Failed to create new MediaStore record.")
        } catch (e: Exception) {
            Log.e("ExpoEzvizView", "Failed to save video to photo album. Error: ${e.localizedMessage}")
            onDownloadError(mapOf("error" to "Failed to save video to photo album.", "path" to videoPath))
        }
    }
}
