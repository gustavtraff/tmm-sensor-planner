#!/usr/bin/env python3
"""Exercise offline probe CLI boundaries with temporary synthetic files only."""
import hashlib
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def run(*args, success=True):
    result = subprocess.run(['sh', 'scripts/datasync-metadata-probe.sh', *map(str, args)],
                            cwd=ROOT, text=True, capture_output=True, timeout=60)
    assert (result.returncode == 0) == success, 'Unexpected probe exit status'
    if not success:
        assert 'Probe failed:' in result.stderr
        assert '<event' not in result.stderr and 'SYNTHETIC-GROUP' not in result.stderr
        assert not any(str(arg) in result.stderr for arg in args if isinstance(arg, Path))
    return result


if __name__ == '__main__':
    run('self-test')
    with tempfile.TemporaryDirectory(prefix='tmm-datasync-cli-') as temporary:
        output = Path(temporary) / 'fixture set'
        run('create', output)
        files = sorted(output.iterdir())
        assert len(files) == 5
        before = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
        for path in files:
            run('check', path.stem, path)
        run('create', output, success=False)
        run('check', 'pir-update', output / 'pir.cot', success=False)
        run('check', 'pir', output / 'missing-private-input.cot', success=False)
        oversized = Path(temporary) / 'oversized-private-input.cot'
        oversized.write_bytes(b'X' * 262145)
        run('check', 'pir', oversized, success=False)
        assert before == {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
    print('Offline probe CLI checks passed: all cases, read-only checks, overwrite refusal and safe failures.')
