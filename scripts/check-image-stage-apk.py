#!/usr/bin/env python3
"""Bounded byte checks for the four independent synthetic assets in a standalone probe APK."""
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = {
    'offer-h1.bin': 'test-fixtures/synthetic/image-offer-proposal/offer-h1.bin',
    'offer-h2.bin': 'test-fixtures/synthetic/image-offer-proposal/offer-h2.bin',
    'h1.png': 'images/src/test/resources/image-transfer/h1.png',
    'h2.png': 'images/src/test/resources/image-transfer/h2.png',
}


def check(apk):
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        for name, source in FIXTURES.items():
            path = 'assets/' + name
            expected = (ROOT / source).read_bytes()
            if names.count(path) != 1 or archive.getinfo(path).file_size != len(expected):
                raise ValueError('Missing, duplicated or wrongly sized synthetic probe asset')
            if archive.read(path) != expected:
                raise ValueError('Changed synthetic probe asset bytes')


if __name__ == '__main__':
    candidates = [Path(sys.argv[1])] if len(sys.argv) == 2 else list((ROOT / 'images/build/outputs/apk/androidTest/debug').glob('*.apk'))
    if len(sys.argv) > 2 or len(candidates) != 1:
        sys.exit('Build/select exactly one standalone instrumentation APK')
    check(candidates[0])
    print('Standalone APK contains all four exact independent descriptor/PNG assets. No native runtime result implied.')
