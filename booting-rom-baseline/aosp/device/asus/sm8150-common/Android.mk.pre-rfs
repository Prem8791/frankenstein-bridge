#
# Copyright (C) 2022 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

LOCAL_PATH := $(call my-dir)

ifneq ($(filter I001D I01WD,$(TARGET_DEVICE)),)

include $(call all-subdir-makefiles,$(LOCAL_PATH))

include $(CLEAR_VARS)

FIRMWARE_MOUNT_POINT := $(TARGET_OUT_VENDOR)/firmware_mnt
$(FIRMWARE_MOUNT_POINT): $(LOCAL_INSTALLED_MODULE)
	@echo "Creating $(FIRMWARE_MOUNT_POINT)"
	@mkdir -p $(TARGET_OUT_VENDOR)/firmware_mnt

BT_FIRMWARE_MOUNT_POINT := $(TARGET_OUT_VENDOR)/bt_firmware
$(BT_FIRMWARE_MOUNT_POINT): $(LOCAL_INSTALLED_MODULE)
	@echo "Creating $(BT_FIRMWARE_MOUNT_POINT)"
	@mkdir -p $(TARGET_OUT_VENDOR)/bt_firmware

DSP_MOUNT_POINT := $(TARGET_OUT_VENDOR)/dsp
$(DSP_MOUNT_POINT): $(LOCAL_INSTALLED_MODULE)
	@echo "Creating $(DSP_MOUNT_POINT)"
	@mkdir -p $(TARGET_OUT_VENDOR)/dsp

ALL_DEFAULT_INSTALLED_MODULES += $(FIRMWARE_MOUNT_POINT) $(BT_FIRMWARE_MOUNT_POINT) $(DSP_MOUNT_POINT)

FACTORY_MOUNT_POINT_SYMLINK := $(TARGET_OUT_VENDOR)/factory
$(FACTORY_MOUNT_POINT_SYMLINK): $(LOCAL_INSTALLED_MODULE)
	@echo "Creating $@ link"
	@rm -rf $@
	$(hide) ln -sf /mnt/vendor/persist $@

ALL_DEFAULT_INSTALLED_MODULES += $(FACTORY_MOUNT_POINT_SYMLINK)

WCNSS_COUNTRY_SYMLINK := $(TARGET_OUT_VENDOR)/firmware/wlan/qca_cld/COUNTRY
$(WCNSS_COUNTRY_SYMLINK): $(LOCAL_INSTALLED_MODULE)
	@echo "WCNSS COUNTRY bin link: $@"
	@mkdir -p $(dir $@)
	@rm -rf $@
	$(hide) ln -sf /vendor/factory/$(notdir $@) $@

WCNSS_MAC_SYMLINK := $(TARGET_OUT_VENDOR)/firmware/wlan/qca_cld/wlan_mac.bin
$(WCNSS_MAC_SYMLINK): $(LOCAL_INSTALLED_MODULE)
	@echo "WCNSS MAC bin link: $@"
	@mkdir -p $(dir $@)
	@rm -rf $@
	$(hide) ln -sf /vendor/factory/$(notdir $@) $@

ALL_DEFAULT_INSTALLED_MODULES += $(WCNSS_COUNTRY_SYMLINK) $(WCNSS_MAC_SYMLINK)

EGL_SYMLINK := $(TARGET_OUT_VENDOR)/lib/libEGL_adreno.so
$(EGL_SYMLINK): $(LOCAL_INSTALLED_MODULE)
	@mkdir -p $(dir $@)
	$(hide) ln -sf egl/$(notdir $@) $@

GLESv2_SYMLINK := $(TARGET_OUT_VENDOR)/lib/libGLESv2_adreno.so
$(GLESv2_SYMLINK): $(LOCAL_INSTALLED_MODULE)
	@mkdir -p $(dir $@)
	$(hide) ln -sf egl/$(notdir $@) $@

Q3DTOOLS_SYMLINK := $(TARGET_OUT_VENDOR)/lib/libq3dtools_adreno.so
$(Q3DTOOLS_SYMLINK): $(LOCAL_INSTALLED_MODULE)
	@mkdir -p $(dir $@)
	$(hide) ln -sf egl/$(notdir $@) $@

EGL_64_SYMLINK := $(TARGET_OUT_VENDOR)/lib64/libEGL_adreno.so
$(EGL_64_SYMLINK): $(LOCAL_INSTALLED_MODULE)
	@mkdir -p $(dir $@)
	$(hide) ln -sf egl/$(notdir $@) $@

GLESv2_64_SYMLINK := $(TARGET_OUT_VENDOR)/lib64/libGLESv2_adreno.so
$(GLESv2_64_SYMLINK): $(LOCAL_INSTALLED_MODULE)
	@mkdir -p $(dir $@)
	$(hide) ln -sf egl/$(notdir $@) $@

Q3DTOOLS_64_SYMLINK := $(TARGET_OUT_VENDOR)/lib64/libq3dtools_adreno.so
$(Q3DTOOLS_64_SYMLINK): $(LOCAL_INSTALLED_MODULE)
	@mkdir -p $(dir $@)
	$(hide) ln -sf egl/$(notdir $@) $@

ALL_DEFAULT_INSTALLED_MODULES += $(EGL_SYMLINK) $(GLESv2_SYMLINK) $(Q3DTOOLS_SYMLINK) $(EGL_64_SYMLINK) $(GLESv2_64_SYMLINK) $(Q3DTOOLS_64_SYMLINK)

endif

