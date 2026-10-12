#!/usr/bin/env python3
"""Check the local PAT without displaying it or changing GitHub data."""
import json
from pathlib import Path
import ssl
import sys
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


def main():
    path = Path(__file__).resolve().parents[1] / ".env.github"
    try:
        lines = path.read_text().splitlines()
    except OSError:
        sys.exit("Missing .env.github. Copy .env.example and enter GH_TOKEN.")
    tokens = [line.removeprefix("GH_TOKEN=").strip()
              for line in lines if line.startswith("GH_TOKEN=")]
    if len(tokens) != 1 or not tokens[0] or any(c.isspace() for c in tokens[0]):
        sys.exit("Enter exactly one GH_TOKEN in .env.github, without quotes or whitespace.")
    token = tokens[0]
    if token.startswith(('"', "'")):
        sys.exit("Remove quotes around GH_TOKEN in .env.github.")
    request = Request("https://api.github.com/user", headers={
        "Authorization": "Bearer " + token,
        "Accept": "application/vnd.github+json",
        "User-Agent": "tmm-sensor-planner-auth-check",
    })
    try:
        # Python.org on macOS may lack its own CA bundle; keep TLS verification enabled.
        fallback = Path("/opt/homebrew/etc/openssl@3/cert.pem")
        cafile = str(fallback) if ssl.get_default_verify_paths().cafile is None and fallback.exists() else None
        with urlopen(request, timeout=15, context=ssl.create_default_context(cafile=cafile)) as response:
            login = json.load(response)["login"]
    except HTTPError as error:
        sys.exit("GitHub rejected the check (HTTP " + str(error.code) + ").")
    except (URLError, TimeoutError, OSError, ValueError, KeyError):
        sys.exit("Could not verify GitHub authentication. Check your network connection.")
    print("GitHub authentication works for " + login + ". The token value is not displayed.")


if __name__ == "__main__":
    main()
