import { requireNativeViewManager } from "expo-modules-core";
import { forwardRef, useImperativeHandle, useRef } from "react";

import { DeviceRecordFile, ExpoEzvizViewProps } from "./ExpoEzvizTypes";

const ExpoEzvizNativeView = requireNativeViewManager("ExpoEzviz", "ExpoEzvizView");

// This interface now represents the unified API exposed on the view's ref for BOTH platforms.
interface NativeViewHandle {
  capturePicture: () => void;
  startPlayback: (recordFile: DeviceRecordFile) => Promise<boolean>;
  stopPlayback: () => Promise<boolean>;
  startLocalRecord: (path: string) => Promise<boolean>;
  stopLocalRecord: () => void;
  downloadRecordFile: (recordFile: DeviceRecordFile) => void;
  pausePlayback: () => Promise<boolean>;
  resumePlayback: () => Promise<boolean>;
  seekPlayback: (offsetTimestamp: number) => Promise<boolean>;
  startRealPlay: () => void;
  stopRealPlay: () => void;
  openSound: () => Promise<boolean>;
  closeSound: () => Promise<boolean>;
}

export interface ExpoEzvizViewHandle {
  capturePicture: () => void;
  startPlayback: (recordFile: DeviceRecordFile) => Promise<boolean>;
  stopPlayback: () => Promise<Boolean>;
  startLocalRecord: (path: string) => Promise<boolean>;
  stopLocalRecord: () => void;
  pausePlayback: () => Promise<boolean>;
  resumePlayback: () => Promise<boolean>;
  seekPlayback: (offsetTimestamp: number) => Promise<boolean>;
  downloadRecordFile: (recordFile: DeviceRecordFile) => void;
  startRealPlay: () => void;
  stopRealPlay: () => void;
  openSound: () => Promise<boolean>;
  closeSound: () => Promise<boolean>;
}

const ExpoEzvizView = forwardRef<ExpoEzvizViewHandle, ExpoEzvizViewProps>(
  (props, ref) => {
    // The ref now holds the imperative methods directly.
    const nativeViewRef = useRef<React.Component & NativeViewHandle>(null);

    // useImperativeHandle simply exposes the methods from the native ref.
    useImperativeHandle(ref, () => ({
      capturePicture: () => nativeViewRef.current?.capturePicture(),
      startPlayback: (recordFile) => nativeViewRef.current?.startPlayback(recordFile) ?? Promise.resolve(false),
      stopPlayback: () => nativeViewRef.current?.stopPlayback() ?? Promise.resolve(false),
      openSound: () => nativeViewRef.current?.openSound() ?? Promise.resolve(false),
      closeSound: () => nativeViewRef.current?.closeSound() ?? Promise.resolve(false),
      startLocalRecord: (path) => nativeViewRef.current?.startLocalRecord(path) ?? Promise.resolve(false),
      stopLocalRecord: () => nativeViewRef.current?.stopLocalRecord(),
      downloadRecordFile: (recordFile) => nativeViewRef.current?.downloadRecordFile(recordFile),
      pausePlayback: () => nativeViewRef.current?.pausePlayback() ?? Promise.resolve(false),
      resumePlayback: () => nativeViewRef.current?.resumePlayback() ?? Promise.resolve(false),
      seekPlayback: (offsetTimestamp) => nativeViewRef.current?.seekPlayback(offsetTimestamp) ?? Promise.resolve(false),
      startRealPlay: () => nativeViewRef.current?.startRealPlay(),
      stopRealPlay: () => nativeViewRef.current?.stopRealPlay(),
    }));

    return <ExpoEzvizNativeView ref={nativeViewRef} {...props} />;
  }
);

export default ExpoEzvizView;
