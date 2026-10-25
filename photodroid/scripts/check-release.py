#!/usr/bin/env python3
"""Read-only guard: a released version must never be replaced by a new signing key."""
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

repo = os.environ.get('GITHUB_REPOSITORY', '')
tag = os.environ.get('RELEASE_TAG', '')
token = os.environ.get('GH_TOKEN', '')
if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repo) or not tag or not token:
    sys.exit('Repository, release tag and a GitHub token are required for the release guard.')
api = os.environ.get('GITHUB_API_URL', 'https://api.github.com').rstrip('/')
url = f'{api}/repos/{repo}/releases/tags/{urllib.parse.quote(tag, safe="")}'
request = urllib.request.Request(url, headers={
    'Accept': 'application/vnd.github+json',
    'Authorization': f'Bearer {token}',
    'X-GitHub-Api-Version': '2022-11-28',
    'User-Agent': 'PhotoDroid-Korean-build',
})
try:
    with urllib.request.urlopen(request, timeout=30):
        pass
except urllib.error.HTTPError as error:
    if error.code == 404:
        print(f'No existing release for {tag}; creation may proceed.')
        sys.exit(0)
    sys.exit(f'Release check failed with HTTP {error.code}; refusing to assume the release is absent.')
except urllib.error.URLError:
    sys.exit('Release check failed because GitHub could not be reached.')
sys.exit(f'Release {tag} already exists. It will not be replaced. Reuse the original signing key and increase the version for an update.')
