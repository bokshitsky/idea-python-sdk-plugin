#!/usr/bin/env bash
set -euo pipefail

version="${1:-}"
if [ -z "$version" ]; then
  echo "Usage: ./release.sh <version>   e.g. ./release.sh 1.0.0" >&2
  exit 1
fi

tag="v${version#v}"   # accept both "1.0.0" and "v1.0.0"

git tag "$tag"
git push origin "$tag"

echo "Pushed $tag — the Release workflow will build and publish it."
