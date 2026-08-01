#!/bin/bash
# Verifies the downloaded signing artifacts and writes them into .env.
#
# Usage: ./fill-env.sh <dist.cer> <profile.mobileprovision> <AuthKey_XXXX.p8> <key-id> <issuer-id>
#
# The .cer from Apple is only half a certificate — it must be joined with the
# private key from the CSR to make a .p12 that can actually sign. This does that
# join with /tmp/dist.key, so run it on the machine where the CSR was generated.

set -euo pipefail

CER="${1:?path to .cer downloaded from developer.apple.com}"
PROFILE="${2:?path to .mobileprovision}"
P8="${3:?path to AuthKey_XXXXXXXXXX.p8}"
KEY_ID="${4:?App Store Connect Key ID}"
ISSUER_ID="${5:?App Store Connect Issuer ID}"

ENV_FILE="$(dirname "$0")/.env"
[ -f /tmp/dist.key ] || { echo "FAIL: /tmp/dist.key missing — the CSR's private key. Regenerate the CSR and request a new certificate."; exit 1; }

# --- certificate -> .p12 --------------------------------------------------
P12_PASS=$(openssl rand -base64 18 | tr -d '\n')
openssl x509 -inform DER -in "$CER" -out /tmp/dist.pem 2>/dev/null \
  || openssl x509 -inform PEM -in "$CER" -out /tmp/dist.pem

SUBJECT=$(openssl x509 -in /tmp/dist.pem -noout -subject)
case "$SUBJECT" in
  *"Apple Distribution"*|*"iPhone Distribution"*) ;;
  *) echo "FAIL: not a distribution certificate:"; echo "  $SUBJECT"; exit 1 ;;
esac

# Confirm the cert actually matches the private key, or signing fails cryptically later.
CERT_MOD=$(openssl x509 -noout -modulus -in /tmp/dist.pem | openssl md5)
KEY_MOD=$(openssl rsa -noout -modulus -in /tmp/dist.key | openssl md5)
[ "$CERT_MOD" = "$KEY_MOD" ] || { echo "FAIL: certificate does not match /tmp/dist.key"; exit 1; }

openssl pkcs12 -export -inkey /tmp/dist.key -in /tmp/dist.pem \
  -out /tmp/dist.p12 -passout "pass:$P12_PASS" -legacy 2>/dev/null \
  || openssl pkcs12 -export -inkey /tmp/dist.key -in /tmp/dist.pem \
       -out /tmp/dist.p12 -passout "pass:$P12_PASS"

# --- provisioning profile -------------------------------------------------
security cms -D -i "$PROFILE" > /tmp/profile.plist 2>/dev/null
PROFILE_NAME=$(/usr/libexec/PlistBuddy -c 'Print :Name' /tmp/profile.plist)
APP_ID=$(/usr/libexec/PlistBuddy -c 'Print :Entitlements:application-identifier' /tmp/profile.plist)

if /usr/libexec/PlistBuddy -c 'Print :ProvisionedDevices' /tmp/profile.plist >/dev/null 2>&1; then
  echo "FAIL: '$PROFILE_NAME' lists specific devices — that is a Development or"
  echo "      Ad Hoc profile. App Store uploads need an App Store profile."
  exit 1
fi

case "$APP_ID" in
  *cx.m42.agentoz) ;;
  *) echo "FAIL: profile is for $APP_ID, expected cx.m42.agentoz"; exit 1 ;;
esac

# --- App Store Connect key ------------------------------------------------
grep -q "BEGIN PRIVATE KEY" "$P8" || { echo "FAIL: $P8 is not a .p8 private key"; exit 1; }

# --- write ----------------------------------------------------------------
b64() { base64 -i "$1" | tr -d '\n'; }

python3 - "$ENV_FILE" <<PYEOF
import re, sys
path = sys.argv[1]
vals = {
    "IOS_DISTRIBUTION_CERTIFICATE_BASE64": "$(b64 /tmp/dist.p12)",
    "IOS_DISTRIBUTION_CERTIFICATE_PASSWORD": "$P12_PASS",
    "IOS_PROVISIONING_PROFILE_BASE64": "$(b64 "$PROFILE")",
    "APP_STORE_CONNECT_API_KEY_ID": "$KEY_ID",
    "APP_STORE_CONNECT_ISSUER_ID": "$ISSUER_ID",
    "APP_STORE_CONNECT_API_KEY_BASE64": "$(b64 "$P8")",
}
text = open(path).read()
for k, v in vals.items():
    text = re.sub(rf"^{k}=.*$", f"{k}={v}", text, flags=re.M)
open(path, "w").write(text)
PYEOF

chmod 600 "$ENV_FILE"
rm -f /tmp/dist.pem /tmp/profile.plist

echo "OK  certificate : $SUBJECT"
echo "OK  profile     : $PROFILE_NAME ($APP_ID)"
echo "OK  ASC key     : $KEY_ID"
echo
echo "Wrote 6 values to .env. Keep the original .p8 somewhere safe — it cannot be re-downloaded."
