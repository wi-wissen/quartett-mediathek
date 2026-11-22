#!/usr/bin/env bash
# Assembles the GitHub Pages site into _site: the page plus screenshots and fonts taken from the app,
# so the website always shows the current screenshots without keeping copies.
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf _site
mkdir -p _site/screenshots _site/fonts
cp site/index.html site/logo.svg _site/
cp app/src/test/screenshots/de_*.png _site/screenshots/
cp app/src/main/res/font/*.ttf _site/fonts/
echo "quartett-mediathek.wi7.net" > _site/CNAME
