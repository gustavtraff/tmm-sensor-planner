#!/usr/bin/env python3
"""Run only the standalone synthetic storage probe on an explicitly selected offline emulator."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'se.tmm.sensorplanner.images.test'
STAGE_POINTS = ['PENDING_CREATED', 'OFFER_SYNCED', 'PNG_SYNCED', 'PENDING_VERIFIED', 'RENAMED', 'READY_VERIFIED']
DISCARD_POINTS = ['DISCARD_MARKED_PENDING', 'DISCARD_OFFER_REMOVED', 'DISCARD_PNG_REMOVED', 'DISCARD_DIRECTORY_REMOVED']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--adb-port', type=int, default=5037)
    parser.add_argument('--run-id', help='Original synthetic run UUID for an explicit repeat read')
    parser.add_argument('--read-only', action='store_true', help='Read the original run after restart; do not install or write')
    parser.add_argument('--kill-after', choices=STAGE_POINTS + DISCARD_POINTS)
    parser.add_argument('--discard', action='store_true', help='Run the explicit-discard backend probe on fresh synthetic entries')
    parser.add_argument('--check-filesystem', action='store_true', help='Also mutate new synthetic quota/symlink/marker fixtures')
    parser.add_argument('--verify-filesystem', action='store_true', help='Reopen previously created filesystem/Full-HD controls without writing')
    args = parser.parse_args()
    if not re.fullmatch(r'emulator-[0-9]+', args.serial) or not 1 <= args.adb_port <= 65535:
        parser.error('Select an explicit synthetic emulator serial and valid ADB port')
    if args.read_only and not args.run_id:
        parser.error('--read-only requires the original --run-id')
    if args.read_only and args.check_filesystem:
        parser.error('Filesystem mutation is incompatible with --read-only')
    if args.verify_filesystem and not args.read_only:
        parser.error('--verify-filesystem requires --read-only')
    args.kill_after = args.kill_after or ('DISCARD_MARKED_PENDING' if args.discard else 'PNG_SYNCED')
    if args.kill_after not in (DISCARD_POINTS if args.discard else STAGE_POINTS):
        parser.error('Select an interruption point belonging to the requested operation')
    if args.discard and (args.check_filesystem or args.verify_filesystem):
        parser.error('Filesystem mutation controls belong to the stage probe')
    run_id = args.run_id or str(uuid.uuid4())
    try:
        parsed = uuid.UUID(run_id)
        if str(parsed) != run_id or parsed.int == 0:
            raise ValueError()
    except ValueError:
        parser.error('Use a canonical nonzero synthetic run UUID')
    sdk = Path(os.environ['ANDROID_HOME'])
    adb = [str(sdk / 'platform-tools/adb'), '-P', str(args.adb_port), '-s', args.serial]

    def run(command, timeout=90):
        result = subprocess.run(command, text=True, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, timeout=timeout)
        if result.returncode != 0:
            raise RuntimeError('Probe command failed:\n' + result.stdout[-8000:])
        return result.stdout

    def shell(*command):
        return run(adb + ['shell', *command])

    if int(shell('getprop', 'ro.build.version.sdk').strip()) < 26:
        raise RuntimeError('Storage probe requires API 26 or newer')
    if shell('getprop', 'ro.kernel.qemu').strip() != '1':
        raise RuntimeError('The selected device is not an emulator')
    if shell('settings', 'get', 'global', 'airplane_mode_on').strip() != '1' or shell('ip', 'route').strip():
        raise RuntimeError('Enable airplane mode, disable Wi-Fi/data and require an empty route table first')
    apks = list((ROOT / 'images/build/outputs/apk/androidTest/debug').glob('*.apk'))
    if len(apks) != 1:
        raise RuntimeError('Build exactly one standalone debug instrumentation APK first')
    apk = apks[0]
    badging = run([str(sdk / 'build-tools/35.0.0/aapt2'), 'dump', 'badging', str(apk)])
    if "name='" + PACKAGE + "'" not in badging or 'uses-permission:' in badging:
        raise RuntimeError('Unexpected probe package or requested permissions')
    if not args.read_only:
        run(adb + ['install', '-r', str(apk)])
    component = PACKAGE + '/se.tmm.sensorplanner.core.PublicationCodecInstrumentation'
    try:
        def phase(name):
            if args.discard:
                name = 'discard-' + name
            return shell('am', 'instrument', '-w', '-r', '-e', 'probe', 'image-stage', '-e', 'runId', run_id,
                         '-e', 'killAfter', args.kill_after, '-e', 'phase', name, component)
        if not args.read_only:
            interrupted = phase('write-and-kill')
            if 'stagePoint=' + args.kill_after not in interrupted or 'Process crashed' not in interrupted:
                raise RuntimeError('Controlled kill was not observed:\n' + interrupted[-8000:])
        reopened = phase('read')
        if 'INSTRUMENTATION_RESULT: result=PASS' not in reopened or 'INSTRUMENTATION_CODE: -1' not in reopened:
            raise RuntimeError('Native fresh-process readback failed:\n' + reopened[-8000:])
        if args.discard and not args.read_only:
            succeeded = phase('success')
            if 'INSTRUMENTATION_RESULT: result=PASS' not in succeeded or 'INSTRUMENTATION_CODE: -1' not in succeeded:
                raise RuntimeError('Native explicit discard success control failed:\n' + succeeded[-8000:])
        if args.check_filesystem:
            checked = phase('filesystem')
            if 'INSTRUMENTATION_RESULT: result=PASS' not in checked or 'INSTRUMENTATION_CODE: -1' not in checked:
                raise RuntimeError('Native quota/link/marker checks failed:\n' + checked[-8000:])
        if args.verify_filesystem:
            checked = phase('filesystem-read')
            if 'INSTRUMENTATION_RESULT: result=PASS' not in checked or 'INSTRUMENTATION_CODE: -1' not in checked:
                raise RuntimeError('Native read-only filesystem/Full-HD checks failed:\n' + checked[-8000:])
        publication = shell('am', 'instrument', '-w', '-r', PACKAGE + '/se.tmm.sensorplanner.core.PublicationCodecInstrumentation')
        if 'INSTRUMENTATION_RESULT: result=PASS' not in publication or 'INSTRUMENTATION_CODE: -1' not in publication:
            raise RuntimeError('Existing native publication parser probe failed:\n' + publication[-8000:])
    finally:
        shell('am', 'force-stop', PACKAGE)
    print('Native Android fresh-process readback passed: exact ready/pending bytes, reconciliation and lock recovery; no implicit promotion.')
    print('Synthetic run ID:', run_id, '(original controlled kill required)' if args.read_only else '(controlled kill observed)')
    if args.check_filesystem:
        print('Native quota/symlink/marker controls and high-entropy 1920x1080 stage readback passed.')
    if args.verify_filesystem:
        print('Read-only native Full-HD and invalid-state preservation checks passed.')
    print('Existing publication parser probe passed. APK SHA-256:', hashlib.sha256(apk.read_bytes()).hexdigest())
    print('Standalone synthetic app only. No ATAK host, source authority, association, transport or power-loss acceptance.')


if __name__ == '__main__':
    main()
