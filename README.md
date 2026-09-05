# Potato 🥔

A minimalist Pomodoro timer, tweaked, rebranded, and perfected for my own personal workflow. 

This is a custom fork of the beautiful [Tomato app by nsh07](https://github.com/nsh07/Tomato).

---

### 🚀 What's different in this build?

- **Rebranded**: Fresh new name and a custom high-quality launcher icon!
- **Automatic Google Drive Backups**: Integrated direct **Google Drive API (v3)** syncing. The app securely and silently backs up the SQLite database to the private Google Drive AppData folder every 24 hours via `WorkManager`. This completely bypasses restrictive OEM/system backup limits (like those on Oppo/ColorOS).
- **Fully Unlocked**: All premium features are permanently unlocked out of the box.
- **Accurate Statistics**: Rewrote the average focus time calculation to correctly measure averages across the entire calendar window (not just active days), giving honest stats.
- **Zero Distractions**: Stripped out all the donation links, author pages, Discord banners, translation widgets, and paywall overlays. Just me and the timer.
- **Side-by-Side Install**: Uses a custom Application ID (`org.nsh07.pomodoro.personal`) so it can be installed alongside the original app without conflicts.

### 🛠️ Building

This project is configured with GitHub Actions. 
Pushing to the `main` branch will automatically run the CI pipeline, decode the secure release keystore, and build an optimized Release APK!

---
*(Original app structure and UI credits to nsh07. Material 3 Expressive base.)*
