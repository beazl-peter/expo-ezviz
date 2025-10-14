# Expo EZVIZ Module

This module provides a React Native component and API to interact with the EZVIZ SDK for Android and iOS, allowing you to display live and recorded video streams from EZVIZ cameras.

## Table of Contents

- [Expo EZVIZ Module](#expo-ezviz-module)
  - [Table of Contents](#table-of-contents)
  - [Installation](#installation)
  - [Configuration](#configuration)
    - [iOS Permissions](#ios-permissions)
  - [Usage](#usage)
    - [ExpoEzvizView Component](#expoezvizview-component)
      - [Props](#props)
      - [Event Callbacks](#event-callbacks)
    - [Imperative Methods](#imperative-methods)
    - [Module API](#module-api)
  - [Full Example](#full-example)

## Installation

Install the package from npm:

```bash
npx expo install expo-ezviz
```

This will install the package and automatically link the native dependencies. If you are not using EAS Build, you will need to run `npx expo prebuild` to generate the native `ios` and `android` directories.

The config plugin will automatically add the required `NSPhotoLibraryAddUsageDescription` permission to your `Info.plist`.

## Configuration

Before using the player, you must initialize the SDK and set the access token. This should typically be done once when your app starts.

```javascript
import ExpoEzviz from 'expo-ezviz';

async function initialize() {
  try {
    // 1. Initialize the SDK with your App Key and API URL
    await ExpoEzviz.initSDK("YOUR_APP_KEY", "https://ieuopen.ezvizlife.com");

    // 2. Set the access token
    await ExpoEzviz.setAccessToken("YOUR_ACCESS_TOKEN");

    console.log("EZVIZ SDK configured successfully!");
  } catch (error) {
    console.error("Failed to configure EZVIZ SDK:", error);
  }
}

initialize();
```

### iOS Permissions

To enable piture and video saving you must add permissions to Info.plist:

```xml
<key>NSPhotoLibraryAddUsageDescription</key>
<string>This app needs access to your photo library to save captured images.</string>
```

or in eas.json for Expo managed builds:

```json
{
  "ios": {
    "infoPlist": {
      "NSPhotoLibraryAddUsageDescription": "This app needs access to your photo library to save captured images."
    }
  }
}
```


## Usage

The module exports a component `<ExpoEzvizView />` for rendering the video and a set of functions on the `ExpoEzviz` object for other operations.

### ExpoEzvizView Component

This is the main component for displaying the video player.

```jsx
import { ExpoEzvizView, ExpoEzvizViewHandle } from 'expo-ezviz';
import { useRef } from 'react';

const playerRef = useRef<ExpoEzvizViewHandle>(null);

<ExpoEzvizView
  ref={playerRef}
  style={{ width: '100%', aspectRatio: 16 / 9 }}
  deviceSerial="YOUR_DEVICE_SERIAL"
  cameraNo={1}
  verifyCode="YOUR_DEVICE_VERIFY_CODE"
  onPlayFailed={(event) => console.error(event.nativeEvent.error)}
/>
```

#### Props

| Prop           | Type     | Required | Description                                           |
| -------------- | -------- | -------- | ----------------------------------------------------- |
| `deviceSerial` | `string` | Yes      | The serial number of the EZVIZ device.                |
| `cameraNo`     | `number` | Yes      | The camera number/channel on the device (usually `1`). |
| `verifyCode`   | `string` | Yes      | The verification code for the encrypted device stream. |

#### Event Callbacks

| Event                 | Payload                                                  | Description                                                              |
| --------------------- | -------------------------------------------------------- | ------------------------------------------------------------------------ |
| `onPlayFailed`        | `{ error: string }`                                      | Fired when the player fails to start playback.                           |
| `onPictureCaptured`   | `{ success: boolean, error?: string }`                   | Fired after a `capturePicture()` call completes.                         |
| `onDownloadProgress`  | `{ progress: number }`                                   | Fired periodically during a file download, with progress from `0.0` to `1.0`. |
| `onDownloadSuccess`   | `{ path: string, savedToAlbum: boolean }`                | Fired when a video download and conversion is successful.                |
| `onDownloadError`     | `{ error: string }`                                      | Fired if an error occurs during video download or processing.            |

### Imperative Methods

You can call methods directly on the component instance using a `ref`. These methods control the player instance associated with the view.

```javascript
// Get a ref to the component
const playerRef = useRef<ExpoEzvizViewHandle>(null);

// Example: Start the live stream
playerRef.current?.startRealPlay();
```

| Method                 | Parameters                               | Returns                 | Description                                                                 |
| ---------------------- | ---------------------------------------- | ----------------------- | --------------------------------------------------------------------------- |
| `startRealPlay()`      | -                                        | `void`                  | Starts the live video stream.                                               |
| `stopRealPlay()`       | -                                        | `void`                  | Stops the live video stream.                                                |
| `openSound()`          | -                                        | `Promise<boolean>`      | Unmutes the player's audio.                                                 |
| `closeSound()`         | -                                        | `Promise<boolean>`      | Mutes the player's audio.                                                   |
| `capturePicture()`     | -                                        | `void`                  | Captures the current frame and saves it to the photo library.               |
| `startPlayback()`      | `recordFile: DeviceRecordFile`           | `Promise<boolean>`      | Starts playback of a recorded file obtained from `searchRecordFileFromDevice`. |
| `downloadRecordFile()` | `recordFile: DeviceRecordFile`           | `void`                  | Downloads a recorded file. Triggers download-related events.                |
| `startLocalRecord()`   | `path: string`                           | `Promise<boolean>`      | Starts recording the current stream to a specified local file path.         |

### Module API

These are static methods available on the `ExpoEzviz` object.

| Method                         | Parameters                                                                   | Returns                               | Description                                                                                                                              |
| ------------------------------ | ---------------------------------------------------------------------------- | ------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `initSDK()`                    | `appKey: string`, `apiUrl: string`                                           | `Promise<void>`                       | Initializes the EZVIZ SDK. Must be called first.                                                                                         |
| `setAccessToken()`             | `accessToken: string`                                                        | `Promise<void>`                       | Sets the access token for authenticating API requests.                                                                                   |
| `searchRecordFileFromDevice()` | `deviceSerial: string`, `cameraNo: number`, `begin: string`, `end: string`   | `Promise<DeviceRecordFile[]>`         | Searches for recorded video files on the device's SD card within a given UTC time range (`YYYY-MM-DD HH:mm:ss`). The time window cannot exceed 24 hours. |
| `addDevice()`                  | `deviceSerial: string`, `verifyCode: string`                                 | `Promise<void>`                       | Adds a device to the user's account.                                                                                                     |
| `deleteDevice()`               | `deviceSerial: string`                                                       | `Promise<void>`                       | Deletes a device from the user's account.                                                                                                |
| `setDeviceName()`              | `deviceName: string`, `deviceSerial: string`                                 | `Promise<void>`                       | Sets a new name for a device.                                                                                                            |
| `setDeviceEncryptStatus()`     | `deviceSerial: string`, `verifyCode: string`, `encryptStatus: boolean`       | `Promise<void>`                       | Enables or disables encryption for a device's video stream. The `verifyCode` is required when disabling encryption.                    |
| `setDefence()`                 | `deviceSerial: string`, `defenceType: number`                                | `Promise<void>`                       | Sets the defence mode of a device. `defenceType`: `0` (Close), `1` (Open), `8` (Sleep/At Home for A1).                                    |
| `getDeviceInfo()`              | `deviceSerial: string`                                                       | `Promise<DeviceInfo>`                 | Retrieves detailed information about a specific device.                                                                                  |

The `DeviceRecordFile` object has the following shape:

```typescript
interface DeviceRecordFile {
  startTime: number; // UTC timestamp in milliseconds
  stopTime: number;  // UTC timestamp in milliseconds
}
```

## Full Example

Here is a more complete example demonstrating how to use the component and its APIs.

```jsx
import ExpoEzviz, {
  DeviceRecordFile,
  ExpoEzvizView,
  ExpoEzvizViewHandle,
} from "@/modules/expo-ezviz";
import { useEffect, useRef, useState } from "react";
import { Alert, Button, StyleSheet, Text, View } from "react-native";

const EZVIZ_CONFIG = {
  appKey: "YOUR_APP_KEY",
  apiUrl: "https://ieuopen.ezvizlife.com",
  accessToken: "YOUR_ACCESS_TOKEN",
  deviceSerial: "YOUR_DEVICE_SERIAL",
  verifyCode: "YOUR_DEVICE_VERIFY_CODE",
};

export default function PlayerScreen() {
  const [isReady, setIsReady] = useState(false);
  const playerRef = useRef<ExpoEzvizViewHandle>(null);

  useEffect(() => {
    async function setup() {
      try {
        await ExpoEzviz.initSDK(EZVIZ_CONFIG.appKey, EZVIZ_CONFIG.apiUrl);
        await ExpoEzviz.setAccessToken(EZVIZ_CONFIG.accessToken);
        setIsReady(true);
      } catch (error) {
        console.error("Error initializing EZVIZ SDK:", error);
      }
    }
    setup();
  }, []);

  if (!isReady) {
    return <Text>Initializing...</Text>;
  }

  return (
    <View style={styles.container}>
      <ExpoEzvizView
        ref={playerRef}
        style={styles.player}
        deviceSerial={EZVIZ_CONFIG.deviceSerial}
        verifyCode={EZVIZ_CONFIG.verifyCode}
        cameraNo={1}
        onPictureCaptured={({ nativeEvent }) => {
          if (nativeEvent.success) {
            Alert.alert("Success", "Image saved to photo library.");
          } else {
            Alert.alert("Error", `Capture failed: ${nativeEvent.error}`);
          }
        }}
        onPlayFailed={({ nativeEvent }) => Alert.alert("Playback Error", nativeEvent.error)}
      />

      <View style={styles.buttonContainer}>
        <Button title="Start Live" onPress={() => playerRef.current?.startRealPlay()} />
        <Button title="Stop Live" onPress={() => playerRef.current?.stopRealPlay()} />
        <Button title="Capture" onPress={() => playerRef.current?.capturePicture()} />
      </View>
      <View style={styles.buttonContainer}>
        <Button title="Open Sound" onPress={() => playerRef.current?.openSound()} />
        <Button title="Close Sound" onPress={() => playerRef.current?.closeSound()} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  player: { width: '100%', aspectRatio: 16 / 9 },
  buttonContainer: { flexDirection: 'row', justifyContent: 'space-around', width: '100%', marginVertical: 10 },
});
```
