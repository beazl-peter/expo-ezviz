import type { StyleProp, ViewStyle } from 'react-native';

export type PictureCapturedEvent = {
  nativeEvent: {
    success: boolean;
    error?: string;
  };
};

export type PlayFailedEvent = {
  nativeEvent: {
    error: string;
  };
};

export type DownloadProgressEvent = {
  nativeEvent: {
    progress: number;
  };
};

export type DownloadSuccessEvent = {
  nativeEvent: {
    path: string;
  };
};

export type DownloadErrorEvent = {
  nativeEvent: {
    error: string;
  };
};

export type PlaybackProgressEvent = {
  nativeEvent: {
    currentTime: number; // UTC timestamp in milliseconds
  };
};

export type DeviceRecordFile = {
  startTime: number;
  stopTime: number;
  fileType: number;
  playbackUrl: string;
  downloadUrl: string;
};

export type ExpoEzvizModuleEvents = {
  onLoad: () => void;
  onPlayFailed: (event: PlayFailedEvent) => void;
  onPictureCaptured: (event: PictureCapturedEvent) => void;
  onDownloadProgress: (event: DownloadProgressEvent) => void;
  onDownloadSuccess: (event: DownloadSuccessEvent) => void;
  onDownloadError: (event: DownloadErrorEvent) => void;
  onPlaybackProgress: (event: PlaybackProgressEvent) => void;
};

export type ExpoEzvizViewProps = {
  deviceSerial: string;
  verifyCode?: string;
  accessToken?: string;
  cameraNo: number,
  defaultSoundOn?: boolean;
  autoplay?: boolean;
  onLoad?: () => void;
  onPictureCaptured?: (event: PictureCapturedEvent) => void;
  onPlayFailed?: (event: PlayFailedEvent) => void;
  onPlayerMessage?: (event: any) => void;
  onDownloadProgress?: (event: DownloadProgressEvent) => void;
  onDownloadSuccess?: (event: DownloadSuccessEvent) => void;
  onDownloadError?: (event: DownloadErrorEvent) => void;
  onPlaybackProgress?: (event: PlaybackProgressEvent) => void;
  style?: StyleProp<ViewStyle>;
};
