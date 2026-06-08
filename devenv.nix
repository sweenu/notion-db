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

  # `emu` — launch the emulator reliably on NixOS.
  #
  # The emulator bundles its own libc++, but devenv puts the SDK build-tools /
  # NDK lib dirs on LD_LIBRARY_PATH (which Gradle needs), and those shadow it
  # with an older libc++, crashing the emulator with a symbol-lookup error. We
  # can't drop them globally without risking the build, so clear LD_LIBRARY_PATH
  # for just the emulator and use the bundled software renderer.
  #
  # Usage: `emu` (windowed, AVD nd_test) · `emu -no-window` (headless) ·
  #        `EMU_AVD=other emu`.
  scripts.emu.exec = ''
    exec env -u LD_LIBRARY_PATH emulator -avd "''${EMU_AVD:-nd_test}" \
      -gpu swiftshader_indirect -no-snapshot -no-audio "$@"
  '';
}
