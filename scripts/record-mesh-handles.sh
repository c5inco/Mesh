#!/usr/bin/env bash
set -euo pipefail

DISPLAY_NUM="${DISPLAY_NUM:-99}"
export DISPLAY=":${DISPLAY_NUM}"
ARTIFACT_DIR="${ARTIFACT_DIR:-/home/user/workspace/repo/.amp/in/artifacts}"
SCREENSHOT_DIR="${SCREENSHOT_DIR:-/home/user/workspace/repo/composeApp/build/spectre-screenshots}"
mkdir -p "$ARTIFACT_DIR" "$SCREENSHOT_DIR"

Xvfb ":${DISPLAY_NUM}" -screen 0 1280x800x24 -ac +extension GLX +render -noreset >/tmp/mesh-handles-xvfb.log 2>&1 &
XVFB_PID=$!
cleanup() {
  kill "$FFMPEG_PID" 2>/dev/null || true
  kill "$XVFB_PID" 2>/dev/null || true
}
trap cleanup EXIT
sleep 0.4

ffmpeg -y -f x11grab -video_size 1280x800 -framerate 15 -i ":${DISPLAY_NUM}.0" \
  -c:v libx264 -pix_fmt yuv420p -preset veryfast \
  "$ARTIFACT_DIR/handles-interaction.mp4" >/tmp/mesh-handles-ffmpeg.log 2>&1 &
FFMPEG_PID=$!
sleep 0.3

set +e
./gradlew :composeApp:desktopTest --tests des.c5inco.mesh.MeshHandlePrototypeTest
STATUS=$?
set -e

sleep 0.8
kill -INT "$FFMPEG_PID" 2>/dev/null || true
wait "$FFMPEG_PID" 2>/dev/null || true

cp -f "$SCREENSHOT_DIR"/handles-*.png "$ARTIFACT_DIR/" 2>/dev/null || true
exit "$STATUS"
