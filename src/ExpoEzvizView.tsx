import
  {
    requireNativeModule,
    requireNativeViewManager,
  } from "expo-modules-core";
import { forwardRef, useImperativeHandle, useRef } from "react";
import { findNodeHandle } from "react-native";

import { DeviceRecordFile, ExpoEzvizViewProps } from "./ExpoEzvizTypes";

const NativeView = requireNativeViewManager("ExpoEzviz", "ExpoEzvizView");
const ExpoEzviz = requireNativeModule("ExpoEzviz");

export interface ExpoEzvizViewHandle {
  capturePicture: () => void;
  startPlayback: (recordFile: DeviceRecordFile) => Promise<boolean>;
  startLocalRecord: (path: string) => Promise<boolean>;
  downloadRecordFile: (recordFile: DeviceRecordFile) => void;
  startRealPlay: () => void;
  stopRealPlay: () => void;
  openSound: () => Promise<boolean>;
  closeSound: () => Promise<boolean>;
}

const ExpoEzvizView = forwardRef<ExpoEzvizViewHandle, ExpoEzvizViewProps>(
  (props, ref) => {
    const nativeViewRef = useRef<React.Component>(null);

    useImperativeHandle(ref, () => ({
      capturePicture: () => {
        const node = findNodeHandle(nativeViewRef.current);
        if (node) {
          ExpoEzviz.capturePicture(node);
        }
      },
      startPlayback: (recordFile: DeviceRecordFile) => {
        const node = findNodeHandle(nativeViewRef.current);
        if (node) {
          return ExpoEzviz.startPlaybackFromDevice(node, recordFile);
        }
        return Promise.resolve(false);
      },
      openSound: () => {
        const node = findNodeHandle(nativeViewRef.current);
        if (node) {
          return ExpoEzviz.openSound(node);
        }
        return Promise.resolve(false);
      },
      closeSound: () => {
        const node = findNodeHandle(nativeViewRef.current);
        if (node) {
          return ExpoEzviz.closeSound(node);
        }
        return Promise.resolve(false);
      },
      startLocalRecord: (path: string) => {
        const node = findNodeHandle(nativeViewRef.current);
        if (node) {
          return ExpoEzviz.startLocalRecord(node, path);
        }
        return Promise.resolve(false);
      },
      downloadRecordFile: (recordFile: DeviceRecordFile) => {
        const node = findNodeHandle(nativeViewRef.current);
        if (node) {
          ExpoEzviz.downloadRecordFile(node, recordFile);
        }
      },
      startRealPlay: () => {
        const node = findNodeHandle(nativeViewRef.current);
        if (node) {
          ExpoEzviz.startRealPlay(node);
        }
      },
      stopRealPlay: () => {
        const node = findNodeHandle(nativeViewRef.current);
        if (node) {
          ExpoEzviz.stopRealPlay(node);
        }
      },
    }));

    return <NativeView ref={nativeViewRef} {...props} />;
  }
);

export default ExpoEzvizView;
