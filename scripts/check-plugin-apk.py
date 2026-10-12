#!/usr/bin/env python3
"""Check the development APK's discovery and packaging boundaries without installing it."""
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile


def main():
    if len(sys.argv) != 2 or not os.environ.get('ANDROID_HOME'):
        sys.exit('Usage: ANDROID_HOME=/path/to/android-sdk python3 scripts/check-plugin-apk.py APK')
    apk = Path(sys.argv[1])
    aapt = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0/aapt2'
    signer = aapt.with_name('apksigner')
    subprocess.run([str(signer), 'verify', str(apk)], check=True, timeout=30)
    badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(apk)], text=True, timeout=30)
    manifest = subprocess.check_output(
        [str(aapt), 'dump', 'xmltree', str(apk), '--file', 'AndroidManifest.xml'],
        text=True, timeout=30)
    assert "name='se.tmm.sensorplanner.plugin'" in badging, 'Unexpected package'
    assert 'uses-permission:' not in badging, 'The loading probe must request no permissions'
    assert 'com.atakmap.app@5.6.0.CIV' in manifest, 'Wrong host API'
    assert 'com.atakmap.app.component' in manifest, 'Missing ATAK discovery component'
    with zipfile.ZipFile(apk) as archive:
        entries = archive.namelist()
        assert 'classes.dex' in entries, 'Missing compiled entry'
        assert not any(name.endswith(('.jar', '.jks', '.keystore', '.key', '.pem'))
                       or name.endswith('android_keystore') for name in entries), 'Private SDK/signing files packaged'
        extension = ET.fromstring(archive.read('assets/plugin.xml')).find('extension')
        assert extension is not None, 'Missing plugin registration'
        assert extension.get('type') == 'gov.tak.api.plugin.IPlugin', 'Wrong extension interface'
        assert extension.get('impl') == 'se.tmm.sensorplanner.plugin.SensorPlannerPlugin', 'Wrong entry class'
    print('APK signature, discovery, permission and packaging checks passed. Runtime loading is not tested.')


if __name__ == '__main__':
    main()
