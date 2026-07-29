# ProdX privileged bootstrap

The ROM builds the small source module in `prodx-bootstrap/` as package
`com.prodx.assistant`. It establishes ProdX's platform signature, privileged
placement, VoiceInteractionService component names, and protected SoundTrigger
permission allowlist without including AI models or native inference runtimes.

Install the complete ProdX APK afterward as an updated system app. It must:

- use package `com.prodx.assistant`;
- be signed with the same ROM platform certificate;
- use a version code greater than the platform bootstrap version.

The complete app currently uses version code 100. Future updates should
increment it. Removing the update rolls back to this bootstrap.
