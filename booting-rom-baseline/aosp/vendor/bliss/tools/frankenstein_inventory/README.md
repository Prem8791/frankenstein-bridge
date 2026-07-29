# Frankenstein OS Surface Inventory

This standard-library Python tool inventories the final Waterlily source tree,
assembled I001D product, and one matching runtime device. It writes no property
values except explicit build identity and public `init.svc.*` state, reads no
protected artifact contents, and redacts the adb serial.

Run as `leimapokpampremika` with the build-host adb and loopback SSH tunnel:

```bash
export PATH="$PWD/out/host/linux-x86/bin:$PATH"
export ANDROID_ADB_SERVER_PORT=15037
PYTHONPATH=vendor/bliss/tools/frankenstein_inventory \
python3 -m frankenstein_inventory collect \
  --tree "$PWD" \
  --product-out "$PWD/out/target/product/I001D" \
  --serial "$FRANKENSTEIN_DEVICE_SERIAL" \
  --output device/asus/sm8150-common/frankenstein/inventory
```

Verify existing evidence without adb or writes:

```bash
PYTHONPATH=vendor/bliss/tools/frankenstein_inventory \
python3 -m frankenstein_inventory verify \
  --output device/asus/sm8150-common/frankenstein/inventory
```

The collector refuses a non-empty destination, uses atomic directory
publication, retains every evidence reference, and leaves all records
`UNCLASSIFIED` with reachability `UNTESTED`. Classification is Stage 2.
