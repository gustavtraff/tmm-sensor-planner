#!/usr/bin/env python3
"""Offline synthetic ZIP controls for clean native-probe asset packaging."""
from pathlib import Path
import runpy
import tempfile
import unittest
import warnings
import zipfile

CHECKER = runpy.run_path(str(Path(__file__).with_name('check-image-stage-apk.py')))


class ProbeAssetTest(unittest.TestCase):
    def test_exact_missing_changed_duplicate_and_oversize_assets(self):
        for kind in ['valid', 'missing', 'changed', 'duplicate', 'oversize']:
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as directory:
                apk = Path(directory) / 'synthetic.apk'
                with warnings.catch_warnings():
                    warnings.simplefilter('ignore', UserWarning)
                    with zipfile.ZipFile(apk, 'w') as archive:
                        for index, (name, source) in enumerate(CHECKER['FIXTURES'].items()):
                            data = (CHECKER['ROOT'] / source).read_bytes()
                            if index == 0:
                                if kind == 'missing':
                                    continue
                                if kind == 'changed':
                                    data = bytes([data[0] ^ 1]) + data[1:]
                                if kind == 'oversize':
                                    data += b'x'
                                if kind == 'duplicate':
                                    archive.writestr('assets/' + name, data)
                            archive.writestr('assets/' + name, data)
                if kind == 'valid':
                    CHECKER['check'](apk)
                else:
                    with self.assertRaises(ValueError):
                        CHECKER['check'](apk)


if __name__ == '__main__':
    unittest.main()
