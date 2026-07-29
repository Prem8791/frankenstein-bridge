import json
import tempfile
import unittest
from pathlib import Path

from frankenstein_inventory.runtime import (
    _load_properties,
    parse_apex,
    parse_cmd_list,
    parse_device_nodes,
    parse_dumpsys_list,
    parse_features,
    parse_getprop_names,
    parse_init_states,
    parse_lshal,
    parse_overlay,
    parse_processes,
    parse_service_list,
    parse_unix_sockets,
)


class RuntimeParserTest(unittest.TestCase):
    def test_partition_product_device_resolves_to_runtime_product_device(self):
        with tempfile.TemporaryDirectory() as temp:
            product_out = Path(temp)
            (product_out / "system").mkdir()
            (product_out / "system/build.prop").write_text(
                "ro.product.system.device=I001D\n", encoding="utf-8"
            )
            properties = _load_properties(product_out)
            self.assertEqual("I001D", properties["ro.product.device"])

    def test_getprop_keeps_names_not_values(self):
        output = "[ro.build.id]: [SECRET_BUILD]\n[persist.vendor.foo]: [SECRET_VALUE]\n"
        rendered = json.dumps([r.to_dict() for r in parse_getprop_names(output)])
        self.assertIn("ro.build.id", rendered)
        self.assertIn("persist.vendor.foo", rendered)
        self.assertNotIn("SECRET_BUILD", rendered)
        self.assertNotIn("SECRET_VALUE", rendered)

    def test_init_states_excludes_debug_pid(self):
        output = "[init.svc.netd]: [running]\n[init.svc_debug_pid.netd]: [1234]\n"
        records = parse_init_states(output)
        self.assertEqual([("netd", "running")], [(r.name, r.availability_condition) for r in records])

    def test_service_list(self):
        record = parse_service_list("0 activity: [android.app.IActivityManager]\n")[0]
        self.assertEqual("activity", record.name)
        self.assertEqual("android.app.IActivityManager", record.interface_descriptor)

    def test_other_runtime_parsers(self):
        self.assertEqual("activity", parse_dumpsys_list("activity\n")[0].name)
        self.assertEqual("package", parse_cmd_list("  package\n")[0].name)
        self.assertEqual("android.hardware.demo", parse_features("feature:android.hardware.demo\n")[0].name)
        self.assertEqual("/dev/demo", parse_device_nodes("/dev/demo\n")[0].name)
        self.assertEqual("@demo", parse_unix_sockets("000: 0 0 0 0 0 0 @demo\n")[0].name)
        self.assertEqual("system_server", parse_processes("u:r:system_server:s0 system system_server\n")[0].name)
        self.assertTrue(parse_lshal("android.hardware.foo@1.0::IFoo/default\n"))
        self.assertTrue(parse_apex("moduleName: com.android.demo\nisActive: true\n"))
        self.assertTrue(parse_overlay("[x] com.demo.overlay\n"))


if __name__ == "__main__":
    unittest.main()
