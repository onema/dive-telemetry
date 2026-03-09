#!/usr/bin/env bash
set -euo pipefail

echo "==> ktlint"
./gradlew ktlintCheck

echo "==> detekt"
./gradlew detekt

echo "==> tests (JVM + macOS native)"
./gradlew :lib:jvmTest :lib:macosArm64Test :app:test :cli:jvmTest

echo "==> docs (VitePress build)"
if command -v npm &>/dev/null; then
    npm run docs:build
else
    echo "     npm not installed — skipping (install Node.js)"
fi

echo ""
echo "All checks passed."
