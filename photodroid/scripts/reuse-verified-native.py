#!/usr/bin/env python3
"""Reuse the verified ko.1 editor only for the narrowly checked ko.2 packaging update."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import stat
import struct
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import zipfile


REPOSITORY = 'useToolkit/support_link'
RUN_ID = 40178271831
RUN_URL = f'https://github.com/{REPOSITORY}/actions/runs/{RUN_ID}'
RELEASE_URL = f'https://github.com/{REPOSITORY}/releases/download/photodroid-ko-v0.3.0-ko.1'
SOURCE_NAME = 'PhotoDroid-KO-0.3.0-ko.1-source.zip'
SOURCE_SHA256 = '88bf629e79e8ecb968a432d178bee9315d000f32479720d5539ddf6575e11f17'
APK_NAME = 'PhotoDroid-KO-0.3.0-ko.1-arm64.apk'
APK_SHA256 = '80337151ceac051d013bb2df996c5b661fb0212028a1cd2b0ff203d40c943a38'
SOURCE_PREFIX = 'PhotoDroid-KO-0.3.0-ko.1-source/'
NATIVE_MEMBER = 'lib/arm64-v8a/libphotocraft_android.so'
GRADLE_PATH = 'android/app/build.gradle'
LICENSE_PREFIX = 'android/app/src/main/assets/licenses/'
# Each allowed new file must be an exact copy of this original in the verified source ZIP.
LICENSE_ORIGINALS = {
    'LICENSE-APACHE': 'LICENSE-APACHE',
    'LICENSE-MIT': 'LICENSE-MIT',
    'NOTICE': 'NOTICE',
    'ATTRIBUTION.md': 'ATTRIBUTION.md',
    'OFL-NanumGothic.txt': 'assets/fonts/OFL-NanumGothic.txt',
    'OFL-Inter.txt': 'assets/fonts/OFL-Inter.txt',
    'OFL-JetBrainsMono.txt': 'assets/fonts/OFL-JetBrainsMono.txt',
    'LICENSE-lucide.txt': 'assets/icons/LICENSE-lucide.txt',
    'LICENSE-symmetry-axes.txt': 'assets/icons/LICENSE-symmetry-axes.txt',
    'LICENSE-noun-magnetic-lasso.txt': 'assets/icons/LICENSE-noun-magnetic-lasso.txt',
    'LICENSE-SCOWL.txt': 'assets/dict/LICENSE-SCOWL.txt',
    'LICENSE-solarized.txt': 'crates/ui-egui/src/LICENSE-solarized.txt',
    'LICENSE-translations.txt': 'crates/ui-egui/src/i18n/LICENSE-translations.txt',
    'LICENSE-app-icon.txt': 'assets/app-icon/LICENSE.txt',
}
VERSION_REPLACEMENTS = (
    (b'        : 30001\n', b'        : 30002\n'),
    (b"        : '0.3.0-ko.1'\n", b"        : '0.3.0-ko.2'\n"),
)


class VerificationError(Exception):
    pass


class SecureRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        old, new = urllib.parse.urlsplit(request.full_url), urllib.parse.urlsplit(newurl)
        if new.scheme != 'https':
            raise VerificationError('Refusing an HTTPS downgrade.')
        if request.has_header('Authorization') and old.netloc != new.netloc:
            raise VerificationError('Refusing to forward the GitHub token to another host.')
        return super().redirect_request(request, fp, code, msg, headers, newurl)


OPENER = urllib.request.build_opener(SecureRedirect())


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open('rb') as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def verify_successful_run():
    headers = {'Accept': 'application/vnd.github+json', 'User-Agent': 'PhotoDroid-KO-native-reuse', 'X-GitHub-Api-Version': '2022-11-28'}
    token = os.environ.get('GH_TOKEN') or os.environ.get('GITHUB_TOKEN')
    if token:
        headers['Authorization'] = f'Bearer {token}'
    request = urllib.request.Request(f'https://api.github.com/repos/{REPOSITORY}/actions/runs/{RUN_ID}', headers=headers)
    with OPENER.open(request, timeout=60) as response:
        data = response.read(2 * 1024 * 1024 + 1)
    if len(data) > 2 * 1024 * 1024:
        raise VerificationError('Unexpectedly large GitHub run response.')
    run = json.loads(data)
    if (run.get('id') != RUN_ID or run.get('repository', {}).get('full_name', '').lower() != REPOSITORY.lower()
            or run.get('status') != 'completed' or run.get('conclusion') != 'success'):
        raise VerificationError(f'Baseline GitHub run {RUN_ID} is not a completed, successful run for {REPOSITORY}.')
    return run.get('head_sha', '')


def download_verified(name, expected_hash, cache):
    cache.mkdir(parents=True, exist_ok=True)
    destination = cache / name
    if destination.exists():
        if not destination.is_file() or destination.is_symlink() or sha256_file(destination) != expected_hash:
            raise VerificationError(f'Cached baseline hash mismatch: {name}')
        return destination
    # Release downloads carry no Authorization header, including on the GitHub CDN redirect.
    request = urllib.request.Request(f'{RELEASE_URL}/{name}', headers={'User-Agent': 'PhotoDroid-KO-native-reuse'})
    temporary = None
    try:
        with OPENER.open(request, timeout=60) as response, tempfile.NamedTemporaryFile(dir=cache, delete=False) as handle:
            temporary = Path(handle.name)
            digest = hashlib.sha256()
            total = 0
            while chunk := response.read(1024 * 1024):
                total += len(chunk)
                if total > 1024 * 1024 * 1024:
                    raise VerificationError(f'Baseline download exceeds 1 GiB: {name}')
                digest.update(chunk)
                handle.write(chunk)
        if digest.hexdigest() != expected_hash:
            raise VerificationError(f'Downloaded baseline SHA-256 mismatch: {name}')
        temporary.replace(destination)
        temporary = None
        return destination
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def excluded(name):
    return name == 'BUILD-PROVENANCE.txt' or name.startswith('build-recipe/')


def safe_name(name):
    path = PurePosixPath(name)
    if not name or path.is_absolute() or '..' in path.parts or path.as_posix() != name or '\\' in name:
        raise VerificationError(f'Invalid source path: {name!r}')
    return name


def baseline_manifest(archive):
    result = {}
    for item in archive.infolist():
        if item.is_dir():
            continue
        if not item.filename.startswith(SOURCE_PREFIX):
            raise VerificationError(f'Unexpected baseline ZIP root: {item.filename!r}')
        name = safe_name(item.filename[len(SOURCE_PREFIX):])
        if excluded(name):
            continue
        if name in result:
            raise VerificationError(f'Duplicate baseline source file: {name}')
        mode = item.external_attr >> 16
        if stat.S_IFMT(mode) not in (0, stat.S_IFREG, stat.S_IFLNK):
            raise VerificationError(f'Unsupported baseline file type: {name}')
        result[name] = item
    if GRADLE_PATH not in result or 'README.ko.md' not in result:
        raise VerificationError('The baseline is missing the Korean source or Android build file.')
    return result


def current_manifest(source):
    # Match package-source.py: every tracked and untracked, non-ignored source file. This
    # excludes .git and ignored compiler outputs, which are never in the source release ZIP.
    listed = subprocess.check_output(['git', '-C', str(source), 'ls-files', '--cached', '--others', '--exclude-standard', '-z'])
    result = {}
    for raw in listed.split(b'\0'):
        if not raw:
            continue
        name = safe_name(raw.decode('utf-8'))
        if excluded(name):
            continue
        path = source / name
        if not path.exists() and not path.is_symlink():
            continue  # A baseline file removed now is rejected by the manifest comparison.
        if not path.is_symlink() and not path.is_file():
            raise VerificationError(f'Unsupported current source file type: {name}')
        result[name] = path
    return result


def local_bytes(path):
    return os.readlink(path).encode('utf-8') if path.is_symlink() else path.read_bytes()


def expected_gradle(baseline):
    expected = baseline
    for old, new in VERSION_REPLACEMENTS:
        if expected.count(old) != 1:
            raise VerificationError('The verified Gradle version defaults do not match the expected ko.1 values.')
        expected = expected.replace(old, new, 1)
    return expected


def verify_source(source, source_zip):
    with zipfile.ZipFile(source_zip) as archive:
        baseline, current = baseline_manifest(archive), current_manifest(source)
        missing = sorted(set(baseline) - set(current))
        added = sorted(set(current) - set(baseline))
        allowed_added = {LICENSE_PREFIX + name: original for name, original in LICENSE_ORIGINALS.items()}
        unexpected = sorted(set(added) - set(allowed_added))
        if missing or unexpected:
            raise VerificationError(f'Editor source manifest changed; deleted={missing[:20]}, unexpected added={unexpected[:20]}')
        unchanged = 0
        changed = []
        editor_digest = hashlib.sha256()
        for name, item in sorted(baseline.items()):
            path = current[name]
            if path.is_symlink() != stat.S_ISLNK(item.external_attr >> 16):
                raise VerificationError(f'Source file/symlink type changed: {name}')
            before, after = archive.read(item), local_bytes(path)
            if name == GRADLE_PATH:
                if path.is_symlink() or after != expected_gradle(before):
                    raise VerificationError('Gradle may change ONLY the exact 30001/ko.1 defaults to 30002/ko.2.')
                changed.append(name)
            elif name == 'README.ko.md':
                if path.is_symlink():
                    raise VerificationError('The Korean README must remain a regular documentation file.')
                if before != after:
                    changed.append(name)
            else:
                if before != after:
                    raise VerificationError(f'Editor source bytes changed: {name}')
                unchanged += 1
                editor_digest.update(name.encode('utf-8') + b'\0' + hashlib.sha256(after).digest())
        for name in added:
            original = allowed_added[name]
            if original not in baseline or current[name].is_symlink() or local_bytes(current[name]) != archive.read(baseline[original]):
                raise VerificationError(f'Bundled notice is not an exact copy of its verified original: {name}')
        # A subset allows local source-only checks before prepare-source.sh copies notices.
        # verify-apk.py independently requires ALL 14 notices inside the final APK.
        return {'unchanged_files': unchanged, 'changed': changed, 'licenses_added': len(added), 'editor_manifest_sha256': editor_digest.hexdigest()}


def verified_native_bytes(apk):
    with zipfile.ZipFile(apk) as archive:
        entries = [item for item in archive.infolist() if item.filename == NATIVE_MEMBER]
        if len(entries) != 1:
            raise VerificationError('The baseline APK must contain exactly one arm64 editor library.')
        data = archive.read(entries[0])
    if len(data) < 64 or data[:6] != b'\x7fELF\x02\x01' or struct.unpack_from('<HH', data, 16) != (3, 183):
        raise VerificationError('The baseline editor is not a little-endian AArch64 shared ELF.')
    offset = struct.unpack_from('<Q', data, 32)[0]
    entry_size, count = struct.unpack_from('<HH', data, 54)
    if entry_size < 56 or not 0 < count <= 4096 or offset + entry_size * count > len(data):
        raise VerificationError('Invalid baseline ELF program headers.')
    loads = 0
    for index in range(count):
        header = offset + index * entry_size
        if struct.unpack_from('<I', data, header)[0] == 1:
            alignment = struct.unpack_from('<Q', data, header + 48)[0]
            if alignment < 16384 or alignment & (alignment - 1):
                raise VerificationError('The baseline ELF does not preserve 16 KiB LOAD alignment.')
            loads += 1
    if not loads:
        raise VerificationError('The baseline ELF has no loadable segments.')
    return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-dir', required=True, type=Path)
    parser.add_argument('--check-only', action='store_true', help='Validate the successful run and source equivalence without downloading/copying the APK.')
    args = parser.parse_args()
    source = args.source_dir.resolve(strict=True)
    temporary_root = Path(os.environ.get('RUNNER_TEMP', tempfile.gettempdir()))
    temporary_root.mkdir(parents=True, exist_ok=True)
    provenance = temporary_root / 'native-reuse-info.txt'
    provenance.unlink(missing_ok=True)
    head_sha = verify_successful_run()
    cache = temporary_root / 'photodroid-verified-ko1'
    source_zip = download_verified(SOURCE_NAME, SOURCE_SHA256, cache)
    result = verify_source(source, source_zip)
    lines = [
        'Packaging-only update: native editor source verified unchanged against ko.1.',
        f'Baseline successful workflow: {RUN_URL}',
        f'Baseline workflow commit: {head_sha}',
        f'Baseline source ZIP SHA-256: {SOURCE_SHA256}',
        f"Unchanged source files: {result['unchanged_files']}; editor manifest SHA-256: {result['editor_manifest_sha256']}",
        f"Allowed changes: {', '.join(result['changed'])}; original notice copies added: {result['licenses_added']}/14.",
        'The prior run passed 77 targeted tests. Those results apply to the identical editor source; tests were not rerun for this packaging-only update.',
        'The new APK is independently checked for identity, all 14 original notices, signing, and ELF/ZIP alignment.',
    ]
    if args.check_only:
        lines.append('Mode: source/run check only; baseline APK not downloaded and no native library copied.')
    else:
        apk = download_verified(APK_NAME, APK_SHA256, cache)
        data = verified_native_bytes(apk)
        destination = source / 'android/app/src/main/jniLibs/arm64-v8a/libphotocraft_android.so'
        if not destination.resolve().is_relative_to(source):
            raise VerificationError('Native output would escape SOURCE_DIR.')
        destination.parent.mkdir(parents=True, exist_ok=True)
        if destination.exists() or destination.is_symlink():
            raise VerificationError('Refusing to overwrite an existing native library; use freshly prepared source.')
        with tempfile.NamedTemporaryFile(dir=destination.parent, delete=False) as handle:
            pending = Path(handle.name)
            handle.write(data)
        pending.chmod(0o644)
        pending.replace(destination)
        lines += [
            f'Baseline APK SHA-256: {APK_SHA256}',
            f'Reused native library SHA-256: {hashlib.sha256(data).hexdigest()}',
            'Native library retained byte-for-byte, already stripped and 16 KiB aligned; no rebuild or binary editing.',
        ]
    provenance.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print(f"Verified unchanged editor source: {result['unchanged_files']} files; allowed notice additions: {result['licenses_added']}/14.")
    print(f'Native reuse provenance: {provenance}')


if __name__ == '__main__':
    try:
        main()
    except (VerificationError, OSError, ValueError, subprocess.CalledProcessError, zipfile.BadZipFile, urllib.error.URLError) as error:
        raise SystemExit(f'Native reuse refused: {error}') from None
