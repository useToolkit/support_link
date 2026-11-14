#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/src/main/assets
model="app/src/main/assets/face_landmarker.task"
if [[ ! -s "$model" ]]; then
  curl -fL --retry 3 -o "$model"     https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task
fi
printf '%s  %s\n' "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff" "$model" | sha256sum -c -
