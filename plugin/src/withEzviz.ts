import { ConfigPlugin, withInfoPlist } from '@expo/config-plugins';

const withEzviz: ConfigPlugin = (config) => {
  // Add iOS permission for saving photos
  config = withInfoPlist(config, (config) => {
    config.modResults.NSPhotoLibraryAddUsageDescription =
      config.modResults.NSPhotoLibraryAddUsageDescription ||
      'This app needs access to your photo library to save captured images and videos.';
    return config;
  });

  // You can add Android permissions or other configurations here if needed

  return config;
};

export default withEzviz;
