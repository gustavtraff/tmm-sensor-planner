#!/usr/bin/env python3
"""Check SDK log containment with synthetic success/failure, without an SDK."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

wrapper = Path(__file__).with_name("check-sdk-ci.sh")
with tempfile.TemporaryDirectory() as tmp:
    root = Path(tmp)
    scripts = root / "scripts"
    scripts.mkdir()
    shutil.copyfile(wrapper, scripts / wrapper.name)
    for code in (0, 7):
        (scripts / "check-sdk.sh").write_text(
            "#!/bin/sh\necho SYNTHETIC_PRIVATE_CANARY\n"
            "echo SYNTHETIC_PRIVATE_CANARY >&2\n"
            f"exit {code}\n"
        )
        result = subprocess.run(
            ["sh", str(scripts / wrapper.name)],
            env={**os.environ, "TMPDIR": str(root)},
            capture_output=True, text=True,
        )
        assert result.returncode == (0 if code == 0 else 1), result
        assert "SYNTHETIC_PRIVATE_CANARY" not in result.stdout + result.stderr
        assert not list(root.glob("tmm-sdk-ci.*")), "private log was retained"
print("SDK CI wrapper: success/failure containment and cleanup passed")
