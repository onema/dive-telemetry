#!/usr/bin/env bash
set -euo pipefail

echo "==> ktlint"
./gradlew ktlintCheck

echo "==> detekt"
./gradlew detekt

echo "==> tests (JVM + macOS native)"
./gradlew :lib:jvmTest :lib:macosArm64Test :app:test :cli:jvmTest

echo "==> docs (MkDocs build)"
if command -v mkdocs &>/dev/null; then
    mkdocs build --strict
else
    echo "     mkdocs not installed — skipping (pip install mkdocs-material)"
fi

echo ""
echo "All checks passed."
