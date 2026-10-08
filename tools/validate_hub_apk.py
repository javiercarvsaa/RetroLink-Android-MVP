#!/usr/bin/env python3
"""Inspect a real APK, its native libraries, compiled classes, metadata and signing cert.

This does not launch Android and does not certify game compatibility or update continuity.
"""
from pathlib import Path
import hashlib
import json
import os
import re
import struct
import subprocess
import sys
import zipfile

PREVIOUS_CERTS = {
    'v1.4.0d': '1a870dd02005ac3b0f98526dce106d20876310eddd6f88b2ea86c4e5e0feb36e',
    'v1.5.0d': 'ee2728a92ffe08ace0c8663fb948d25fb5d03866a2e4c500e46fdd339887723c',
}
LIBRARIES = ['libretro_n64.so', 'libretro_gambatte.so', 'libretro_snes9x.so',
    'libretro_atari2600.so', 'libretro_ps1.so', 'libppsspp_jni.so', 'libretrolink_native.so']
CLASS_NAMES = ['RetroHubActivity', 'HubCatalog', 'HubGame', 'HubStore', 'HubCoverLoader',
    'MainActivity', 'AdaptiveRuntimeSession', 'OptimizationSettingsActivity',
    'Ps1GameActivity', 'Ps1ControllerActivity', 'SnesControllerActivity',
    'Atari2600ControllerActivity', 'ControllerActivity', 'PspRoomActivity', 'GameBoyLinkActivity']

def dex_classes(data):
    if not data.startswith(b'dex\n'): raise ValueError('Invalid DEX magic')
    def u4(offset): return struct.unpack_from('<I', data, offset)[0]
    def dex_string(offset):
        while True:
            value = data[offset]; offset += 1
            if value < 128: break
        return data[offset:data.index(b'\0', offset)].decode('utf-8', errors='replace')
    strings = [dex_string(u4(u4(60) + 4*i)) for i in range(u4(56))]
    types = [strings[u4(u4(68) + 4*i)] for i in range(u4(64))]
    return {types[u4(u4(100) + 32*i)] for i in range(u4(96))}

def main():
    apk = Path(sys.argv[1]); out = Path(sys.argv[2]); out.mkdir(parents=True, exist_ok=True)
    sdk = Path(os.environ['ANDROID_SDK_ROOT'])/'build-tools/36.0.0'
    native = {}
    classes = set()
    with zipfile.ZipFile(apk) as archive:
        assert archive.testzip() is None, 'APK ZIP CRC failed'
        for lib in LIBRARIES:
            entry = 'lib/arm64-v8a/' + lib
            data = archive.read(entry)
            assert data[:4] == b'\x7fELF' and data[4] == 2 and data[5] == 1, entry
            assert struct.unpack_from('<H', data, 18)[0] == 183, f'Not AArch64: {entry}'
            native[lib] = {'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
        for name in ['assets/PPSSPP_LICENSE.txt', 'assets/NOTICE_PPSSPP.txt', 'assets/NOTICE_RETROSR_SGSR.txt']:
            assert archive.getinfo(name).file_size > 0, name
        for name in archive.namelist():
            if re.fullmatch(r'classes\d*\.dex', name): classes.update(dex_classes(archive.read(name)))
    for name in CLASS_NAMES:
        assert 'Lcl/retrolink/app/' + name + ';' in classes, f'Class missing from APK: {name}'
    assert 'Lorg/ppsspp/ppsspp/PpssppActivity;' in classes, 'PSP activity missing'
    badging = subprocess.check_output([str(sdk/'aapt'), 'dump', 'badging', str(apk)], text=True)
    assert "name='cl.retrolink.app'" in badging
    assert "versionCode='68'" in badging and "versionName='1.6.0-rc1'" in badging
    assert "launchable-activity: name='cl.retrolink.app.RetroHubActivity'" in badging
    assert "sdkVersion:'26'" in badging
    signature = subprocess.check_output([str(sdk/'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)],
                                        text=True, stderr=subprocess.STDOUT)
    fingerprints = [v.replace(':','').lower() for v in re.findall(r'Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F:]+)', signature)]
    assert fingerprints, 'No verified signing certificate'
    matches = [version for version, digest in PREVIOUS_CERTS.items() if digest in fingerprints]
    continuity = ('Certificate matches ' + ', '.join(matches) + '; actual installed app still needs checking.'
                  if matches else 'No matching certificate for either supplied previous APK. Do not uninstall the existing app to force installation.')
    report = {
        'apk': apk.name, 'bytes': apk.stat().st_size, 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
        'base_source_commit': '9379168238fbc3d78fc284744536c95a0ef88718',
        'workflow_run': os.environ.get('GITHUB_RUN_ID'), 'workflow_commit': os.environ.get('GITHUB_SHA'),
        'version_code': 68, 'version_name': '1.6.0-rc1', 'native_libraries': native,
        'new_and_preserved_classes_checked': CLASS_NAMES, 'apk_crc': 'PASS', 'signature_verification': 'PASS',
        'signing_certificate_sha256': fingerprints, 'known_certificate_matches': matches,
        'signing_continuity_note': continuity, 'physical_devices_tested': [],
        'gameplay_p2_audio_saves': 'NOT TESTED by this script', 'performance_comparison': 'NOT MEASURED',
    }
    (out/'apk-verification.json').write_text(json.dumps(report, indent=2) + '\n')
    (out/'signing-report.txt').write_text(signature + '\n' + continuity + '\n')
    (out/'android-package-metadata.txt').write_text(badging)
    print('APK CRC, seven AArch64 libraries, compiled UI/legacy classes, package metadata and signature PASS')
    print(continuity)
    if not matches: print('::warning::New signing certificate: update continuity is NOT confirmed. Preserve the old installation and saved data.')

if __name__ == '__main__': main()
