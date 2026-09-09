#!/usr/bin/env bash
# record-test.sh
#
# Records a Selenium run by:
#   1. Launching Xvfb on display :99 at 1400x900x24
#   2. Telling ffmpeg to capture that X display into an MP4
#   3. Running the requested Gradle test under that display
#
# Usage:  ./record-test.sh <gradle-test-name> <output.mp4>
#
# Example:
#   ./record-test.sh GuestRegistrationFormTest videos/form.mp4
#   ./record-test.sh DseSharePriceScrapeTest      videos/dse.mp4

set -euo pipefail

TEST_NAME="${1:?usage: $0 <test-name> <output.mp4>}"
OUT_MP4="${2:?usage: $0 <test-name> <output.mp4>}"

DISPLAY_ID=99
WIDTH=1400
HEIGHT=900
FPS=15

mkdir -p "$(dirname "$OUT_MP4")"

# Clean up any old Xvfb on this display
pkill -f "Xvfb :${DISPLAY_ID}" 2>/dev/null || true
sleep 1

# Start Xvfb
Xvfb :${DISPLAY_ID} -screen 0 ${WIDTH}x${HEIGHT}x24 -ac +extension GLX +render -noreset &
XVFB_PID=$!
trap 'kill ${XVFB_PID} 2>/dev/null || true; pkill -P $$ 2>/dev/null || true' EXIT

# Wait for the X server socket to be ready (polling xdpyinfo)
for i in $(seq 1 50); do
  if DISPLAY=:${DISPLAY_ID} xdpyinfo >/dev/null 2>&1; then
    break
  fi
  sleep 0.2
done

# Start ffmpeg capturing the X display
DISPLAY=:${DISPLAY_ID} ffmpeg -y \
    -f x11grab -video_size ${WIDTH}x${HEIGHT} -framerate ${FPS} -i :${DISPLAY_ID} \
    -c:v libx264 -preset ultrafast -pix_fmt yuv420p \
    -movflags +faststart \
    "$OUT_MP4" \
    >/tmp/ffmpeg-${TEST_NAME}.log 2>&1 &
FFMPEG_PID=$!

# Give ffmpeg ~2s to spin up
sleep 2

# Run the test under the same DISPLAY. Use system 'gradle' since this repo
# doesn't ship a Gradle wrapper.
set +e
DISPLAY=:${DISPLAY_ID} gradle test \
    --rerun-tasks \
    --tests "com.assignment.tests.${TEST_NAME}" \
    --console=plain 2>&1 | tail -30
GRADLE_RC=$?
set -e

# Give ffmpeg a moment to flush its tail
sleep 1
kill ${FFMPEG_PID} 2>/dev/null || true
wait ${FFMPEG_PID} 2>/dev/null || true

echo "----"
echo "Gradle exit: ${GRADLE_RC}"
echo "Video:      ${OUT_MP4}"
ls -la "$OUT_MP4" 2>/dev/null || true

exit ${GRADLE_RC}
