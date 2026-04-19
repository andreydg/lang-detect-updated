#!/bin/bash
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
exec ./mvnw -q -pl lang-detect-utils -am exec:java \
  -Dexec.mainClass=language.tools.LanguageDetectorTester \
  -Dexec.args="-dataPath lang-detect/war -testMultiString"
