import { requireNativeModule } from "expo-modules-core";

import
  {
    DeviceRecordFile,
    DownloadErrorEvent,
    DownloadProgressEvent,
    DownloadSuccessEvent,
    PictureCapturedEvent,
    PlayFailedEvent,
  } from "./src/ExpoEzvizTypes";

import ExpoEzvizView, { ExpoEzvizViewHandle } from "./src/ExpoEzvizView";

const ExpoEzviz = requireNativeModule("ExpoEzviz");

export { ExpoEzvizView };

  export type {
    DeviceRecordFile,
    DownloadErrorEvent,
    DownloadProgressEvent,
    DownloadSuccessEvent,
    ExpoEzvizViewHandle,
    PictureCapturedEvent,
    PlayFailedEvent
  };

export default ExpoEzviz;
