#!/usr/bin/env python3
"""Offline controls: invalid/physical/connected targets must be rejected before install or instrumentation."""
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import subprocess
import sys
import unittest
from unittest import mock

sys.dont_write_bytecode = True
SPEC = importlib.util.spec_from_file_location('stage_android', Path(__file__).with_name('test-image-stage-android.py'))
PROBE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PROBE)


class AndroidProbeGuards(unittest.TestCase):
    def test_invalid_arguments_never_start_a_command_or_require_an_sdk(self):
        run_id = '00000000-0000-4000-8150-000000000001'
        for args in [[], ['--serial', 'SYNTHETIC-PHONE'], ['--serial', 'emulator-5584', '--adb-port', '0'],
                     ['--serial', 'emulator-5584', '--read-only'],
                     ['--serial', 'emulator-5584', '--run-id', '00000000-0000-0000-0000-000000000000'],
                     ['--serial', 'emulator-5584', '--run-id', 'invalid'],
                     ['--serial', 'emulator-5584', '--read-only', '--run-id', run_id, '--check-filesystem'],
                     ['--serial', 'emulator-5584', '--verify-filesystem'],
                     ['--serial', 'emulator-5584', '--discard', '--kill-after', 'PNG_SYNCED'],
                     ['--serial', 'emulator-5584', '--kill-after', 'DISCARD_OFFER_REMOVED'],
                     ['--serial', 'emulator-5584', '--discard', '--check-filesystem']]:
            with self.subTest(args=args), mock.patch.dict(os.environ, {}, clear=True), \
                    mock.patch.object(sys, 'argv', ['probe', *args]), mock.patch.object(subprocess, 'run') as command, \
                    contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit) as failure:
                    PROBE.main()
                self.assertEqual(2, failure.exception.code)
                command.assert_not_called()

    def test_runtime_and_network_guards_precede_apk_inspection_or_install(self):
        for replies, message in [(['25\n'], 'API 26'), (['35\n', '0\n'], 'not an emulator'),
                                 (['35\n', '1\n', '0\n'], 'airplane mode'),
                                 (['35\n', '1\n', '1\n', 'default via synthetic\n'], 'airplane mode')]:
            with self.subTest(replies=replies), mock.patch.dict(os.environ, {'ANDROID_HOME': '/synthetic-sdk'}), \
                    mock.patch.object(sys, 'argv', ['probe', '--serial', 'emulator-5584']), \
                    mock.patch.object(subprocess, 'run', side_effect=[subprocess.CompletedProcess([], 0, stdout=s) for s in replies]) as command:
                with self.assertRaisesRegex(RuntimeError, message):
                    PROBE.main()
                self.assertEqual(len(replies), command.call_count)
                for call in command.call_args_list:
                    self.assertIn('shell', call.args[0])
                    self.assertNotIn('install', call.args[0])
                    self.assertNotIn('instrument', call.args[0])


if __name__ == '__main__':
    unittest.main()
