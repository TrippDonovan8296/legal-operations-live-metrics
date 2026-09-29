#!/usr/bin/env sh
set -eu

build_dir="${TMPDIR:-/tmp}/legal-metrics-classes"
mkdir -p "$build_dir"
javac -d "$build_dir" $(find src/main/java -name '*.java')
java -cp "$build_dir" legalops.LegalMetricsApplication
