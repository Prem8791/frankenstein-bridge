# Frankenstein Broker ROM integration

Copy this project into the Android tree as `packages/apps/FrankensteinApps`,
then inherit the product fragment from the I001D product configuration:

```make
$(call inherit-product, packages/apps/FrankensteinApps/rom/frankenstein_broker_product.mk)
```

The Soong modules install the broker and the ProdX base APK as platform-signed
product priv-apps and install their permission configuration. Replaceable
ProdX code continues to communicate with the broker through its AIDL contract.

The ROM builds only a tiny ProdX bootstrap. It freezes ProdX's package/signing
identity, VoiceInteractionService component names, protected SoundTrigger
grants, and access to the device's Qualcomm SoundTrigger 2.3 implementation—not
the full assistant or an owner voice model. A future higher-version ProdX APK
signed with the same platform certificate supplies MiniCPM, UI, actions,
owner-voice enrollment, and compatible DSP models without rebuilding the ROM.
The CPU/ONNX wake-word fallback is intentionally excluded.

On I001D, the boot-critical combined audio service remains 64-bit. A separate
non-critical 32-bit vendor process publishes the legacy Qualcomm SoundTrigger
2.3 HAL so ASUS's 32-bit support libraries never share the main audio process.
If that legacy process fails, Android and normal audio continue to boot.
