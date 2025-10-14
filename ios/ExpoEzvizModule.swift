import ExpoModulesCore
import EZOpenSDKFramework

public class ExpoEzvizModule: Module {
  // Each module class must implement the definition function. The definition consists of components
  // that describes the module's functionality and behavior.
  // See https://docs.expo.dev/modules/module-api for more details about available components.
  public func definition() -> ModuleDefinition {
    // Sets the name of the module that JavaScript code will use to refer to the module. Takes a string as an argument.
    // Can be inferred from module's class name, but it's recommended to set it explicitly for clarity.
    Name("ExpoEzviz")


    // Defines event names that the module can send to JavaScript.
    Events("onLoad")

    AsyncFunction("initSDK") { (appKey: String, apiUrl: String?) in
      if let url = apiUrl, !url.isEmpty {
        // set debug mode first
        EZOpenSDK.setDebugLogEnable(true)
        EZOpenSDK.initLib(withAppKey: appKey, url: url, authUrl: "")
      } else {
        EZOpenSDK.initLib(withAppKey: appKey)
      }
      print("EZOpenSDK version: \(EZOpenSDK.getVersion())")
    }

    AsyncFunction("createPlayer") { (view: ExpoEzvizView) in
        view.createPlayer()
    }

    AsyncFunction("setAccessToken") { (accessToken: String) in
      print("EzvizModule: Setting Access Token")
      EZOpenSDK.setAccessToken(accessToken)
    }

    AsyncFunction("capturePicture") { (viewTag: Int) in
      // We must dispatch to the main thread to find the view
      DispatchQueue.main.async {
        guard let view = self.appContext?.findView(withTag: viewTag, ofType: ExpoEzvizView.self) else {
          print("EzvizModule: Could not find view with tag \(viewTag)")
          return
        }
        view.capturePicture()
      }
    }


    AsyncFunction("searchRecordFileFromDevice") { (deviceSerial: String, cameraNo: Int, beginTimeString: String, endTimeString: String, promise: Promise) in
      let dateFormatter = DateFormatter()
      dateFormatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
      dateFormatter.timeZone = TimeZone(abbreviation: "UTC")
      guard let beginTime = dateFormatter.date(from: beginTimeString),
            let endTime = dateFormatter.date(from: endTimeString) else {
          promise.reject("date-format-error", "Could not parse date strings. Expected format: yyyy-MM-dd HH:mm:ss")
          return
      }

      let timeInterval = endTime.timeIntervalSince(beginTime)
      if timeInterval > 24 * 60 * 60 {
          promise.reject("query-window-error", "The time window for querying records cannot exceed 24 hours.")
          return
      }

      EZOpenSDK.searchRecordFile(fromDevice: deviceSerial, cameraNo: cameraNo, beginTime: beginTime, endTime: endTime) { deviceRecords, error in
        if let error = error {
          promise.reject("search-error", error.localizedDescription)
          return
        }
 
        let serializableRecords = deviceRecords.map { record -> [String: Any?] in
            guard let record = record as? EZDeviceRecordFile else { return [:] }
            return [
              "startTime": record.startTime.timeIntervalSince1970 * 1000, // JS uses milliseconds
              "stopTime": record.stopTime.timeIntervalSince1970 * 1000
            ]
        }
        promise.resolve(serializableRecords)
      }
    }

    AsyncFunction("startPlaybackFromDevice") { (viewTag: Int, recordFileDict: [String: Any], promise: Promise) in
      DispatchQueue.main.async {
        guard let view = self.appContext?.findView(withTag: viewTag, ofType: ExpoEzvizView.self) else {
          promise.reject("view-not-found", "Could not find view with tag \(viewTag)")
          return
        }

        let success = view.startPlayback(from: recordFileDict)
        promise.resolve(success)
      }
    }

    AsyncFunction("startLocalRecord") { (viewTag: Int, path: String, promise: Promise) in
      DispatchQueue.main.async {
        guard let view = self.appContext?.findView(withTag: viewTag, ofType: ExpoEzvizView.self) else {
          promise.reject("view-not-found", "Could not find view with tag \(viewTag)")
          return
        }

        let success = view.startLocalRecord(with: path)
        promise.resolve(success)
      }
    }

    AsyncFunction("downloadRecordFile") { (viewTag: Int, recordFileDict: [String: Any]) in
        DispatchQueue.main.async {
            guard let view = self.appContext?.findView(withTag: viewTag, ofType: ExpoEzvizView.self) else {
                return
            }
            view.downloadRecordFile(from: recordFileDict)
        }
    }

    AsyncFunction("openSound") { (viewTag: Int, promise: Promise) in
        DispatchQueue.main.async {
            guard let view = self.appContext?.findView(withTag: viewTag, ofType: ExpoEzvizView.self) else {
                promise.reject("view-not-found", "Could not find view with tag \(viewTag)")
                return
            }
            view.openSound()
            promise.resolve(true)
        }
    }

    AsyncFunction("closeSound") { (viewTag: Int, promise: Promise) in
        DispatchQueue.main.async {
            guard let view = self.appContext?.findView(withTag: viewTag, ofType: ExpoEzvizView.self) else {
                promise.reject("view-not-found", "Could not find view with tag \(viewTag)")
                return
            }
            view.closeSound()
            promise.resolve(true)
        }
    }

    AsyncFunction("startRealPlay") { (viewTag: Int) in
        DispatchQueue.main.async {
            guard let view = self.appContext?.findView(withTag: viewTag, ofType: ExpoEzvizView.self) else {
                // It's better to reject the promise if the view is not found
                return
            }
            view.startRealPlay()
        }
    }

    AsyncFunction("stopRealPlay") { (viewTag: Int) in
        DispatchQueue.main.async {
            guard let view = self.appContext?.findView(withTag: viewTag, ofType: ExpoEzvizView.self) else {
                // It's better to reject the promise if the view is not found
                return
            }
            view.stopRealPlay()
        }
    }

    AsyncFunction("addDevice") { (deviceSerial: String, verifyCode: String, promise: Promise) in
      EZOpenSDK.addDevice(deviceSerial, verifyCode: verifyCode) { error in
        if let error = error {
          promise.reject("add-device-error", error.localizedDescription)
        } else {
          promise.resolve(nil)
        }
      }
    }

    AsyncFunction("deleteDevice") { (deviceSerial: String, promise: Promise) in
      EZOpenSDK.deleteDevice(deviceSerial) { error in
        if let error = error {
          promise.reject("delete-device-error", error.localizedDescription)
        } else {
          promise.resolve(nil)
        }
      }
    }

    AsyncFunction("setDeviceName") { (deviceName: String, deviceSerial: String, promise: Promise) in
      EZOpenSDK.setDeviceName(deviceName, deviceSerial: deviceSerial) { error in
        if let error = error {
          promise.reject("set-device-name-error", error.localizedDescription)
        } else {
          promise.resolve(nil)
        }
      }
    }

    AsyncFunction("setDeviceEncryptStatus") { (deviceSerial: String, verifyCode: String, encryptStatus: Bool, promise: Promise) in
      EZOpenSDK.setDeviceEncryptStatus(deviceSerial, verifyCode: verifyCode, encrypt: encryptStatus) { error in
        if let error = error {
          promise.reject("set-encrypt-status-error", error.localizedDescription)
        } else {
          promise.resolve(nil)
        }
      }
    }

    AsyncFunction("setDefence") { (deviceSerial: String, defenceType: Int, promise: Promise) in
      guard let type = EZDefenceStatus(rawValue: defenceType) else {
        promise.reject("invalid-defence-type", "Invalid defence type. Use 0 (Off/Sleep), 1 (On), 8 (At Home), or 16 (Outer).")
        return
      }

      EZOpenSDK.setDefence(type, deviceSerial: deviceSerial) { error in
        if let error = error {
          promise.reject("set-defence-error", error.localizedDescription)
        } else {
          promise.resolve(nil)
        }
      }
    }

    AsyncFunction("getDeviceInfo") { (deviceSerial: String, promise: Promise) in
      EZOpenSDK.getDeviceInfo(deviceSerial) { deviceInfo, error in
        if let error = error {
          promise.reject("get-device-info-error", error.localizedDescription)
          return
        }

        // Manually create a dictionary with the desired properties to avoid KVC crashes.
        // Some of the elements are swallowed by the EZViz SDK.
        let deviceInfoDict: [String: Any?] = [
            // "addTime": deviceInfo.addTime,
            // "alarmSoundMode": deviceInfo.alarmSoundMode,
            "cameraNum": deviceInfo.cameraNum,
            "category": deviceInfo.category,
            "defence": deviceInfo.defence,
            "detectorNum": deviceInfo.detectorNum,
            "deviceCover": deviceInfo.deviceCover,
            "deviceName": deviceInfo.deviceName,
            "deviceSerial": deviceInfo.deviceSerial,
            "deviceType": deviceInfo.deviceType,
            "deviceVersion": deviceInfo.deviceVersion,
            "isEncrypt": deviceInfo.isEncrypt,
            // "offlineNotify": deviceInfo.offlineNotify,
            "status": deviceInfo.status,
            // "supportExtShort": deviceInfo.supportExtShort
        ]
        promise.resolve(deviceInfoDict)
      }
    }

    AsyncFunction("destoryPlayer") { (view: ExpoEzvizView) in
        // note typo from EZOpenSDK
        view.destoryPlayer()
    }


    // Enables the module to be used as a native view. Definition components that are accepted as part of the
    // view definition: Prop, Events.
    View(ExpoEzvizView.self) {
      // Defines a setter for the `url` prop.
      Prop("deviceSerial") { (view: ExpoEzvizView, prop: String?) in view.deviceSerial = prop }
      Prop("cameraNo") { (view: ExpoEzvizView, prop: Int) in view.cameraNo = prop }
      Prop("accessToken") { (view: ExpoEzvizView, prop: String?) in view.accessToken = prop }
      Prop("verifyCode") { (view: ExpoEzvizView, prop: String?) in view.verifyCode = prop }

      Events("onLoad", "onPlayFailed", "onPictureCaptured", "onDownloadProgress", "onDownloadSuccess", "onDownloadError")
    }
  }

}
