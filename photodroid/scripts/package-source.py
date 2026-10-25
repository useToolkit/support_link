#!/usr/bin/env python3
"""Archive all tracked/added source, licenses and the exact public build recipe; no build output."""
import hashlib
import os
from pathlib import Path
import stat
import subprocess
import zipfile

root = Path(os.environ['PHOTODROID_RECIPE_ROOT'])
source = Path(os.environ['SOURCE_DIR'])
dist = Path(os.environ['DIST_DIR'])
dist.mkdir(parents=True, exist_ok=True)
prefix = f"PhotoDroid-KO-{os.environ['APP_VERSION']}-source/"
listed = subprocess.check_output(['git', '-C', str(source), 'ls-files', '--cached', '--others', '--exclude-standard', '-z'])
names = sorted(set(n.decode('utf-8') for n in listed.split(b'\0') if n))
required = {'LICENSE-APACHE', 'LICENSE-MIT', 'NOTICE', 'ATTRIBUTION.md', 'assets/fonts/NanumGothic-Regular.ttf', 'assets/fonts/OFL-NanumGothic.txt'}
if not required.issubset(names):
    raise SystemExit(f'Missing corresponding source/license files: {sorted(required - set(names))}')
patch_hash = hashlib.sha256((root / 'photodroid/ko.patch').read_bytes()).hexdigest()
info = '\n'.join([
    'PhotoDroid Korean build provenance',
    f"Application ID: {os.environ['APP_ID']}",
    f"Version: {os.environ['APP_VERSION']} ({os.environ['APP_VERSION_CODE']})",
    f"Upstream: {os.environ['UPSTREAM_URL']}",
    f"Upstream commit: {os.environ['UPSTREAM_COMMIT']}",
    f"Recipe commit: {os.environ.get('GITHUB_SHA', 'local-validation')}",
    f'Korean patch SHA-256: {patch_hash}',
    f"Google Fonts commit: {os.environ['FONT_COMMIT']}",
    f"NanumGothic SHA-256: {os.environ['FONT_SHA256']}",
    f"Font license SHA-256: {os.environ['FONT_LICENSE_SHA256']}",
    f"Rust: {os.environ['RUST_VERSION']}; JDK: 17; Gradle: {os.environ['GRADLE_VERSION']}",
    f"Android SDK: {os.environ['SDK_VERSION']}; build-tools: {os.environ['BUILD_TOOLS_VERSION']}; NDK: {os.environ['NDK_VERSION']}",
    'ABI: arm64-v8a; minimum Android API: 29',
    'Linker max-page-size/common-page-size: 16384; APK ELF/ZIP alignment checked.',
    'The full modified source is in this archive. build-recipe/ contains the public reproduction recipe.',
    'No private signing or backup-decryption key is included.',
    '',
])
reuse_info = Path(os.environ.get('RUNNER_TEMP', '/tmp')) / 'native-reuse-info.txt'
if reuse_info.is_file():
    info += '\n' + reuse_info.read_text(encoding='utf-8')
(dist / 'BUILD-INFO.txt').write_text(info, encoding='utf-8')

def add_file(archive, path, name):
    if not path.exists() and not path.is_symlink():
        return  # A deletion in ko.patch removes it from the delivered source too.
    item = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
    item.create_system = 3
    if path.is_symlink():
        item.external_attr = (stat.S_IFLNK | 0o777) << 16
        data = os.readlink(path).encode('utf-8')
    else:
        item.external_attr = (stat.S_IFREG | stat.S_IMODE(path.stat().st_mode)) << 16
        data = path.read_bytes()
    item.compress_type = zipfile.ZIP_DEFLATED
    archive.writestr(item, data)

with zipfile.ZipFile(dist / os.environ['SOURCE_NAME'], 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
    for name in names:
        if name.startswith(('target/', 'android/app/build/', 'android/app/src/main/jniLibs/')):
            raise SystemExit(f'Unexpected build output in source manifest: {name}')
        add_file(archive, source / name, prefix + name)
    add_file(archive, dist / 'BUILD-INFO.txt', prefix + 'BUILD-PROVENANCE.txt')
    # Explicit public-file allowlist: a stray local password/key backup must not enter the ZIP.
    recipe_files = [root / name for name in [
        '.github/workflows/photodroid-ko.yml', 'photodroid/README.ko.md',
        'photodroid/RELEASE-NOTES.ko.md', 'photodroid/ko.patch', 'photodroid/backup-public.pem',
    ]]
    recipe_files += sorted((root / 'photodroid/scripts').glob('*.sh'))
    recipe_files += sorted((root / 'photodroid/scripts').glob('*.py'))
    for path in recipe_files:
        if path.is_file() and '__pycache__' not in path.parts:
            relative = path.relative_to(root).as_posix()
            if relative.endswith(('.jks', '.p12', '.key')) or 'private' in path.name.lower():
                raise SystemExit(f'Private key material cannot be included: {relative}')
            add_file(archive, path, prefix + 'build-recipe/' + relative)
print(f"Complete source archive: {os.environ['SOURCE_NAME']}")
