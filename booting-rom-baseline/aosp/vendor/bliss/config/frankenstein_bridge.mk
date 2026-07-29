PRODUCT_SOONG_NAMESPACES += \
    frameworks/base/frankenstein/aidl \
    system/core/frankenstein_diag

PRODUCT_PACKAGES += \
    frankenstein_diag \
    frankenstein-bridge-aidl-V1-java \
    frankenstein-diag-aidl-V1-java \
    frankenstein-diag-aidl-V1-ndk

ifneq ($(wildcard device/asus/sm8150-common/frankenstein/inventory/os-surface-inventory.cbor),)
PRODUCT_COPY_FILES += \
    device/asus/sm8150-common/frankenstein/inventory/os-surface-inventory.cbor:$(TARGET_COPY_OUT_SYSTEM)/etc/frankenstein/inventory/os-surface-inventory.cbor
endif
