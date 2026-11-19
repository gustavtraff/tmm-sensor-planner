#!/usr/bin/env python3
"""Offline guard checks; no emulator, Docker, or real memory pressure."""
import os
from pathlib import Path
import runpy
import signal
import subprocess
import sys
import unittest
from unittest.mock import patch

guard = runpy.run_path(str(Path(__file__).with_name("lab-memory-guard.py")))


class MemoryGuardTest(unittest.TestCase):
    def test_parses_compressed_memory_and_swap(self):
        outputs = ["Mach Virtual Memory Statistics: (page size of 16384 bytes)\nPages occupied by compressor: 65536.",
                   "total = 1024.00M used = 0.25G free = 768.00M", "1\n"]
        with patch.dict(guard["sample"].__globals__, capture=lambda *args: outputs.pop(0)):
            self.assertEqual(guard["sample"](), (1024, 256, 1))

    def test_refuses_before_starting_when_host_is_busy(self):
        for reading in ((1025, 0, 1), (0, 513, 1), (0, 0, 2), (0, 0, 4)):
            with patch.object(subprocess, "Popen") as start:
                with self.assertRaisesRegex(RuntimeError, "memory budget"):
                    guard["guard"](["unused"], read=lambda: reading)
                start.assert_not_called()

    def test_does_not_signal_reaped_child_group(self):
        child = unittest.mock.Mock(returncode=0)
        with patch.object(os, "killpg") as kill:
            guard["stop"](child)
            kill.assert_not_called()

    def test_interruption_before_launch_starts_nothing(self):
        with patch.object(subprocess, "Popen") as start:
            with self.assertRaisesRegex(RuntimeError, "interrupted"):
                guard["guard"](["unused"], read=lambda: (0, 0, 1), cancelled=lambda: True)
            start.assert_not_called()

    def test_cleanup_ignores_repeated_interruption_and_restores_handlers(self):
        before = signal.getsignal(signal.SIGTERM)

        def cleanup(child):
            self.assertEqual(signal.getsignal(signal.SIGTERM), signal.SIG_IGN)
            os.kill(os.getpid(), signal.SIGTERM)
            os.kill(os.getpid(), signal.SIGHUP)

        with patch.dict(guard["guard"].__globals__, stop=cleanup):
            guard["guard"]([sys.executable, "-c", "pass"], interval=0.01, read=lambda: (0, 0, 1))
        self.assertEqual(signal.getsignal(signal.SIGTERM), before)

    @unittest.skipUnless(os.name == "posix", "Process group checks require POSIX")
    def test_stops_owned_child_on_pressure_sampler_failure_or_deadline(self):
        real_start = subprocess.Popen
        for failure in (RuntimeError("Host memory budget exceeded."), OSError("sample failed"), None):
            children = []

            def start(*args, **kwargs):
                child = real_start(*args, **kwargs)
                children.append(child)
                return child

            readings = iter([(0, 0, 1)])

            def read():
                initial = next(readings, None)
                if initial is not None:
                    return initial
                if failure is not None:
                    raise failure
                return (0, 0, 1)

            with patch.object(subprocess, "Popen", side_effect=start):
                with self.assertRaises((RuntimeError, OSError)):
                    guard["guard"]([sys.executable, "-c", "import time; time.sleep(30)"],
                                   seconds=0, interval=0.01, read=read)
            self.assertEqual(len(children), 1)
            self.assertIsNotNone(children[0].poll())

    @unittest.skipUnless(os.name == "posix", "Process group checks require POSIX")
    def test_success_preserves_exit_code(self):
        self.assertEqual(guard["guard"]([sys.executable, "-c", "raise SystemExit(7)"],
                                       interval=0.01, read=lambda: (0, 0, 1)), 7)


if __name__ == "__main__":
    unittest.main()
