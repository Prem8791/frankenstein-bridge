#
# Copyright (C) 2022 The LineageOS Project
#

# AsusParts uses-library compatibility
PRODUCT_BROKEN_VERIFY_USES_LIBRARIES := true
# SPDX-License-Identifier: Apache-2.0
#

# Inherit from those products. Most specific first.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)

# Inherit some common Bliss stuff.
$(call inherit-product, vendor/bliss/config/common_full_phone.mk)

# Inherit from I001D device
$(call inherit-product, device/asus/I001D/device.mk)

# Bootanimation
TARGET_BOOT_ANIMATION_RES := 1080

# UDFPS Animations
EXTRA_UDFPS_ANIMATIONS := true

PRODUCT_DEVICE := I001D
PRODUCT_NAME := bliss_I001D
PRODUCT_BRAND := asus
PRODUCT_MODEL := ASUS_I001D
PRODUCT_MANUFACTURER := asus

PRODUCT_GMS_CLIENTID_BASE := android-asus

# Build info
    BUILD_FINGERPRINT := asus/WW_I001D/ASUS_I001_1:11/RKQ1.200710.002/18.0210.2201.215-0:user/release-keys

# HomeLauncher
PRODUCT_PACKAGES += \
    HomeLauncher \
    privapp-permissions-com.home.launcher

# Frankenstein platform broker
$(call inherit-product, packages/apps/FrankensteinApps/rom/frankenstein_broker_product.mk)

PRODUCT_RELEASE_CONFIG_MAPS := build/release/release_config_map.textproto
