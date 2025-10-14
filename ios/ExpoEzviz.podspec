folly_compiler_flags = '-DFOLLY_NO_CONFIG -DFOLLY_MOBILE=1 -DFOLLY_USE_LIBCPP=1 -Wno-comma -Wno-shorten-64-to-32'

Pod::Spec.new do |s|
  s.name           = 'ExpoEzviz'
  s.version        = '1.0.0'
  s.summary        = 'Expo Ezviv Camera Viewer'
  s.description    = 'Expo Ezviz Camera Viewer'
  s.author         = 'Peter Smith <peter@curstan.com>'
  s.homepage       = 'https://docs.expo.dev/modules/'
  s.platforms      = {
    :ios => '15.1',
    :tvos => '15.1'
  }
  s.source         = { git: '' }
  s.static_framework = true

  s.dependency 'ExpoModulesCore'
  s.dependency 'EZOpenSDK'
  s.compiler_flags = folly_compiler_flags + " -DRCT_NEW_ARCH_ENABLED=1 -Wno-nullability-completeness"


  # Swift/Objective-C compatibility
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
  }

  s.source_files = "**/*.{h,m,mm,swift,hpp,cpp}"
end
