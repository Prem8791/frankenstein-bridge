import tempfile
import unittest
from pathlib import Path

from frankenstein_inventory.static import (
    _decode,
    _is_binary_payload,
    parse_context_file,
    parse_init_file,
    parse_java_native_registrations,
    parse_permissions_xml,
    parse_vintf_xml,
)


class StaticParserTest(unittest.TestCase):
    def test_binary_android_xml_payload_is_not_treated_as_source_text(self):
        self.assertTrue(_is_binary_payload(bytes.fromhex(
            "03000800a01d000001001c00780200003000000000000000"
        )))
        self.assertFalse(_is_binary_payload(b"<?xml version=\"1.0\"?>\n"))

    def test_decode_accepts_tracked_latin1_source_without_nul(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "Legacy.java"
            path.write_bytes(b"// copyright \xa9 legacy\nclass Legacy {}\n")
            self.assertIn("copyright", _decode(path))

    def test_java_registration_parser_handles_comments_multiline_and_consumers(self):
        source = """
// ServiceManager.addService("commented", fake);
ServiceManager.addService(
    "real", makeService(foo(bar)));
LocalServices.addService(FooInternal.class, foo);
FooInternal f = LocalServices.getService(FooInternal.class);
LocalManagerRegistry.addManager(BarManager.class, bar);
LocalManagerRegistry.getManager(BarManager.class);
AServiceManager_addService(nativeBinder, "native.real");
"""
        records = parse_java_native_registrations(source, "Owner.java", "system")
        identities = {(r.kind, r.name) for r in records}
        self.assertIn(("BINDER_SERVICE", "real"), identities)
        self.assertIn(("BINDER_SERVICE", "native.real"), identities)
        self.assertIn(("LOCAL_SERVICE_PRODUCER", "FooInternal"), identities)
        self.assertIn(("LOCAL_SERVICE_CONSUMER", "FooInternal"), identities)
        self.assertIn(("LOCAL_MANAGER_PRODUCER", "BarManager"), identities)
        self.assertIn(("LOCAL_MANAGER_CONSUMER", "BarManager"), identities)
        self.assertNotIn(("BINDER_SERVICE", "commented"), identities)

    def test_init_parser_handles_continuation(self):
        records = parse_init_file(
            "service demo /system/bin/demo \\\n"
            "    --flag\n"
            "    class late_start\n",
            "system/etc/init/demo.rc",
            "system",
        )
        self.assertEqual(1, len(records))
        self.assertEqual("demo", records[0].name)
        self.assertEqual("/system/bin/demo", records[0].owner)

    def test_vintf_parser_handles_aidl_and_hidl(self):
        xml = """<manifest version="1.0" type="device">
          <hal format="aidl"><name>android.hardware.foo</name><version>2</version>
            <interface><name>IFoo</name><instance>default</instance></interface></hal>
          <hal format="hidl"><name>vendor.demo</name><transport>hwbinder</transport>
            <version>1.1</version><interface><name>IDemo</name>
            <instance>default</instance></interface></hal>
        </manifest>"""
        records = parse_vintf_xml(xml, "vendor/etc/vintf/manifest.xml", "vendor")
        self.assertEqual(
            {"VINTF_AIDL_INSTANCE", "VINTF_HIDL_INSTANCE"},
            {record.kind for record in records},
        )

    def test_context_parser_preserves_selector_and_label(self):
        records = parse_context_file(
            "# comment\nactivity u:object_r:activity_service:s0\n",
            "system/etc/selinux/plat_service_contexts",
            "system",
            "SERVICE_CONTEXT",
        )
        self.assertEqual("activity", records[0].name)
        self.assertEqual("u:object_r:activity_service:s0", records[0].selinux_context)

    def test_permissions_parser_records_features_and_privapp_pairs(self):
        xml = """<permissions>
          <feature name="android.hardware.demo"/>
          <privapp-permissions package="com.demo">
            <permission name="android.permission.DEMO"/>
            <deny-permission name="android.permission.NO"/>
          </privapp-permissions>
        </permissions>"""
        records = parse_permissions_xml(xml, "system/etc/permissions/demo.xml", "system")
        self.assertIn(
            ("SYSTEM_FEATURE", "android.hardware.demo", ""),
            {(r.kind, r.name, r.instance) for r in records},
        )
        self.assertIn(
            ("PRIVAPP_PERMISSION_GRANT", "com.demo", "grant:android.permission.DEMO"),
            {(r.kind, r.name, r.instance) for r in records},
        )


if __name__ == "__main__":
    unittest.main()
