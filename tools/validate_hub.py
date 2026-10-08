#!/usr/bin/env python3
"""CI gate: source identity, manifest, resource and routing checks, not gameplay tests."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET

BASE = '9379168238fbc3d78fc284744536c95a0ef88718'
A = '{http://schemas.android.com/apk/res/android}'

def blob_hash(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()

def check():
    protected = subprocess.check_output(['git', 'ls-tree', '-r', '-z', BASE, '--',
        'app/src/main/java', 'app/src/main/cpp', 'app/src/main/assets',
        'app/src/main/res/layout', 'ppsspp-engine/build.gradle', 'settings.gradle'])
    checked = 0
    for record in protected.split(b'\0'):
        if not record: continue
        metadata, name = record.split(b'\t', 1)
        mode, kind, digest = metadata.decode().split()
        if kind != 'blob': continue
        path = Path(name.decode())
        assert path.is_file(), f'Protected file missing: {path}'
        assert blob_hash(path.read_bytes()) == digest, f'Protected source changed: {path}'
        checked += 1
    manifest = ET.parse('app/src/main/AndroidManifest.xml').getroot()
    base_manifest = ET.fromstring(subprocess.check_output(['git', 'show', BASE + ':app/src/main/AndroidManifest.xml']))
    permissions = lambda m: sorted(tuple(sorted(n.attrib.items())) for n in m.findall('uses-permission'))
    assert permissions(manifest) == permissions(base_manifest), 'Permission regression'
    app = manifest.find('application'); old_app = base_manifest.find('application')
    for k, v in old_app.attrib.items():
        if k not in [A+'icon', A+'roundIcon']: assert app.get(k) == v, f'Application attribute changed: {k}'
    activities = {a.get(A+'name'): a for a in app.findall('activity')}
    for old in old_app.findall('activity'):
        name = old.get(A+'name'); assert name in activities
        for k, v in old.attrib.items():
            if name == '.MainActivity' and k == A+'exported': continue
            assert activities[name].get(k) == v, f'Activity attribute changed: {name}/{k}'
    launchers = [a for a in app.findall('activity') if any(c.get(A+'name') == 'android.intent.category.LAUNCHER' for c in a.findall('intent-filter/category'))]
    assert len(launchers) == 1 and launchers[0].get(A+'name') == '.RetroHubActivity'
    assert activities['.MainActivity'].get(A+'exported') == 'false'
    build = Path('app/build.gradle').read_text()
    assert 'versionCode 68' in build and 'versionName "1.6.0-rc1"' in build
    old_build = subprocess.check_output(['git', 'show', BASE + ':app/build.gradle']).decode()
    assert build == old_build.replace('versionCode 67', 'versionCode 68').replace('versionName "1.5.0-rc1"', 'versionName "1.6.0-rc1"')
    for p in Path('app/src/main/res').rglob('*.xml'): ET.parse(p)
    assert not list(Path('app/src/main/res').rglob('*.b64'))
    java = Path('app/src/main/java/cl/retrolink/app')
    catalog = (java/'HubCatalog.java').read_text()
    registry = (java/'CoreRegistry.java').read_text()
    for id in ['psp.ppsspp', 'ps1.pcsx_rearmed', 'n64.mupen64plus-next', 'snes.snes9x', 'atari2600.stella2014', 'gb.gambatte']:
        assert id in catalog and id in registry
    for target in re.findall(r'\b(\w+Activity)\.class', catalog): assert '.'+target in activities, f'Unregistered route: {target}'
    assert 'forFileName(' not in catalog and 'byId(' not in catalog
    assert 'Mode.SINGLE' in catalog and 'PspLauncher.launchGame' in catalog
    source = '\n'.join((java/n).read_text() for n in ['HubGame.java','HubStore.java','HubCatalog.java','HubCoverLoader.java','RetroHubActivity.java'])
    for token in ['HttpURLConnection','openConnection(', 'nativeSetFrontendOption', 'setGraphicsProfile(', 'import tensorflow', 'litert', 'deleteRecursive', '.delete()']:
        assert token not in source, f'Unexpected operation in UI overlay: {token}'
    assert '4 * 1024 * 1024' in source and 'new ArrayBlockingQueue<>(24)' in source
    assert 'Thread.currentThread().isInterrupted()' in source
    assert 'getOrDefault(game.key, 0L)' in source
    assert 'dp(48)' in source and 'onSaveInstanceState' in source and 'pendingN64Controller' in source
    assert 'MainActivity.class' in (java/'RetroHubActivity.java').read_text()
    print(f'Immutable baseline: {checked} original files PASS; launcher/resources/6 routes PASS')

if __name__ == '__main__': check()
