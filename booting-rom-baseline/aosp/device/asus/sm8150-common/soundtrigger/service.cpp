/*
 * Copyright (C) 2026 The BlissRoms Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "soundtrigger-hal-legacy"

#include <cstdlib>

#include <hidl/HidlTransportSupport.h>
#include <hidl/LegacySupport.h>
#include <log/log.h>
#include <utils/Errors.h>

using android::OK;
using android::status_t;
using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;
using android::hardware::registerPassthroughServiceImplementation;

int main() {
  configureRpcThreadpool(4, true /* callerWillJoin */);

  const status_t status = registerPassthroughServiceImplementation(
      "android.hardware.soundtrigger@2.3::ISoundTriggerHw");
  if (status != OK) {
    ALOGE("Failed to register legacy SoundTrigger 2.3 service: %d", status);
    return EXIT_FAILURE;
  }

  ALOGI("Registered legacy 32-bit SoundTrigger 2.3 service");
  joinRpcThreadpool();
  return EXIT_FAILURE;
}
