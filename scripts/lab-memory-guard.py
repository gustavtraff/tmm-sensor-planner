#!/usr/bin/env python3
"""Run one foreground lab command in its own process group, with macOS memory limits."""
import argparse
import os
import re
import signal
import subprocess
import sys
import time


def capture(*args):
    return subprocess.check_output(args, text=True, timeout=3)


def sample():
    vm = capture("/usr/bin/vm_stat")
    page_size = int(re.search(r"page size of (\d+) bytes", vm)[1])
    compressed = int(re.search(r"Pages occupied by compressor:\s+(\d+)", vm)[1])
    swap = capture("/usr/sbin/sysctl", "-n", "vm.swapusage")
    used, unit = re.search(r"used\s*=\s*([\d.]+)([MG])", swap).groups()
    pressure = int(capture("/usr/sbin/sysctl", "-n", "kern.memorystatus_vm_pressure_level"))
    return compressed * page_size / 1048576, float(used) * (1024 if unit == "G" else 1), pressure


def check(values, compressor_limit, swap_limit):
    compressed, swap, pressure = values
    if pressure != 1 or compressed > compressor_limit or swap > swap_limit:
        raise RuntimeError("Host memory budget exceeded (compressor %.0f MiB, swap %.0f MiB, pressure %d)."
                           % (compressed, swap, pressure))


def stop(child):
    if child.returncode is not None:
        return
    # Signal only the new group created for this invocation, never a name or parent PID.
    for sig in (signal.SIGTERM, signal.SIGKILL):
        try:
            os.killpg(child.pid, sig)
        except ProcessLookupError:
            pass
        if sig == signal.SIGTERM:
            time.sleep(0.2)
    child.wait(timeout=3)


def guard(command, compressor_limit=1024, swap_limit=512, seconds=600, interval=2,
          read=sample, cancelled=lambda: False):
    check(read(), compressor_limit, swap_limit)
    if cancelled():
        raise RuntimeError("Lab guard interrupted.")
    child = subprocess.Popen(command, start_new_session=True)
    deadline = time.monotonic() + seconds
    try:
        while child.poll() is None:
            if cancelled():
                raise RuntimeError("Lab guard interrupted.")
            check(read(), compressor_limit, swap_limit)
            if time.monotonic() >= deadline:
                raise RuntimeError("Lab time budget exceeded.")
            time.sleep(interval)
        return child.returncode
    finally:
        previous = {sig: signal.signal(sig, signal.SIG_IGN)
                    for sig in (signal.SIGINT, signal.SIGTERM, signal.SIGHUP)}
        try:
            stop(child)
        finally:
            for sig, handler in previous.items():
                signal.signal(sig, handler)


def positive(value):
    number = float(value)
    if not 0 < number < float("inf"):
        raise argparse.ArgumentTypeError("Expected a finite positive number")
    return number


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--max-compressor-mib", type=positive, default=1024)
    parser.add_argument("--max-swap-mib", type=positive, default=512)
    parser.add_argument("--seconds", type=positive, default=600)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command:
        parser.error("Provide -- followed by a foreground command")
    if sys.platform != "darwin":
        parser.error("Live memory sampling requires macOS")

    cancelled = [False]

    def interrupted(signum, frame):
        # Do not raise during Popen: a signal there could orphan the new process.
        cancelled[0] = True

    for sig in (signal.SIGINT, signal.SIGTERM, signal.SIGHUP):
        signal.signal(sig, interrupted)
    try:
        return guard(command, args.max_compressor_mib, args.max_swap_mib, args.seconds,
                     cancelled=lambda: cancelled[0])
    except (RuntimeError, OSError, subprocess.SubprocessError, ValueError, AttributeError, TypeError) as error:
        # Sampler output and command arguments can contain private data; do not print them.
        print(str(error) if isinstance(error, RuntimeError) else "Memory monitoring unavailable; lab stopped.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
