#!/usr/bin/env python3
"""Prepare committed build sources for human review; never submit them."""
import hashlib
import io
from pathlib import Path, PurePosixPath
import stat
import subprocess
import sys
import zipfile

ROOT = 'TMM-Sensor-Planner/'
INPUTS = (
    'README.md', 'build.gradle', 'settings.gradle', 'gradle.properties',
    'gradlew', 'gradlew.bat', 'gradle/wrapper/gradle-wrapper.jar',
    'gradle/wrapper/gradle-wrapper.properties',
    'app/build.gradle', 'app/lint.xml', 'app/src/main',
    'core/src/main', 'core/src/test', 'images/build.gradle',
    'images/settings.gradle', 'images/gradle.properties', 'images/src',
)
REQUIRED = ('gradlew', 'gradle/wrapper/gradle-wrapper.jar', 'app/build.gradle',
            'app/src/main/AndroidManifest.xml', 'app/src/main/assets/plugin.xml')


def create_archive(repo, output):
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=repo, text=True).strip()
    data = subprocess.check_output(
        ['git', 'archive', '--format=zip', '--prefix=' + ROOT, commit, '--', *INPUTS], cwd=repo)
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        names = archive.namelist()
        if archive.testzip() is not None or len(names) != len(set(names)):
            raise ValueError('Invalid source archive')
        if not all(ROOT + name in names for name in REQUIRED):
            raise ValueError('Missing required build files')
        for entry in archive.infolist():
            path = PurePosixPath(entry.filename)
            if not entry.filename.startswith(ROOT) or '..' in path.parts or path.is_absolute():
                raise ValueError('Unsafe archive path')
            if stat.S_ISLNK(entry.external_attr >> 16):
                raise ValueError('Source archive must not contain symlinks')
            if any(part.lower().startswith('.env') or part.lower() in {
                '.git', '.gradle', '.takdev', 'build', 'private-data', 'sdk', 'libs',
                'local.properties', 'android_keystore',
            } for part in path.parts):
                raise ValueError('Private or generated input in source archive')
            if path.suffix.lower() in {'.apk', '.aab', '.zip', '.jar', '.jks', '.keystore',
                                       '.key', '.pem', '.p12', '.pfx', '.crt', '.cer', '.der'} and entry.filename != ROOT + 'gradle/wrapper/gradle-wrapper.jar':
                raise ValueError('Binary dependency or signing material in source archive')
    with output.open('xb') as stream:
        stream.write(data)
    return commit, hashlib.sha256(data).hexdigest()


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit('Usage: python3 scripts/create-signing-source.py /private/path/source.zip')
    repo = Path(__file__).resolve().parents[1]
    commit, digest = create_archive(repo, Path(sys.argv[1]))
    print('Prepared committed source for review, not upload approval.')
    print('Commit:', commit)
    print('SHA-256:', digest)
