#!/usr/bin/env python3
"""Offline checks for the token checker; all credentials are synthetic."""
from contextlib import redirect_stdout
from io import StringIO
from pathlib import Path
import runpy
import unittest
from unittest.mock import patch

check = runpy.run_path(str(Path(__file__).with_name("check-github-auth.py")))


class AuthCheckTest(unittest.TestCase):
    def test_empty_token_never_calls_api(self):
        with patch.object(check["Path"], "read_text", return_value="GH_TOKEN="), patch.dict(check["main"].__globals__, urlopen=lambda *a, **k: self.fail("Unexpected API call")):
            with self.assertRaisesRegex(SystemExit, "Fyll i"):
                check["main"]()

    def test_success_does_not_print_token(self):
        token = "synthetic-test-token"
        def response(request, **kwargs):
            self.assertEqual(request.full_url, "https://api.github.com/user")
            self.assertEqual(request.get_header("Authorization"), "Bearer " + token)
            return StringIO('{"login":"synthetic-user"}')
        output = StringIO()
        with patch.object(check["Path"], "read_text", return_value="GH_TOKEN=" + token), patch.dict(check["main"].__globals__, urlopen=response), redirect_stdout(output):
            check["main"]()
        self.assertIn("synthetic-user", output.getvalue())
        self.assertNotIn(token, output.getvalue())


if __name__ == "__main__":
    unittest.main()
