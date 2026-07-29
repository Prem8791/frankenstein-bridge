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
identity, VoiceInteractionService component names, and protected SoundTrigger
grants—not the full assistant or a wake model. A future higher-version ProdX APK
signed with the same platform certificate supplies MiniCPM, ONNX, UI, actions,
and compatible DSP models without rebuilding the ROM.
