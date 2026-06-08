{ pkgs, ... }:

{
  # JDK for Gradle / Android Gradle Plugin (AGP 8.9 needs JDK 17).
  languages.java = {
    enable = true;
    jdk.package = pkgs.jdk17;
  };

  # Android SDK: compile (platform 35 + build-tools) plus a hardware-accelerated
  # emulator (x86_64 google_apis system image) so the app can be built and driven
  # end-to-end. devenv sets ANDROID_HOME / ANDROID_SDK_ROOT and PATHs for adb,
  # emulator, sdkmanager, avdmanager.
  android = {
    enable = true;
    platforms.version = [ "35" ];
    buildTools.version = [ "35.0.0" ];
    abis = [ "x86_64" ];
    systemImageTypes = [ "google_apis" ];
    emulator.enable = true;
    systemImages.enable = true;
    googleAPIs.enable = true;
    extraLicenses = [
      "android-sdk-preview-license"
      "android-googletv-license"
      "android-sdk-arm-dbt-license"
      "google-gdk-license"
      "intel-android-extra-license"
      "intel-android-sysimage-license"
      "mips-android-sysimage-license"
    ];
  };
}
