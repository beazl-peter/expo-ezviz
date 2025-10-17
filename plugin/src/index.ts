import {
  ConfigPlugin,
  withPlugins,
  withSettingsGradle,
  withInfoPlist,
} from "expo/config-plugins";

const withEzvizInfoPlist: ConfigPlugin = (config) => {
  return withInfoPlist(config, (config) => {
    config.modResults.NSPhotoLibraryAddUsageDescription =
      config.modResults.NSPhotoLibraryAddUsageDescription ||
      "This app needs access to your photo library to save captured images and videos.";
    return config;
  });
};

const withEzvizSettingsGradle: ConfigPlugin = (config) => {
  return withSettingsGradle(config, (config) => {
    config.modResults.contents += `\napply from: new File(["node", "--print", "require.resolve('expo/package.json')"].execute(null, rootDir).text.trim(), "../android/settings.gradle")\n`;
    return config;
  });
};

const withEzviz: ConfigPlugin = (config) =>
  withPlugins(config, [withEzvizInfoPlist, withEzvizSettingsGradle]);

export default withEzviz;