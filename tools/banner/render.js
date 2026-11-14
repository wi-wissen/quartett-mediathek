// Renders banner.html to the xhdpi TV banner (640x360 px = 320x180 dp). Requires Playwright.
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 320, height: 180 }, deviceScaleFactor: 2 });
  await page.goto('file://' + path.resolve(__dirname, 'banner.html'));
  await page.waitForTimeout(500);
  await page.screenshot({ path: path.resolve(__dirname, '../../app/src/main/res/drawable-xhdpi/banner.png') });
  await browser.close();
})();
