#!/usr/bin/env sh
set -eu

APK_PATH=${1:?usage: validate-privapp-permissions.sh <apk> <permissions-xml> <manifest path|dir>}
PERMISSIONS_XML=${2:?usage: validate-privapp-permissions.sh <apk> <permissions-xml> <manifest path|dir>}
SOURCE_PATH=${3:?usage: validate-privapp-permissions.sh <apk> <permissions-xml> <manifest path|dir>}

if ! command -v aapt2 >/dev/null 2>&1; then
  echo "aapt2 is required to validate privapp permissions" >&2
  exit 1
fi

if [ ! -e "$SOURCE_PATH" ]; then
  echo "Manifest source not found: $SOURCE_PATH" >&2
  exit 1
fi

if [ -d "$SOURCE_PATH" ]; then
  manifest_files=$(find "$SOURCE_PATH" -name AndroidManifest.xml -print)
else
  manifest_files="$SOURCE_PATH"
fi

if [ -z "$manifest_files" ]; then
  echo "No AndroidManifest.xml files found under $SOURCE_PATH" >&2
  exit 1
fi

apk_package=$(aapt2 dump badging "$APK_PATH" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n 1)
xml_package=$(sed -n "s/.*<privapp-permissions package=\"\\([^\"]*\\)\".*/\\1/p" "$PERMISSIONS_XML" | head -n 1)

if [ -z "$apk_package" ] || [ -z "$xml_package" ]; then
  echo "Failed to parse APK package or privapp XML package" >&2
  exit 1
fi

if [ "$apk_package" != "$xml_package" ]; then
  echo "Privapp XML package mismatch: APK=$apk_package XML=$xml_package" >&2
  exit 1
fi

apk_permissions=$(
  aapt2 dump badging "$APK_PATH" \
    | sed -n "s/^uses-permission: name='\([^']*\)'.*/\1/p" \
    | sort -u
)
xml_permissions=$(
  sed -n "s/.*<permission name=\"\\([^\"]*\\)\".*/\\1/p" "$PERMISSIONS_XML" \
    | sort -u
)

derived_privapp_permissions=$(
  for manifest in $manifest_files; do
    perl -0777 -ne '
      while (/<uses-permission\b[^>]*>/g) {
        $block = $&;
        if ($block =~ /tools:ignore="ProtectedPermissions"/ && $block =~ /android:name="([^"]+)"/) {
          print "$1\n";
        }
      }
    ' "$manifest"
  done | sort -u
)

if [ -z "$derived_privapp_permissions" ]; then
  echo "No permissions marked with tools:ignore=\"ProtectedPermissions\" under $SOURCE_PATH" >&2
  exit 1
fi

missing=0
stale=0

for permission in $derived_privapp_permissions; do
  if printf '%s\n' "$apk_permissions" | grep -Fxq "$permission" && ! printf '%s\n' "$xml_permissions" | grep -Fxq "$permission"; then
    echo "Missing privapp allowlist entry for $permission" >&2
    missing=1
  fi
done

for permission in $xml_permissions; do
  if ! printf '%s\n' "$apk_permissions" | grep -Fxq "$permission"; then
    echo "Stale privapp allowlist entry not requested by APK: $permission" >&2
    stale=1
    continue
  fi
  if ! printf '%s\n' "$derived_privapp_permissions" | grep -Fxq "$permission"; then
    echo "Stale privapp allowlist entry not derived from protected manifest permissions: $permission" >&2
    stale=1
  fi
done

if [ "$missing" -ne 0 ] || [ "$stale" -ne 0 ]; then
  exit 1
fi

echo "Validated privapp permissions for $apk_package"
