import { NativeModule, requireNativeModule } from "expo";

import { DeviceRecordFile, ExpoEzvizModuleEvents } from "./ExpoEzvizTypes";

// Define interfaces for common nested objects if they exist in EZDeviceInfo
export interface CameraInfo {
  deviceSerial: string;
  cameraNo: number;
  // Add other properties of EZCameraInfo as they are discovered/needed
  [key: string]: any; // Allows for any other dynamically discovered properties
}

export interface SubDeviceInfo {
  deviceSerial: string;
  // Add other properties of EZSubDeviceInfo as they are discovered/needed
  [key: string]: any; // Allows for any other dynamically discovered properties
}

export interface DeviceInfo {
  deviceName: string;
  deviceSerial: string;
  deviceType: string;
  deviceVersion: string;
//   addTime: number;
//   alarmSoundMode: number;
  cameraNum: number;
  category: string;
  defence: number;
  detectorNum: number;
  deviceCover: string;
  isEncrypt: boolean;
//   offlineNotify: number;
  status: number;
//   supportExtShort: string;
  // cameraInfo is intentionally excluded as per request
  // subDeviceInfo is intentionally excluded as per request
  [key: string]: any; // Allows for any other dynamically discovered properties
}

declare class ExpoEzvizModule extends NativeModule<ExpoEzvizModuleEvents> {
  initSDK(appKey: string, apiUrl?: string): void;
  setAccessToken(accessToken: string): void;
  capturePicture(viewTag: number): void;
  searchRecordFileFromDevice(
    deviceSerial: string,
    cameraNo: number,
    beginTime: string,
    endTime: string
  ): Promise<DeviceRecordFile[]>;
  startPlaybackFromDevice(
    viewTag: number,
    recordFile: DeviceRecordFile
  ): Promise<boolean>;
  startLocalRecord(viewTag: number, path: string): Promise<boolean>;
  downloadRecordFile(viewTag: number, recordFile: DeviceRecordFile): void;
  addDevice(deviceSerial: string, verifyCode: string): Promise<void>;
  deleteDevice(deviceSerial: string): Promise<void>;
  setDeviceName(deviceName: string, deviceSerial: string): Promise<void>;
  setDeviceEncryptStatus(deviceSerial: string, verifyCode: string, encryptStatus: boolean): Promise<void>;
  setDefence(deviceSerial: string, defenceType: number): Promise<void>;
  getDeviceInfo(deviceSerial: string): Promise<DeviceInfo>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ExpoEzvizModule>("ExpoEzviz");
