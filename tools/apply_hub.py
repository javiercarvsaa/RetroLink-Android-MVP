#!/usr/bin/env python3
"""Apply only launcher/icon/version changes to the exact inspected configuration files."""
from pathlib import Path
import hashlib
import re
import sys
import xml.etree.ElementTree as ET

BASE_SHA = '9379168238fbc3d78fc284744536c95a0ef88718'
EXPECTED = {
    'app/build.gradle': 'c2e88a9165a723db1c22a324592d12aa98a5dcfb',
    'app/src/main/AndroidManifest.xml': 'b42ddb4679a3ae81d03c5877854b072328d8264e',
}
A = '{http://schemas.android.com/apk/res/android}'

def git_blob(data: bytes) -> str:
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()

def transformed(build: str, manifest: str) -> tuple[str, str]:
    assert build.count('versionCode 67') == 1
    assert build.count('versionName "1.5.0-rc1"') == 1
    build = build.replace('versionCode 67', 'versionCode 68', 1).replace('versionName "1.5.0-rc1"', 'versionName "1.6.0-rc1"', 1)
    main = re.search(r'<activity\s+android:name="\.MainActivity"[\s\S]*?</activity>', manifest)
    assert main, 'The existing launcher activity was not found'
    old = main.group(0)
    assert 'android.intent.category.LAUNCHER' in old and 'android.intent.action.MAIN' in old
    classic = re.sub(r'\s*<intent-filter>[\s\S]*?</intent-filter>', '', old, count=1)
    classic = classic.replace('android:exported="true"', 'android:exported="false"', 1)
    manifest = manifest[:main.start()] + classic + manifest[main.end():]
    assert 'android:name=".RetroHubActivity"' not in manifest
    assert manifest.count('android:icon="@mipmap/ic_launcher"') == 1
    assert manifest.count('android:roundIcon="@mipmap/ic_launcher_round"') == 1
    manifest = manifest.replace('android:icon="@mipmap/ic_launcher"', 'android:icon="@mipmap/hub_launcher"', 1)
    manifest = manifest.replace('android:roundIcon="@mipmap/ic_launcher_round"', 'android:roundIcon="@mipmap/hub_launcher"', 1)
    activity = '''        <activity
            android:name=".RetroHubActivity"
            android:exported="true"
            android:enableOnBackInvokedCallback="true"
            android:screenOrientation="sensorLandscape"
            android:windowSoftInputMode="adjustResize|stateAlwaysHidden">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
'''
    manifest = manifest.replace('    </application>', activity + '    </application>', 1)
    ET.fromstring(manifest)
    return build, manifest

def main() -> None:
    original = {}
    for name, digest in EXPECTED.items():
        data = Path(name).read_bytes()
        if git_blob(data) != digest:
            raise SystemExit(f'Base changed: {name}. Expected source from {BASE_SHA}. No configuration files were overwritten.')
        original[name] = data.decode('utf-8')
    build, manifest = transformed(original['app/build.gradle'], original['app/src/main/AndroidManifest.xml'])
    for name, content in [('app/build.gradle', build), ('app/src/main/AndroidManifest.xml', manifest)]:
        Path(name).write_text(content, encoding='utf-8')
    print('Launcher, icon and version applied; no existing Java/core/save/transport source modified.')

if __name__ == '__main__': main()
