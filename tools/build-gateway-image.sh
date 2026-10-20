#!/usr/bin/env bash
# Build the clad-gateway and clad-mdr jars and the localhost/clad-gateway:<version> image (podman).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
(cd "$ROOT/app" && sbt -batch gateway/assembly mdr/assembly)
VERSION="$(cd "$ROOT/app" && sbt -batch -Dsbt.supershell=false 'print gateway/version' | tail -1)"
cp "$ROOT/app/gateway/target/scala-3.3.8/clad-gateway.jar" "$ROOT/app/gateway/clad-gateway.jar"
cp "$ROOT/app/mdr/target/scala-3.3.8/clad-mdr.jar" "$ROOT/app/gateway/clad-mdr.jar"
podman build -t "localhost/clad-gateway:$VERSION" -t localhost/clad-gateway:latest "$ROOT/app/gateway"
rm -f "$ROOT/app/gateway/clad-gateway.jar" "$ROOT/app/gateway/clad-mdr.jar"
echo "built localhost/clad-gateway:$VERSION"
