#!/usr/bin/env bash
# Invoked by workflow validation jobs; do not run without lint authorization.
set -euo pipefail
directory="$RUNNER_TEMP/actionlint"
mkdir -p "$directory"
curl --fail --location --retry 3 --proto '=https' --tlsv1.2 \
  https://github.com/rhysd/actionlint/releases/download/v1.7.12/actionlint_1.7.12_linux_amd64.tar.gz \
  --output "$directory/actionlint.tar.gz"
printf '%s  %s\n' \
  8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8 \
  "$directory/actionlint.tar.gz" | sha256sum --check --strict
tar -xzf "$directory/actionlint.tar.gz" -C "$directory" actionlint
printf '%s\n' "$directory" >> "$GITHUB_PATH"
