# RetroLink v1.6.0 RC1 — Source overlay

This ZIP is an incremental overlay, NOT a complete emulator repository and NOT an APK.
Base commit: `9379168238fbc3d78fc284744536c95a0ef88718`.

The companion `build-retrolink-v1.6.0-library-hub.yml` contains this exact source
archive and compiles it on GitHub Actions. Upload only that workflow to the
repository's `.github/workflows/` directory on `main`.

For a manual code review, use a clean checkout of the base commit, copy the new
files from this archive into it, and run `python3 tools/apply_hub.py` followed by
`python3 tools/validate_hub.py`. These scripts verify the old sources rather than
silently replacing them. Android SDK/NDK and pinned emulation engines are still
required for an actual build; see the workflow and Spanish scope document.

Checksums in `SOURCE_MANIFEST.json` cover the included source files, not this ZIP
and not the final APK. No private signing key, ROM, BIOS, saves or external model
is included. Read the signing/installation warning in `docs/RETROLINK_V1_6_HUB.md`.
