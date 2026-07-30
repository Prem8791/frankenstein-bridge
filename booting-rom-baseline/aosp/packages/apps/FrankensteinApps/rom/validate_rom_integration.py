#!/usr/bin/env python3
"""Static release gate for the I001D Frankenstein ROM integration."""

from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[4]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> int:
    product = (ROOT / "device/asus/sm8150-common/msmnile.mk").read_text()
    vendor_properties = (
        ROOT / "device/asus/sm8150-common/vendor.prop"
    ).read_text()
    vendor_file_contexts = (
        ROOT / "device/asus/sm8150-common/sepolicy/vendor/file_contexts"
    ).read_text()
    broker_manifest = (
        ROOT
        / "packages/apps/FrankensteinApps/broker/src/main/AndroidManifest.xml"
    ).read_text()
    vendor_makefile_path = ROOT / "vendor/asus/I001D/I001D-vendor.mk"
    product_packages = (
        ROOT / "packages/apps/FrankensteinApps/rom/frankenstein_broker_product.mk"
    ).read_text()
    legacy_dir = ROOT / "device/asus/sm8150-common/soundtrigger"
    legacy_bp = legacy_dir / "Android.bp"
    legacy_source = legacy_dir / "service.cpp"
    legacy_init = (
        legacy_dir
        / "android.hardware.soundtrigger@2.3-service.i001d.rc"
    )

    for required_file in (legacy_bp, legacy_source, legacy_init):
        require(
            required_file.is_file(),
            f"Missing isolated SoundTrigger service file: {required_file}",
        )

    legacy_bp_text = legacy_bp.read_text()
    legacy_source_text = legacy_source.read_text()
    legacy_init_text = legacy_init.read_text()

    require(
        "android_hardware_audio,run_64bit,true" in product,
        "I001D must keep its boot-proven 64-bit combined audio service. "
        "A 32-bit SoundTrigger path must use a separate process.",
    )
    require(
        "android.hardware.soundtrigger@2.3-impl" in product,
        "SoundTrigger 2.3 implementation is not included in the product.",
    )
    require(
        "android.hardware.soundtrigger@2.3-service.i001d" in product,
        "The isolated 32-bit SoundTrigger service is not in the product.",
    )
    require(
        'name: "android.hardware.soundtrigger@2.3-service.i001d"' in legacy_bp_text
        and 'compile_multilib: "32"' in legacy_bp_text,
        "The isolated SoundTrigger service must be explicitly compiled 32-bit.",
    )
    require(
        'registerPassthroughServiceImplementation(' in legacy_source_text
        and "android.hardware.soundtrigger@2.3::ISoundTriggerHw"
        in legacy_source_text,
        "The isolated service does not register SoundTrigger HIDL 2.3.",
    )
    for unrelated_interface in (
        "IDevicesFactory",
        "IEffectsFactory",
        "IBluetoothAudioProvidersFactory",
    ):
        require(
            unrelated_interface not in legacy_source_text,
            f"The isolated service must not register {unrelated_interface}.",
        )
    for forbidden_directive in ("critical", "reboot_on_failure", "onrestart"):
        require(
            forbidden_directive not in legacy_init_text,
            f"Boot-unsafe init directive found: {forbidden_directive}.",
        )
    require(
        "android.hardware.soundtrigger@2.3::ISoundTriggerHw default"
        in legacy_init_text,
        "The legacy init service does not declare its HIDL interface.",
    )
    require(
        "ro.vendor.audio.soundtrigger.separate_service=true"
        in vendor_properties,
        "The I001D separate-SoundTrigger property is not enabled.",
    )
    require(
        "android\\.hardware\\.soundtrigger@2\\.3-service\\.i001d"
        in vendor_file_contexts
        and "hal_audio_default_exec" in vendor_file_contexts,
        "The isolated service executable lacks its HAL audio file context.",
    )
    combined_service = (
        ROOT
        / "hardware/interfaces/audio/common/all-versions/default/service/service.cpp"
    )
    if combined_service.exists():
        combined_service_text = combined_service.read_text()
        require(
            "ro.vendor.audio.soundtrigger.separate_service"
            in combined_service_text
            and "property_get_bool" in combined_service_text,
            "The 64-bit combined service does not honor separate ownership.",
        )
    else:
        mirror_patch = (
            ROOT.parent.parent
            / "rom-code/patches/0001-i001d-separate-soundtrigger-registration.patch"
        )
        require(
            mirror_patch.is_file()
            and "ro.vendor.audio.soundtrigger.separate_service"
            in mirror_patch.read_text(),
            "The partial local mirror lacks the shared audio-service patch.",
        )
    if vendor_makefile_path.exists():
        vendor_makefile = vendor_makefile_path.read_text()
        for library in ("libsmwrapper.so", "libacdbloader.so"):
            copy_rule = (
                f"proprietary/vendor/lib/{library}:"
                f"$(TARGET_COPY_OUT_VENDOR)/lib/{library}"
            )
            require(
                copy_rule in vendor_makefile,
                f"Missing 32-bit vendor copy rule for {library}.",
            )

    require(
        "android.intent.category.LAUNCHER" not in broker_manifest,
        "The ROM broker must be a headless service, not a launcher app.",
    )
    require(
        "com.frankenbridge.assistant.SETUP" not in broker_manifest,
        "The obsolete broker setup activity is still externally exposed.",
    )
    require(
        ".BridgeBrokerService" in broker_manifest,
        "The broker Binder service was accidentally removed.",
    )
    require(
        "FrankensteinBridgeTest" not in product_packages,
        "The obsolete bridge test app must not be baked into the product.",
    )

    print("Frankenstein ROM integration static checks passed.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as error:
        print(f"FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
