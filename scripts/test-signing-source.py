#!/usr/bin/env python3
"""Offline archive-boundary tests using an isolated synthetic Git repository."""
from pathlib import Path
import runpy
import subprocess
import tempfile
import unittest
import zipfile

prepare = runpy.run_path(str(Path(__file__).with_name('create-signing-source.py')))

GRADLE_SOURCE = "synthetic\nclasspath 'com.atakmap.gradle:atak-gradle-takdev:2.+'\n"


class SourceArchiveTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name) / 'repo'
        self.repo.mkdir()
        self.output = Path(self.temp.name) / 'source.zip'
        self.git('init', '-q')
        for name in prepare['INPUTS']:
            if name.endswith(('src', 'src/main', 'src/test')):
                name += '/synthetic.txt'
            self.write(name)
        for name in prepare['REQUIRED']:
            self.write(name)
        self.write('app/build.gradle', GRADLE_SOURCE)
        (self.repo / 'gradlew').chmod(0o755)
        self.commit()

    def git(self, *args):
        return subprocess.check_output(['git', '-c', 'user.name=Synthetic Test',
                                       '-c', 'user.email=synthetic@example.invalid', *args], cwd=self.repo)

    def write(self, name, content='synthetic'):
        path = self.repo / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)

    def commit(self):
        self.git('add', '.')
        self.git('commit', '-qm', 'Synthetic source fixture')

    def create(self):
        return prepare['create_archive'](self.repo, self.output)

    def test_only_committed_build_inputs_and_executable_wrapper(self):
        self.write('.env.github', 'synthetic-secret')
        self.write('app/build/ignored.apk')
        self.write('unrelated.txt')
        self.commit()
        self.write('app/build.gradle', 'uncommitted-edit')
        commit, digest = self.create()
        self.assertEqual(commit, self.git('rev-parse', 'HEAD').decode().strip())
        self.assertEqual(len(digest), 64)
        with zipfile.ZipFile(self.output) as archive:
            self.assertEqual(archive.read(prepare['ROOT'] + 'app/build.gradle'), GRADLE_SOURCE.encode())
            self.assertTrue(all(n.startswith(prepare['ROOT']) for n in archive.namelist()))
            self.assertNotIn(prepare['ROOT'] + '.env.github', archive.namelist())
            self.assertNotIn(prepare['ROOT'] + 'app/build/ignored.apk', archive.namelist())
            self.assertNotIn(prepare['ROOT'] + 'unrelated.txt', archive.namelist())
            self.assertTrue(archive.getinfo(prepare['ROOT'] + 'gradlew').external_attr >> 16 & 0o111)
        with self.assertRaises(FileExistsError):
            self.create()

    def test_rejects_tracked_private_files_in_selected_sources(self):
        for name in ('secret.pem', 'certificate.crt', '.env', 'local.properties', 'dependency.jar'):
            with self.subTest(name=name):
                path = self.repo / 'app/src/main/assets' / name
                self.write(str(path.relative_to(self.repo)))
                self.commit()
                with self.assertRaises(ValueError):
                    self.create()
                self.assertFalse(self.output.exists())
                path.unlink()
                self.commit()

    def test_rejects_noncompliant_takdev_major_or_missing_coordinate(self):
        for version in ('3.+', '1.+', '3.6.0', None):
            with self.subTest(version=version):
                self.write('app/build.gradle', GRADLE_SOURCE.replace('2.+', version) if version else 'synthetic\n')
                self.commit()
                with self.assertRaisesRegex(ValueError, 'requires takdev 2'):
                    self.create()
                self.assertFalse(self.output.exists())

    def test_rejects_symlink(self):
        (self.repo / 'app/src/main/assets/link').symlink_to('plugin.xml')
        self.commit()
        with self.assertRaises(ValueError):
            self.create()
        self.assertFalse(self.output.exists())

    def test_rejects_missing_required_file(self):
        (self.repo / 'app/src/main/assets/plugin.xml').unlink()
        self.commit()
        with self.assertRaises(ValueError):
            self.create()
        self.assertFalse(self.output.exists())


if __name__ == '__main__':
    unittest.main()
