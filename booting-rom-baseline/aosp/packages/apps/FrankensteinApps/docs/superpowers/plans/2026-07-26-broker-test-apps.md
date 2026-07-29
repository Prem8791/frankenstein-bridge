# Broker and Test App Implementation Plan

**Goal:** Prove the complete ordinary-app → platform-signed broker → ROM bridge path on the connected I001D.

**Architecture:** Keep replaceable app code outside the ROM tree in one multi-module project. `broker-api` owns the app-facing AIDL contract, `broker` is platform signed and is the only app allowed to locate the ROM bridge, and `tester` is an ordinary app that knows only the broker contract. The first operation checks the bridge Binder descriptor and liveness; capability DTO mapping is intentionally deferred until this trust path is proven.

1. Scaffold `broker-api`, `broker`, and `tester` Android modules.
2. Define `IBridgeBroker.probeBridge()` in broker-owned AIDL.
3. Implement an exported broker service that authorizes only the tester package and probes the `frankenstein` Binder.
4. Implement a tester screen with a button and a result label.
5. Build both apps, platform-sign the broker with the preserved ROM key, install both, and launch the tester.

