package com.poseidon

import android.os.Build
import androidx.annotation.RequiresApi
import android.util.Log
import com.videogo.exception.BaseException
import com.videogo.openapi.EZConstants
import com.videogo.openapi.EZGlobalSDK
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.GregorianCalendar

class ExpoDeviceInfo : Record {
    @Field var cameraNum: Int = 0
    @Field var category: String? = null
    @Field var defence: Int = 0
    @Field var detectorNum: Int = 0
    @Field var deviceCover: String? = null
    @Field var deviceName: String? = null
    @Field var deviceSerial: String? = null
    @Field var deviceType: String? = null
    @Field var deviceVersion: String? = null
    @Field var isEncrypt: Boolean = false
    @Field var status: Int = 0
}

class ExpoDeviceRecordFile : Record {
    @Field var startTime: Long = 0
    @Field var stopTime: Long = 0
}

class ExpoEzvizModule : Module() {
  @RequiresApi(Build.VERSION_CODES.O)
  override fun definition() = ModuleDefinition {
    Name("ExpoEzviz")

    AsyncFunction("initSDK") { appKey: String, apiUrl: String? ->
      val application = appContext.activityProvider?.currentActivity?.application
        ?: throw IllegalStateException("Application context is not available.")

      EZGlobalSDK.showSDKLog(true)
      EZGlobalSDK.setDebugStreamEnable(true)
      EZGlobalSDK.enableP2P(false)
      EZGlobalSDK.initLib(application, appKey)

      if (apiUrl != null && apiUrl.isNotEmpty()) {
          EZGlobalSDK.getInstance().setServerUrl(apiUrl, "")
      }
    }

    AsyncFunction("setAccessToken") { accessToken: String ->
      print("EzvizModule: Setting Access Token")
      EZGlobalSDK.getInstance().setAccessToken(accessToken)
    }

    AsyncFunction("searchRecordFileFromDevice") { deviceSerial: String, cameraNo: Int, beginTimeString: String, endTimeString: String, promise: Promise ->
      val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

      val beginTime = try { ZonedDateTime.parse(beginTimeString, formatter) } catch (e: Exception) { null }
      val endTime = try { ZonedDateTime.parse(endTimeString, formatter) } catch (e: Exception) { null }

      if (beginTime == null || endTime == null) {
        promise.reject("date-format-error", "Could not parse date strings. Expected format: yyyy-MM-dd HH:mm:ss", null)
        return@AsyncFunction
      }

      val timeIntervalSeconds = Duration.between(beginTime, endTime).seconds
      if (timeIntervalSeconds > 24 * 60 * 60) {
        promise.reject("query-window-error", "The time window for querying records cannot exceed 24 hours.", null)
        return@AsyncFunction
      }

      val beginCalendar = GregorianCalendar.from(beginTime)
      val endCalendar = GregorianCalendar.from(endTime)

      Thread {
        try {
          val serializableRecords = EZGlobalSDK.getInstance()
            .searchRecordFileFromDevice(deviceSerial, cameraNo, beginCalendar, endCalendar)
            .map { record ->
                ExpoDeviceRecordFile().apply {
                    this.startTime = record.startTime?.timeInMillis ?: 0
                    this.stopTime = record.stopTime?.timeInMillis ?: 0
                }
            }
          promise.resolve(serializableRecords)
        } catch (e: BaseException) {
          promise.reject("search-error", e.localizedMessage, e)
        }
      }.start()
    }

    AsyncFunction("addDevice") { deviceSerial: String, verifyCode: String, promise: Promise ->
      Thread {
        try {
          EZGlobalSDK.getInstance().addDevice(deviceSerial, verifyCode)
          promise.resolve(null)
        } catch (e: BaseException) {
          promise.reject("add-device-error", e.localizedMessage, e)
        }
      }.start()
    }

    AsyncFunction("deleteDevice") { deviceSerial: String, promise: Promise ->
      Thread {
        try {
          EZGlobalSDK.getInstance().deleteDevice(deviceSerial)
          promise.resolve(null)
        } catch (e: BaseException) {
          promise.reject("delete-device-error", e.localizedMessage, e)
        }
      }.start()
    }

    AsyncFunction("setDeviceName") { deviceName: String, deviceSerial: String, promise: Promise ->
      Thread {
        try {
          EZGlobalSDK.getInstance().setDeviceName(deviceSerial, deviceName)
          promise.resolve(null)
        } catch (e: BaseException) {
          promise.reject("set-device-name-error", e.localizedMessage, e)
        }
      }.start()
    }

    AsyncFunction("setDeviceEncryptStatus") { deviceSerial: String, verifyCode: String, encryptStatus: Boolean, promise: Promise ->
      Thread {
        try {
          EZGlobalSDK.getInstance().setDeviceEncryptStatus(deviceSerial, verifyCode, encryptStatus)
          promise.resolve(null)
        } catch (e: BaseException) {
          promise.reject("set-encrypt-status-error", e.localizedMessage, e)
        }
      }.start()
    }

    AsyncFunction("setDefence") { deviceSerial: String, defenceType: Int, promise: Promise ->
      val defenceStatus = when (defenceType) {
        0 -> EZConstants.EZDefenceStatus.EZDefence_IPC_CLOSE
        1 -> EZConstants.EZDefenceStatus.EZDefence_IPC_OPEN
        8 -> EZConstants.EZDefenceStatus.EZDefence_ALARMHOST_ATHOME
        16 -> EZConstants.EZDefenceStatus.EZDefence_ALARMHOST_OUTER
        else -> null
      }

      if (defenceStatus == null) {
        promise.reject("invalid-defence-type", "Invalid defence type. Use 0 (Off/Sleep), 1 (On), 8 (At Home), or 16 (Outer).", null)
        return@AsyncFunction
      }

      Thread {
        try {
          EZGlobalSDK.getInstance().setDefence(deviceSerial, defenceStatus)
          promise.resolve(null)
        } catch (e: BaseException) {
          promise.reject("set-defence-error", e.localizedMessage, e)
        }
      }.start()
    }

    AsyncFunction("getDeviceInfo") { deviceSerial: String, promise: Promise ->
      Thread {
        try {
          val deviceInfo = EZGlobalSDK.getInstance().getDeviceInfo(deviceSerial)
          Log.d("ExpoEzvizModule", "deviceInfo: $deviceInfo")
          val expoDeviceInfo = ExpoDeviceInfo().apply {
            this.cameraNum = deviceInfo.cameraNum
            this.category = deviceInfo.category
            this.defence = deviceInfo.defence
            this.detectorNum = deviceInfo.detectorNum
            this.deviceCover = deviceInfo.deviceCover
            this.deviceName = deviceInfo.deviceName
            this.deviceSerial = deviceInfo.deviceSerial
            this.deviceType = deviceInfo.deviceType
            this.deviceVersion = deviceInfo.deviceVersion
            this.isEncrypt = deviceInfo.isEncrypt == 1
            this.status = deviceInfo.status
          }
          promise.resolve(expoDeviceInfo)
        } catch (e: BaseException) {
          promise.reject("get-device-info-error", e.localizedMessage, e)
        }
      }.start()
    }

    View(ExpoEzvizView::class) {
      Prop("deviceSerial") { view: ExpoEzvizView, deviceSerial: String? ->
        view.deviceSerial = deviceSerial
      }

      Prop("cameraNo") { view: ExpoEzvizView, cameraNo: Int ->
        view.cameraNo = cameraNo
      }

      Prop("verifyCode") { view: ExpoEzvizView, verifyCode: String? ->
        view.verifyCode = verifyCode
      }

      Prop("autoplay") { view: ExpoEzvizView, autoplay: Boolean? ->
        view.autoplay = autoplay ?: false
      }

      Prop("defaultSoundOn") { view: ExpoEzvizView, defaultSoundOn: Boolean? ->
        view.setDefaultSoundOn(defaultSoundOn)
      }

      Events("onLoad", "onPlayFailed", "onPictureCaptured", "onDownloadProgress", "onDownloadSuccess", "onDownloadError", "onPlayerMessage", "onPlaybackProgress")

      AsyncFunction("capturePicture") { view: ExpoEzvizView ->
        view.capturePicture()
      }

      AsyncFunction("startPlayback") { view: ExpoEzvizView, recordFileDict: Map<String, Any> ->
        return@AsyncFunction view.startPlayback(recordFileDict)
      }

      AsyncFunction("stopPlayback") { view: ExpoEzvizView ->
        return@AsyncFunction view.stopPlayback()
      }

      AsyncFunction("stopLocalRecord") { view: ExpoEzvizView ->
        view.stopLocalRecord()
      }

      AsyncFunction("downloadRecordFile") { view: ExpoEzvizView, recordFileDict: Map<String, Any> ->
        view.downloadRecordFile(recordFileDict)
      }

      AsyncFunction("openSound") { view: ExpoEzvizView ->
        view.openSound()
      }

      AsyncFunction("closeSound") { view: ExpoEzvizView ->
        view.closeSound()
      }

      AsyncFunction("startRealPlay") { view: ExpoEzvizView ->
        view.startRealPlay()
      }

      AsyncFunction("stopRealPlay") { view: ExpoEzvizView ->
        view.stopRealPlay()
      }

      AsyncFunction("pausePlayback") { view: ExpoEzvizView ->
        return@AsyncFunction view.pausePlayback()
      }

      AsyncFunction("resumePlayback") { view: ExpoEzvizView ->
        return@AsyncFunction view.resumePlayback()
      }

      AsyncFunction("seekPlayback") { view: ExpoEzvizView, offsetTimestamp: Double ->
        return@AsyncFunction view.seekPlayback(offsetTimestamp)
      }

      // Renamed to match iOS for a unified API
      AsyncFunction("startLocalRecord") { view: ExpoEzvizView, path: String ->
        return@AsyncFunction view.startLocalRecordWithFile(path)
      }
    }
  }
}
