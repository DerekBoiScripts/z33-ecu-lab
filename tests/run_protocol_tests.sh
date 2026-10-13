#!/usr/bin/env sh
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/tests/out"
javac -d "$ROOT/tests/out" "$ROOT/app/src/main/java/com/z33eculab/BlueProtocol.java" "$ROOT/app/src/main/java/com/z33eculab/ResponseParser.java" "$ROOT/app/src/main/java/com/z33eculab/LivePollScheduler.java" "$ROOT/tests/ProtocolTest.java"
java -cp "$ROOT/tests/out" ProtocolTest
