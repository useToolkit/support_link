#!/usr/bin/env python3
"""Import only the original release signer through a bounded, encrypted run-specific inbox."""
import base64
import binascii
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tarfile
import time
import urllib.error
import urllib.parse
import urllib.request

MAX_INBOX = 256 * 1024
MAX_CMS = 192 * 1024
MAX_TAR = 256 * 1024
MAX_RESPONSE = 512 * 1024
FILE_LIMITS = {'release.jks': 128 * 1024, 'store-password.txt': 4096, 'key-password.txt': 4096, 'key-alias.txt': 512}


def decode_inbox(payload, expected_path):
    if not isinstance(payload, dict) or payload.get('type') != 'file' or payload.get('path') != expected_path:
        raise ValueError('Unexpected inbox response.')
    size = payload.get('size')
    if not isinstance(size, int) or not 0 < size <= MAX_INBOX or payload.get('encoding') != 'base64':
        raise ValueError('Inbox file exceeds its size limit or has an unsupported encoding.')
    api_content = payload.get('content')
    if not isinstance(api_content, str) or len(api_content) > MAX_RESPONSE:
        raise ValueError('Invalid GitHub content payload.')
    raw = base64.b64decode(''.join(api_content.split()).encode('ascii'), validate=True)
    if len(raw) != size or len(raw) > MAX_INBOX:
        raise ValueError('Inbox size mismatch.')
    cms = base64.b64decode(b''.join(raw.split()), validate=True)
    if not 0 < len(cms) <= MAX_CMS:
        raise ValueError('Encrypted envelope exceeds its size limit.')
    return cms


def extract_signing(archive_path, destination):
    if not 0 < archive_path.stat().st_size <= MAX_TAR:
        raise ValueError('Decrypted archive exceeds its size limit.')
    with tarfile.open(archive_path, mode='r:') as archive:
        members = archive.getmembers()
        if len(members) != len(FILE_LIMITS) or {m.name for m in members} != set(FILE_LIMITS):
            raise ValueError('Signing archive must contain exactly the four expected files.')
        contents = {}
        for member in members:
            if not member.isreg() or member.issparse() or not 0 < member.size <= FILE_LIMITS[member.name]:
                raise ValueError('Signing archive contains an invalid file type or size.')
            stream = archive.extractfile(member)
            if stream is None:
                raise ValueError('Signing archive entry could not be read.')
            data = stream.read(FILE_LIMITS[member.name] + 1)
            if len(data) != member.size:
                raise ValueError('Signing archive file size mismatch.')
            if member.name.endswith('.txt'):
                value = data.decode('utf-8')
                if '\x00' in value or '\r' in value or '\n' in value:
                    raise ValueError('Signing credential files must contain a single value without line breaks.')
            contents[member.name] = data
    # Exact names, regular files, no links, and exclusive private creation; never extractall.
    for name, data in contents.items():
        fd = os.open(destination / name, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        with os.fdopen(fd, 'wb') as stream:
            stream.write(data)


def main():
    os.umask(0o077)
    repo = os.environ.get('GITHUB_REPOSITORY', '')
    run_id = os.environ.get('GITHUB_RUN_ID', '')
    attempt = os.environ.get('GITHUB_RUN_ATTEMPT', '')
    token = os.environ.get('GH_TOKEN', '')
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repo) or not re.fullmatch(r'\d+', run_id) or not re.fullmatch(r'\d+', attempt) or not token:
        raise ValueError('Repository, run identity and read token are required.')
    recipient = Path(os.environ['RUNNER_TEMP']) / 'signing-import'
    certificate = recipient / 'recipient-public.pem'
    private_key = recipient / 'recipient-private.pem'
    if not certificate.is_file() or not private_key.is_file():
        raise ValueError('This job has no temporary signing recipient.')
    destination = Path(sys.argv[1])
    if not destination.is_dir() or any(destination.iterdir()):
        raise ValueError('Signing import destination must be an empty private directory.')
    inbox_path = f'photodroid/signing-inbox/{run_id}-{attempt}.cms.b64'
    url = f'https://api.github.com/repos/{repo}/contents/{urllib.parse.quote(inbox_path, safe="/")}?ref=photodroid-ko'
    request = urllib.request.Request(url, headers={
        'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'PhotoDroid-signing-import',
        'Cache-Control': 'no-cache',
    })
    deadline = time.monotonic() + 20 * 60
    seen_sha = None
    last_heartbeat = 0.0
    print(f'Waiting up to 20 minutes for encrypted signing input at {inbox_path}.', flush=True)
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                raw_response = response.read(MAX_RESPONSE + 1)
            if len(raw_response) > MAX_RESPONSE:
                raise ValueError('GitHub response exceeds its size limit.')
            payload = json.loads(raw_response)
            blob_sha = payload.get('sha') if isinstance(payload, dict) else None
            if not isinstance(blob_sha, str) or not re.fullmatch(r'[0-9a-f]{40}', blob_sha):
                raise ValueError('GitHub inbox has no valid blob identity.')
            if blob_sha != seen_sha:
                seen_sha = blob_sha
                cms = decode_inbox(payload, inbox_path)
                cms_path = recipient / 'received.cms'
                tar_path = recipient / 'received.tar'
                cms_path.write_bytes(cms)
                result = subprocess.run([
                    'openssl', 'cms', '-decrypt', '-binary', '-inform', 'DER',
                    '-in', str(cms_path), '-recip', str(certificate), '-inkey', str(private_key), '-out', str(tar_path),
                ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)
                cms_path.unlink(missing_ok=True)
                if result.returncode == 0:
                    try:
                        extract_signing(tar_path, destination)
                    finally:
                        tar_path.unlink(missing_ok=True)
                    print('Encrypted signing archive imported; required certificate fingerprint is checked before use.', flush=True)
                    return
                tar_path.unlink(missing_ok=True)
                # A rerun gets a new recipient. Wait for the old attempt's ciphertext to be replaced.
                print('Inbox envelope is not addressed to this job recipient; waiting for an updated envelope.', flush=True)
        except urllib.error.HTTPError as error:
            if error.code not in (404, 429, 500, 502, 503, 504):
                raise ValueError(f'GitHub refused the signing inbox request (HTTP {error.code}).') from None
        except (urllib.error.URLError, TimeoutError):
            pass
        except (binascii.Error, UnicodeError, json.JSONDecodeError):
            raise ValueError('The signing inbox is not valid bounded base64 content.') from None
        now = time.monotonic()
        if now - last_heartbeat >= 60:
            print('Waiting for the encrypted inbox; no plaintext key has been uploaded or logged.', flush=True)
            last_heartbeat = now
        time.sleep(min(15, max(0, deadline - now)))
    raise ValueError('Timed out waiting for encrypted signing input. No new release key was generated.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, tarfile.TarError, subprocess.SubprocessError) as error:
        if isinstance(error, ValueError):
            sys.exit(str(error))
        sys.exit('Signing import failed during a local file or cryptographic operation.')
