#
# Copyright (C) 2022 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

# Soong namespaces
PRODUCT_SOONG_NAMESPACES += \
    $(LOCAL_PATH)

# Audio
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/audio/audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio/audio_policy_configuration.xml \
    $(LOCAL_PATH)/audio/audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy_configuration.xml \
    $(LOCAL_PATH)/audio/mixer_paths_ZS660KL.xml:$(TARGET_COPY_OUT_VENDOR)/etc/mixer_paths.xml \
    $(LOCAL_PATH)/audio/mixer_paths_ZS660KL.xml:$(TARGET_COPY_OUT_VENDOR)/etc/mixer_paths_ZS660KL.xml \
    $(LOCAL_PATH)/audio/mixer_paths_ZS660KL_EU.xml:$(TARGET_COPY_OUT_VENDOR)/etc/mixer_paths_ZS660KL_EU.xml \
    $(LOCAL_PATH)/audio/audio_platform_info.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_platform_info.xml \
    $(LOCAL_PATH)/audio/sound_trigger_platform_info.xml:$(TARGET_COPY_OUT_VENDOR)/etc/sound_trigger_platform_info.xml \

# Fingerprint
PRODUCT_PACKAGES += \
    android.hardware.biometrics.fingerprint@2.3-service.I001D

# Init
PRODUCT_PACKAGES += \
    fstab.qcom

# Input
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/configs/idc/goodix_ts.idc:$(TARGET_COPY_OUT_SYSTEM)/usr/idc/goodix_ts.idc \
    $(LOCAL_PATH)/configs/idc/goodix_ts_station.idc:$(TARGET_COPY_OUT_SYSTEM)/usr/idc/goodix_ts_station.idc \
    $(LOCAL_PATH)/configs/keychars/goodix_ts.kcm:$(TARGET_COPY_OUT_SYSTEM)/usr/keychars/goodix_ts.kcm \
    $(LOCAL_PATH)/configs/keylayout/goodix_ts.kl:$(TARGET_COPY_OUT_SYSTEM)/usr/keylayout/goodix_ts.kl

# RRO Overlays
PRODUCT_PACKAGES += \
    FrameworksResOverlay \
    SettingsRes \
    SystemUIRog2 \
    SettingsProviderRog2


# UDFPS animations
EXTRA_UDFPS_ANIMATIONS := true

# The forced Android 16 prebuilt kernel is 4.14.357-openela-perf+. Keep its
# matching OEM-signed modules; modules built by the old 4.14.190 source tree
# fail vermagic/modversion/signature checks with that kernel.
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/prebuilt/modules/ec_i2c_interface.ko:$(TARGET_COPY_OUT_VENDOR)/lib/modules/ec_i2c_interface.ko \
    $(LOCAL_PATH)/prebuilt/modules/ec_i2c_updatefw.ko:$(TARGET_COPY_OUT_VENDOR)/lib/modules/ec_i2c_updatefw.ko \
    $(LOCAL_PATH)/prebuilt/modules/ene_6k582_station.ko:$(TARGET_COPY_OUT_VENDOR)/lib/modules/ene_6k582_station.ko \
    $(LOCAL_PATH)/prebuilt/modules/ene_8k41_dt.ko:$(TARGET_COPY_OUT_VENDOR)/lib/modules/ene_8k41_dt.ko \
    $(LOCAL_PATH)/prebuilt/modules/ene_8k41_power.ko:$(TARGET_COPY_OUT_VENDOR)/lib/modules/ene_8k41_power.ko \
    $(LOCAL_PATH)/prebuilt/modules/ghci-hcd.ko:$(TARGET_COPY_OUT_VENDOR)/lib/modules/ghci-hcd.ko \
    $(LOCAL_PATH)/prebuilt/modules/gspca_main.ko:$(TARGET_COPY_OUT_VENDOR)/lib/modules/gspca_main.ko \
    $(LOCAL_PATH)/prebuilt/modules/gxhci-hcd.ko:$(TARGET_COPY_OUT_VENDOR)/lib/modules/gxhci-hcd.ko \
    $(LOCAL_PATH)/prebuilt/modules/ml51fb9ae_inbox.ko:$(TARGET_COPY_OUT_VENDOR)/lib/modules/ml51fb9ae_inbox.ko \
    $(LOCAL_PATH)/prebuilt/modules/modules.alias:$(TARGET_COPY_OUT_VENDOR)/lib/modules/modules.alias \
    $(LOCAL_PATH)/prebuilt/modules/modules.dep:$(TARGET_COPY_OUT_VENDOR)/lib/modules/modules.dep \
    $(LOCAL_PATH)/prebuilt/modules/modules.softdep:$(TARGET_COPY_OUT_VENDOR)/lib/modules/modules.softdep \
    $(LOCAL_PATH)/prebuilt/modules/nct7802.ko:$(TARGET_COPY_OUT_VENDOR)/lib/modules/nct7802.ko \
    $(LOCAL_PATH)/prebuilt/modules/station_goodix_touch.ko:$(TARGET_COPY_OUT_VENDOR)/lib/modules/station_goodix_touch.ko \
    $(LOCAL_PATH)/prebuilt/modules/station_key.ko:$(TARGET_COPY_OUT_VENDOR)/lib/modules/station_key.ko \
    $(LOCAL_PATH)/prebuilt/modules/texfat.ko:$(TARGET_COPY_OUT_VENDOR)/lib/modules/texfat.ko \
    $(LOCAL_PATH)/prebuilt/modules/tntfs.ko:$(TARGET_COPY_OUT_VENDOR)/lib/modules/tntfs.ko


# Inherit from the sm8150-common
$(call inherit-product, device/asus/sm8150-common/msmnile.mk)

# Inherit the proprietary files
$(call inherit-product, vendor/asus/I001D/I001D-vendor.mk)
PRODUCT_ENABLE_UFFD_GC := false

# Use personal signing keys
PRODUCT_DEFAULT_DEV_CERTIFICATE := device/asus/I001D/security/platform

# Point mainline SEPolicy cert lookups to our custom keys so mac_permissions.xml
# matches APKs signed with our personal certificate chain
PRODUCT_MAINLINE_BLUETOOTH_SEPOLICY_DEV_CERTIFICATES := device/asus/I001D/security
PRODUCT_MAINLINE_SEPOLICY_DEV_CERTIFICATES := device/asus/I001D/security
