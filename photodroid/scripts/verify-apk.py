#!/usr/bin/env python3
"""Check the APK identity and every bundled ELF's 16 KB LOAD segment alignment."""
import os
from pathlib import Path
import re
import struct
import subprocess
import sys
import zipfile

apk = Path(sys.argv[1])
build_tools = Path(os.environ['ANDROID_HOME']) / 'build-tools' / os.environ['BUILD_TOOLS_VERSION']
signature = subprocess.check_output([str(build_tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
signers = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$', signature, re.MULTILINE)
if [value.lower() for value in signers] != [os.environ['REQUIRED_SIGNER_SHA256']]:
    raise SystemExit('Final APK signing certificate does not match the required original signer.')
print(signature, end='')
print('Final APK signer matches the required original release certificate.')
badging = subprocess.check_output([str(build_tools / 'aapt2'), 'dump', 'badging', str(apk)], text=True)
package_line = next((line for line in badging.splitlines() if line.startswith('package:')), '')
values = dict(re.findall(r"(\w+)='([^']*)'", package_line))
for key, expected in [('name', os.environ['APP_ID']), ('versionCode', os.environ['APP_VERSION_CODE']), ('versionName', os.environ['APP_VERSION'])]:
    if values.get(key) != expected:
        raise SystemExit(f'Unexpected APK {key}: {values.get(key)!r}')
if "application-label:'포토드로이드'" not in badging:
    raise SystemExit('The Korean launcher label is missing.')
if "launchable-activity: name='IA.photodroid.PhotoCraftActivity'" not in badging:
    raise SystemExit('The NativeActivity class must retain the IA.photodroid package.')
with zipfile.ZipFile(apk) as archive:
    names = archive.namelist()
    source = Path(os.environ['SOURCE_DIR'])
    notices = [
        'LICENSE-APACHE', 'LICENSE-MIT', 'NOTICE', 'ATTRIBUTION.md',
        'assets/fonts/OFL-NanumGothic.txt', 'assets/fonts/OFL-Inter.txt', 'assets/fonts/OFL-JetBrainsMono.txt',
        'assets/icons/LICENSE-lucide.txt', 'assets/icons/LICENSE-symmetry-axes.txt',
        'assets/icons/LICENSE-noun-magnetic-lasso.txt', 'assets/dict/LICENSE-SCOWL.txt',
        'crates/ui-egui/src/LICENSE-solarized.txt', 'crates/ui-egui/src/i18n/LICENSE-translations.txt',
    ]
    notice_pairs = [(name, f'assets/licenses/{Path(name).name}') for name in notices]
    notice_pairs.append(('assets/app-icon/LICENSE.txt', 'assets/licenses/LICENSE-app-icon.txt'))
    for original, bundled in notice_pairs:
        if bundled not in names or archive.read(bundled) != (source / original).read_bytes():
            raise SystemExit(f'Original license/notice is missing or modified in the APK: {bundled}')
    print(f'Original project/font/asset notices verified inside APK: {len(notice_pairs)} files')
    if 'classes.dex' not in names or 'lib/arm64-v8a/libphotocraft_android.so' not in names:
        raise SystemExit('The Java classes or arm64 native editor are missing.')
    expected_native = source / 'android/app/src/main/jniLibs/arm64-v8a/libphotocraft_android.so'
    if archive.read('lib/arm64-v8a/libphotocraft_android.so') != expected_native.read_bytes():
        raise SystemExit('The final APK native editor differs from the verified native library.')
    print('Final APK native editor exactly matches the verified native library bytes.')
    libraries = [name for name in names if name.startswith('lib/') and name.endswith('.so')]
    for name in libraries:
        if not name.startswith('lib/arm64-v8a/'):
            raise SystemExit(f'Unexpected ABI: {name}')
        data = archive.read(name)
        if data[:6] != b'\x7fELF\x02\x01':
            raise SystemExit(f'Expected a 64-bit little-endian ELF: {name}')
        if struct.unpack_from('<H', data, 18)[0] != 183:
            raise SystemExit(f'ELF is not AArch64: {name}')
        offset = struct.unpack_from('<Q', data, 32)[0]
        entry_size, entry_count = struct.unpack_from('<HH', data, 54)
        loads = 0
        for index in range(entry_count):
            header = offset + index * entry_size
            if struct.unpack_from('<I', data, header)[0] == 1:
                alignment = struct.unpack_from('<Q', data, header + 48)[0]
                if alignment < 16384:
                    raise SystemExit(f'ELF LOAD alignment {alignment} is below 16384: {name}')
                loads += 1
        if not loads:
            raise SystemExit(f'No loadable ELF segments: {name}')
        print(f'{name}: {loads} LOAD segments, each aligned to at least 16384 bytes')
subprocess.run([str(build_tools / 'zipalign'), '-c', '-P', '16', '-v', '4', str(apk)], check=True)
print(f"APK package, Korean launcher, arm64 ELF and ZIP alignment passed: {os.environ['APP_ID']}")
