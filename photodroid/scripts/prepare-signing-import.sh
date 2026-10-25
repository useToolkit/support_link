#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077
source "$(dirname -- "${BASH_SOURCE[0]}")/config.sh"
: "${RUNNER_TEMP:?Runner temporary directory is required}"
: "${GITHUB_OUTPUT:?GitHub step output file is required}"
: "${GITHUB_RUN_ID:?GitHub run identity is required}"
: "${GITHUB_RUN_ATTEMPT:?GitHub run attempt is required}"
provided=0
for secret_name in ANDROID_KEYSTORE_BASE64 ANDROID_STORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do
  if [[ -n "${!secret_name:-}" ]]; then provided=$((provided + 1)); fi
done
if [[ "$provided" -eq 4 ]]; then
  printf 'needs_import=false\n' >> "$GITHUB_OUTPUT"
  exit 0
fi
if [[ "$provided" -ne 0 ]]; then
  echo 'All four ANDROID_* secrets must be supplied together.'
  exit 1
fi
import_dir="$RUNNER_TEMP/signing-import"
test ! -e "$import_dir" || { echo 'A signing recipient already exists in this job.'; exit 1; }
mkdir -m 700 "$import_dir"
ready=false
cleanup_failure() { if [[ "$ready" != true ]]; then rm -rf -- "$import_dir"; fi; }
trap cleanup_failure EXIT
openssl req -x509 -newkey rsa:3072 -nodes -sha256 -days 2 \
  -keyout "$import_dir/recipient-private.pem" -out "$import_dir/recipient-public.pem" \
  -subj '/CN=PhotoDroid temporary signing-key recipient' > "$import_dir/keygen.log" 2>&1
python3 - <<'PY'
import hashlib, json, os, pathlib, subprocess
folder = pathlib.Path(os.environ['RUNNER_TEMP']) / 'signing-import'
der = subprocess.check_output(['openssl', 'x509', '-in', str(folder / 'recipient-public.pem'), '-outform', 'DER'])
info = {
    'repository': os.environ['GITHUB_REPOSITORY'],
    'workflow_commit': os.environ['GITHUB_SHA'],
    'run_id': os.environ['GITHUB_RUN_ID'],
    'run_attempt': os.environ['GITHUB_RUN_ATTEMPT'],
    'recipient_certificate_sha256': hashlib.sha256(der).hexdigest(),
    'required_apk_signer_sha256': os.environ['REQUIRED_SIGNER_SHA256'],
    'inbox_branch': 'photodroid-ko',
    'inbox_path': f"photodroid/signing-inbox/{os.environ['GITHUB_RUN_ID']}-{os.environ['GITHUB_RUN_ATTEMPT']}.cms.b64",
    'expires_after_minutes': 20,
    'note': 'Verify the repository, run, attempt and workflow commit before encrypting. A rerun replaces the recipient; old ciphertext cannot be reused.',
}
(folder / 'recipient-info.json').write_text(json.dumps(info, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f"Recipient prepared for run {info['run_id']}, attempt {info['run_attempt']}.")
print(f"Public recipient SHA-256: {info['recipient_certificate_sha256']}")
PY
ready=true
printf 'needs_import=true\n' >> "$GITHUB_OUTPUT"
