<p align="center">
<img src="./docs/images/LOGO.png" height="80"/>
</p>

<div align="center">

[![GitHub stars](https://img.shields.io/github/stars/litongle/GoGoGo?logo=github)](https://github.com/litongle/GoGoGo/stargazers)
[![GitHub forks](https://img.shields.io/github/forks/litongle/GoGoGo?logo=github)](https://github.com/litongle/GoGoGo/network)
[![license](https://img.shields.io/github/license/litongle/GoGoGo)](https://github.com/litongle/GoGoGo/blob/master/LICENSE)
[![GitHub Release](https://img.shields.io/github/v/release/litongle/GoGoGo?label=Release)](https://github.com/litongle/GoGoGo/releases)
</div>

<div align="center">
GoGoGo - A mock location app without root on Android 8.0+.
</div>

## Overview
This fork migrates the original project from Baidu Map SDK to AMap SDK and keeps the original mock-location + joystick workflow.

Chinese documentation is available in [README.md](./README.md).

## Key changes in this fork
- Replaced Baidu map, location and search capabilities with AMap Android SDK
- Unified map/search/UI coordinates to GCJ02
- Kept mock output in WGS84 for Android test providers
- Added compatibility for legacy history records stored in BD09

## Build
Set the following values in `local.properties`:

```properties
sdk.dir=YOUR_ANDROID_SDK_PATH
MAPS_API_KEY=YOUR_AMAP_ANDROID_KEY
```

## Repository
- Source: [https://github.com/litongle/GoGoGo](https://github.com/litongle/GoGoGo)
- Issues: [https://github.com/litongle/GoGoGo/issues](https://github.com/litongle/GoGoGo/issues)

## License
GPL-3.0-only
