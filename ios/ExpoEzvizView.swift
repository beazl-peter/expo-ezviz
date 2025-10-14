import ExpoModulesCore
import EZOpenSDKFramework
import Photos

// This view will be used as a native component. Make sure to inherit from `ExpoView`
// to apply the proper styling (e.g. border radius and shadows).
class ExpoEzvizView: ExpoView {
  var player: EZPlayer? = nil
  var playerView = UIView()

  var deviceSerial: String? {
    didSet {
      print("ExpoEzvizView: deviceSerial changed.")
      createPlayer()
    }
  }
  var cameraNo: Int = 1 {
    didSet {
      createPlayer()
    }
  }

  var verifyCode: String? // This can be a prop too if needed
  var accessToken: String?
  var downloadTask: EZDeviceRecordDownloadTask?

  var defaultSoundOn: Bool = true
  private var isSoundOn: Bool = true

  let onLoad = EventDispatcher()
  let onPlayFailed = EventDispatcher()
  let onPictureCaptured = EventDispatcher()
  let onDownloadProgress = EventDispatcher()
  let onDownloadSuccess = EventDispatcher()
  let onDownloadError = EventDispatcher()

  required init(appContext: AppContext? = nil) {
    super.init(appContext: appContext)
    clipsToBounds = true
    addSubview(playerView)
    self.isSoundOn = self.defaultSoundOn
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    playerView.frame = bounds
  }

  override func removeFromSuperview() {
    print("ExpoEzvizView: removeFromSuperview called. Stopping player.")
    player?.stopRealPlay()
    player?.destoryPlayer()
    super.removeFromSuperview()
  }

    func destoryPlayer() {
        print("ExpoEzvizView: destoryPlayer() called")
        player?.destoryPlayer()
    }

    func openSound() {
        player?.openSound()
        self.isSoundOn = true
    }

    func closeSound() {
        player?.closeSound()
        self.isSoundOn = false
    }

    func getIsSoundOn() -> Bool {
        return self.isSoundOn
    }

    func startRealPlay() {
        player?.startRealPlay()
    }

    func stopRealPlay() {
        player?.stopRealPlay()
    }

    func capturePicture() {
        print("ExpoEzvizView: capturePicture() called")
        DispatchQueue.main.async {
            guard let picture = self.player?.capturePicture(100) else {
                print("ExpoEzvizView: capturePicture() failed, returned nil image.")
                self.onPictureCaptured(["success": false, "error": "Failed to capture image from player."])
                return
            }

            print("ExpoEzvizView: Picture captured successfully. Now saving to photo library.")
            UIImageWriteToSavedPhotosAlbum(picture, self, #selector(self.image(_:didFinishSavingWithError:contextInfo:)), nil)
        }
    }

    func startPlayback(from recordFileDict: [String: Any]) -> Bool {
        let recordFile = EZDeviceRecordFile()
        if let startTimeInterval = recordFileDict["startTime"] as? Double {
            recordFile.startTime = Date(timeIntervalSince1970: startTimeInterval / 1000)
        }
        if let stopTimeInterval = recordFileDict["stopTime"] as? Double {
            recordFile.stopTime = Date(timeIntervalSince1970: stopTimeInterval / 1000)
        }
       
        
        return self.player?.startPlayback(fromDevice: recordFile) ?? false
    }

    func startLocalRecord(with path: String) -> Bool {
        return self.player?.startLocalRecord(withPathExt: path) ?? false
    }

    func downloadRecordFile(from recordFileDict: [String: Any]) {
        let recordFile = EZDeviceRecordFile()
        if let startTimeInterval = recordFileDict["startTime"] as? Double {
            recordFile.startTime = Date(timeIntervalSince1970: startTimeInterval / 1000)
        }
        if let stopTimeInterval = recordFileDict["stopTime"] as? Double {
            recordFile.stopTime = Date(timeIntervalSince1970: stopTimeInterval / 1000)
        }
       
        let documentsPath = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true)[0]
        
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyyMMddHHmmss"
        let dateString = dateFormatter.string(from: recordFile.startTime ?? Date())

        let fileName = "\(dateString).ps"
        let savePath = (documentsPath as NSString).appendingPathComponent(fileName)

        guard let deviceSerial = self.deviceSerial, let verifyCode = self.verifyCode else {
            self.onDownloadError(["error": "Device serial or verify code is not set."])
            return
        }

        let taskID = UInt.random(in: 0...UInt.max)
        let downloadTask = EZDeviceRecordDownloadTask()

        downloadTask.initTask(
            withID: taskID,
            deviceRecordFileInfo: recordFile,
            deviceSerial: deviceSerial,
            cameraNo: self.cameraNo,
            verifyCode: verifyCode,
            savePath: savePath
        ) { [weak self] task in
            guard let self = self else {
                return
            }

            self.downloadTask = task

            task.setDownloadCallBackWithFinshed({ statusCode in
                DispatchQueue.main.async {
                    switch statusCode {
                    case .finish:
                        print("ExpoEzvizView: Download finished successfully for task \(task.taskID). Starting conversion.")
                        
                        guard let verifyCode = self.verifyCode else {
                            self.onDownloadError(["error": "Verify code is missing for video conversion."])
                            return
                        }

                        let psPath = savePath
                        let mp4Path = (psPath as NSString).deletingPathExtension + ".mp4"

                        EZVideoTransformer.videoTransFormerPSPath(
                            psPath,
                            toPath: mp4Path,
                            type: EZVideoTransformerTypeMP4,
                            withKey: verifyCode,
                            succBlock: {
                                DispatchQueue.main.async {
                                    print("ExpoEzvizView: PS to MP4 conversion successful. Saving to photo album.")
                                    
                                    PHPhotoLibrary.shared().performChanges({
                                        PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: URL(fileURLWithPath: mp4Path))
                                    }, completionHandler: { success, error in
                                        DispatchQueue.main.async {
                                            if success {
                                                print("ExpoEzvizView: Video saved to photo album successfully.")
                                                self.onDownloadSuccess(["path": mp4Path, "savedToAlbum": true])
                                                // Clean up the temporary mp4 file
                                                try? FileManager.default.removeItem(atPath: mp4Path)
                                            } else {
                                                print("ExpoEzvizView: Failed to save video to photo album. Error: \(error?.localizedDescription ?? "unknown error")")
                                                // Don't delete the file, let the user access it from the path.
                                                self.onDownloadError(["error": "Failed to save video to photo album.", "path": mp4Path])
                                            }
                                            // Clean up the .ps file in both cases
                                            try? FileManager.default.removeItem(atPath: psPath)
                                        }
                                    })
                                }
                            },
                            processBlock: { rate in
                                DispatchQueue.main.async {
                                    self.onDownloadProgress(["progress": Float(rate) / 100.0])
                                }
                            },
                            fail: { errCode in
                                DispatchQueue.main.async {
                                    print("ExpoEzvizView: PS to MP4 conversion failed with code: \(errCode)")
                                    self.onDownloadError(["error": "Video conversion failed with code: \(errCode)"])
                                }
                            }
                        )
                    default:
                        print("ExpoEzvizView: Download finished with status: \(statusCode.rawValue)")
                        self.onDownloadError(["error": "Download finished with status: \(statusCode.rawValue)"])
                    }
                    EZRecordDownloader.shareInstane().stop(task)
                    self.downloadTask = nil
                }
            }, failed: { error in
                DispatchQueue.main.async {
                    print("ExpoEzvizView: Download failed with error: \(error.localizedDescription)")
                    self.onDownloadError(["error": "Download failed: \(error.localizedDescription)"])
                    EZRecordDownloader.shareInstane().stop(task)
                    self.downloadTask = nil
                }
            })

            let ret = EZRecordDownloader.shareInstane().add(task)
            DispatchQueue.main.async {
                switch ret {
                case 0:
                    print("ExpoEzvizView: Download task added to queue successfully.")
                case -1:
                    self.onDownloadError(["error": "Failed to add download task: task is nil."])
                    self.downloadTask = nil
                case -2:
                    self.onDownloadError(["error": "Failed to add download task: task is already downloading."])
                    self.downloadTask = nil
                default:
                    self.onDownloadError(["error": "Failed to add download task with unknown error code: \(ret)."])
                    self.downloadTask = nil
                }
            }
        }
    }

    @objc func image(_ image: UIImage, didFinishSavingWithError error: Error?, contextInfo: UnsafeRawPointer) {
        if let error = error {
            // We got back an error!
            print("ExpoEzvizView: Save error: \(error.localizedDescription)")
            onPictureCaptured(["success": false, "error": "Failed to save image: \(error.localizedDescription)"])
        } else {
            print("ExpoEzvizView: Image saved successfully to photo library.")
            onPictureCaptured(["success": true])
        }
    }


  func createPlayer() {
    print("ExpoEzvizView: createPlayer() called on thread: \(Thread.current). Is main thread? \(Thread.isMainThread)")
    guard let deviceSerial = self.deviceSerial, !deviceSerial.isEmpty else {
      print("ExpoEzvizView: createPlayer() aborted, deviceSerial is nil or empty.")
      return
    }

    // All UI operations and SDK calls that interact with views must be on the main thread.
    // And we must destroy the old player if it exists
    DispatchQueue.main.async {
      print("ExpoEzvizView: Executing player creation on main thread.")
      self.player = EZOpenSDK.createPlayer(withDeviceSerial: deviceSerial, cameraNo: self.cameraNo)
      print("ExpoEzvizView: Player instance created.")
      
      if let code = self.verifyCode {
        self.player?.setPlayVerifyCode(code)
        print("ExpoEzvizView: Verify code set.")
      }

      self.player?.delegate = self
      self.player?.setPlayerView(self.playerView)
      print("ExpoEzvizView: Player view set.")
    }
  }
}

extension ExpoEzvizView: EZPlayerDelegate {
  func player(_ player: EZPlayer!, didPlayFailed error: Error!) {
    print("ExpoEzvizView: Play failed with error: \(error.localizedDescription)")
    DispatchQueue.main.async {
        self.onPlayFailed(["error": error.localizedDescription])
    }
  }

  func player(_ player: EZPlayer!, didReceivedMessage messageCode: Int) {
    print("ExpoEzvizView: Received message: \(messageCode)")
    // Corresponds to PLAYER_PLAYBACK_START in Objective-C
    if messageCode == 2002 {
        if !self.isSoundOn {
            player.closeSound()
        }
    }
  }
}
