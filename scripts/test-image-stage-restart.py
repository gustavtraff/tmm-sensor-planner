#!/usr/bin/env python3
"""Opt-in, synthetic development-filesystem recovery trial using two fresh native-graphics workers."""
from pathlib import Path
import os
import signal
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
CLASS = 'se.tmm.sensorplanner.plugin.images.ImageStageProcessRestartTest'


def worker(workspace, phase):
    env = os.environ.copy()
    env.update(TMM_STAGE_RESTART_ROOT=str(workspace), TMM_STAGE_RESTART_PHASE=phase)
    command = [str(ROOT / 'gradlew'), '-p', str(ROOT / 'images'), '--offline', '--no-daemon',
               '--max-workers=2', '-Dorg.gradle.jvmargs=-Xmx1536m', 'testDebugUnitTest',
               '--tests', CLASS]
    process = subprocess.Popen(command, cwd=ROOT, env=env, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True, start_new_session=True)
    try:
        output, _ = process.communicate(timeout=180)
    except BaseException:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()
        raise
    if phase == 'write-and-halt':
        if process.returncode == 0 or 'exit value 73' not in output or not (workspace / 'worker-state.txt').is_file():
            raise RuntimeError('Write worker did not reach the controlled halt:\n' + output[-12000:])
    elif process.returncode != 0:
        raise RuntimeError('Fresh read worker failed:\n' + output[-12000:])


def main():
    with tempfile.TemporaryDirectory(prefix='tmm-stage-restart-148-') as directory:
        workspace = Path(directory)
        (workspace / 'harness-marker').write_text('TMM-SYNTHETIC-RESTART-148', encoding='ascii')
        worker(workspace, 'write-and-halt')
        worker(workspace, 'read')
        report = ROOT / 'images/build/test-results/testDebugUnitTest' / ('TEST-' + CLASS + '.xml')
        result = ET.parse(report).getroot()
        if any(result.get(k) != v for k, v in {'tests': '1', 'failures': '0', 'errors': '0', 'skipped': '0'}.items()):
            raise RuntimeError('Fresh-worker result is not an actual passing one-case run')
    print('Controlled halt after PNG sync observed; a fresh API-26 native-graphics worker reopened exact ready/pending bytes.')
    print('Pending remained unassociated and blocked new writes. Development filesystem only; no Android process/power-loss proof.')


if __name__ == '__main__':
    main()
