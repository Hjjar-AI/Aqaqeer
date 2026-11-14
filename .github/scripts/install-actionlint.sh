#!/usr/bin/env bash
# Invoked by workflow validation jobs; do not run without lint authorization.
set -euo pipefail
directory="$RUNNER_TEMP/actionlint"
mkdir -p "$directory"
read -r version checksum <<EOF
$(python3 -c 'import json; p=json.load(open(".github/ci-tools.json"))["actionlint"]; print(p["version"], p["sha256"])')
EOF
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ && "$checksum" =~ ^[0-9a-f]{64}$ ]]
curl --fail --location --retry 3 --proto '=https' --tlsv1.2 \
  "https://github.com/rhysd/actionlint/releases/download/v$version/actionlint_${version}_linux_amd64.tar.gz" \
  --output "$directory/actionlint.tar.gz"
printf '%s  %s\n' "$checksum" "$directory/actionlint.tar.gz" | sha256sum --check --strict
tar -xzf "$directory/actionlint.tar.gz" -C "$directory" actionlint
printf '%s\n' "$directory" >> "$GITHUB_PATH"
