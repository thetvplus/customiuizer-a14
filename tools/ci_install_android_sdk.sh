#!/usr/bin/env bash
# Install the exact Android SDK packages required by compileSdk 37 / AGP 9.3.
# Pin stable packages. Do not glob android-37* or pick beta/rc.
set -euo pipefail

PLATFORM_PACKAGE="platforms;android-37.1"
BUILD_TOOLS_PACKAGE="build-tools;37.0.0"

if [[ "${PLATFORM_PACKAGE}${BUILD_TOOLS_PACKAGE}" =~ [Bb]eta|[Rr][Cc][0-9]|[Pp]review ]]; then
  echo "::error::Pinned Android SDK packages must be stable, not beta/rc/preview"
  exit 1
fi

if [ -z "${ANDROID_SDK_ROOT:-}" ]; then
  echo "::error::ANDROID_SDK_ROOT is not set"
  exit 1
fi

PLATFORM_DIR_NAME="${PLATFORM_PACKAGE#platforms;}"
BUILD_TOOLS_DIR_NAME="${BUILD_TOOLS_PACKAGE#build-tools;}"
PLATFORM_DIR="${ANDROID_SDK_ROOT}/platforms/${PLATFORM_DIR_NAME}"
BUILD_TOOLS_DIR="${ANDROID_SDK_ROOT}/build-tools/${BUILD_TOOLS_DIR_NAME}"

echo "Installing ${PLATFORM_PACKAGE} ${BUILD_TOOLS_PACKAGE} platform-tools"
sdkmanager --channel=0 "platform-tools" "${PLATFORM_PACKAGE}" "${BUILD_TOOLS_PACKAGE}"

if [ ! -f "${PLATFORM_DIR}/android.jar" ]; then
  echo "::error::Pinned platform package ${PLATFORM_PACKAGE} did not install ${PLATFORM_DIR}/android.jar"
  ls -la "${ANDROID_SDK_ROOT}/platforms" || true
  exit 1
fi
if [ ! -x "${BUILD_TOOLS_DIR}/aapt" ]; then
  echo "::error::Pinned build-tools package ${BUILD_TOOLS_PACKAGE} did not install ${BUILD_TOOLS_DIR}/aapt"
  ls -la "${ANDROID_SDK_ROOT}/build-tools" || true
  exit 1
fi

echo "Installed platform dir: ${PLATFORM_DIR}"
echo "Installed build-tools dir: ${BUILD_TOOLS_DIR}"
printf 'sdk.dir=%s\n' "${ANDROID_SDK_ROOT}" > local.properties
