# Booting ROM source baseline — 2026-07-29

This directory records the source state that produced the confirmed-booting
Bliss 19.6 Android 16 GApps build for ASUS I001D:

`Bliss-v19.6-I001D-UNOFFICIAL-gapps-20260729.zip`

SHA-256:
`99a6975b659eb6f83de3897010b4fbde16ac2158ba6a6da001b705cfe2aa3428`

The `aosp/` directory is an overlay, not a complete Android checkout. Copy its
contents over an AOSP tree checked out at the revisions below, then remove the
paths in `deleted-paths.txt`.

## Base revisions

| Project | Revision |
|---|---|
| `frameworks/base` | `5e418bcbe12c6e91d285721e5d922b815c9f8841` |
| `system/core` | `0d6db2bb104f0e63ae514d175d29a49584c251c3` |
| `system/sepolicy` | `d47c04ee29fd4570078c2111e87eae160d11c102` |
| `device/asus/I001D` | `2c4bd7c6077a1b3f29e679b455aed402c5464aab` |
| `device/asus/sm8150-common` | `d9a3a18b21284587e3e98396bcfb5d8df710cfee` |
| `vendor/bliss` | `4820a2a528d4847dc075930831ca2feb52de4b9b` |

Manifest `default.xml` SHA-256:
`1d1bf13fd424ada3457991edd82e606f6cd48ab9c5a6cb91cc8b43c3594f87c2`

## Included

- Every tracked file modified in the six projects above, captured as its final
  booting content.
- Every untracked source/configuration file in those projects, excluding Python
  bytecode caches and private signing material.
- `packages/apps/FrankensteinApps`, including the broker and the small ProdX ROM
  bootstrap. Gradle caches and build outputs are excluded.
- The product inheritance line in
  `device/asus/I001D/bliss_I001D.mk`.
- The I001D `prebuilt/dtbo.img` used by the source tree.

The separately installed full ProdX application is maintained in the sibling
`ProdXAssistant` Git repository. The matching local commits are:

- `97e39dd` — privileged SoundTrigger wake infrastructure
- `c9cd8bf` — retire the LFM runtime in favor of MiniCPM
- `c3c4047` — prepare the full app to update the ROM bootstrap
- `a80258f` — record the bootstrap callback correction

## Deliberately excluded from Git

- Android build output under `out/`
- OTA images other than the small device `dtbo.img` source prebuilt
- Python `__pycache__` and `.pyc` files
- Personal signing private keys

The exact I001D signing directory was copied locally, outside all Git
repositories, to:

`/home/home/bliss/.rom-private/I001D-security/`

Keep that directory private. It contains 18 certificate/key files and is
required to reproduce the same signing identity.

