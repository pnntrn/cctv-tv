#!/usr/bin/env bash
# Run once on a trusted computer with JDK 17, Python 3 and authenticated GitHub CLI.
# The private key never enters git or workflow artifacts.
set -euo pipefail
umask 077
repo_name=pnntrn/cctv-apk
key_dir="${CCTV_SIGNING_DIRECTORY:-$HOME/cctv-signing}"
mkdir -p "$key_dir"
if [ -e "$key_dir/release.p12" ] || [ -e "$key_dir/signing.json" ]; then
  echo 'Existing signing material found. Keep it; do not replace the signing identity.' >&2
  exit 1
fi
command -v gh >/dev/null
command -v keytool >/dev/null
command -v python3 >/dev/null
# Fail before generating a key if repository secret access is unavailable.
gh api "repos/$repo_name/actions/secrets/public-key" >/dev/null
CCTV_NEW_PASSWORD=$(python3 -c 'import secrets; print(secrets.token_urlsafe(36))')
export CCTV_NEW_PASSWORD
keytool -genkeypair -keystore "$key_dir/release.p12" -storetype PKCS12 \
  -alias cctv -keyalg RSA -keysize 3072 -validity 10000 \
  -storepass:env CCTV_NEW_PASSWORD -keypass:env CCTV_NEW_PASSWORD \
  -dname 'CN=CCTV, OU=Android TV, O=Private development' >/dev/null 2>&1
python3 - "$key_dir" <<'PY'
import os,sys,pathlib,json,base64
p=pathlib.Path(sys.argv[1])
(p/'signing.json').write_text(json.dumps({'alias':'cctv','password':os.environ['CCTV_NEW_PASSWORD'],'keystore':base64.b64encode((p/'release.p12').read_bytes()).decode()}))
PY
unset CCTV_NEW_PASSWORD
gh secret set CCTV_SIGNING_BUNDLE --repo "$repo_name" < "$key_dir/signing.json"
echo 'Retained signing key configured. Keep the private backup directory safe. Run the Android workflow to build the signed release.'
