#!/usr/bin/env bash
set -euo pipefail
adb devices
npm run cap:sync
npx cap run android
