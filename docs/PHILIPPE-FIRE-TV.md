# Philippe's Fire TV (TCL 55S450F-CA)

This copy of [Seagey/couch-tv](https://github.com/Seagey/couch-tv) is for the living-room set:

| | |
|---|---|
| Name on the TV | TV - Philippe |
| Model | TCL_55S450F-CA (Fire TV Edition, Canada) |
| Software | Fire OS 7.7.1.7 |
| Wi-Fi | Same network as the Windows PC (SSID on the TV Network screen) |
| TV IP | 192.168.1.110 |
| Path | **Option A:** sideload the home app onto the TV. No computer stays on. |

Amazon's home cannot be replaced. couch-tv sits on top of it. Pressing Home flashes Amazon's home for a moment, then couch-tv comes back. Never disable `com.amazon.tv.launcher`.

**Already-installed apps stay.** The installer only adds `couch-tv-home.apk`. It does not uninstall, update, or replace **TiviMate** (`ar.tvplayer.tv`) or anything else. If TiviMate is on the TV, the home-row tile opens that same package.

Dock (first three, if those apps are installed): YouTube, Crave, TiviMate. Language: English. Weather: Notre-Dame-des-Pins, QC (nearest town for the home's weather; no street address).

This TCL's GPU is modest. **Lighter home** is on: no blur, no glass panels, no tile sway. That is the same setting Couch TV uses on Fire sticks. You can also switch it in Home settings → Look. The layout stays the same.

## 1. On the TV (once)

1. Settings → **My Fire TV** → **About**. Highlight the first line, press OK **seven times**.
2. Developer options: **ADB debugging** on, **Apps from Unknown Sources** on.
3. Stay on the TV. The PC's first connection asks **Allow USB debugging?** Tick **Always allow**, then **Allow**.

PC and TV must be on the same Wi-Fi. Gateway on the TV was 192.168.1.1.

## 2. On the Windows PC (PowerShell)

Python is not on this PC. Download the official 64-bit installer (last 3.12 with a Windows installer):

https://www.python.org/ftp/python/3.12.10/python-3.12.10-amd64.exe

On the first screen tick **Add python.exe to PATH**, then **Install Now**. Close PowerShell. PATH only updates in a **new** window.

Already cloned at `C:\Users\Phili\couch-tv`. In a **new** PowerShell window:

```powershell
cd C:\Users\Phili\couch-tv
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r bridge\requirements.txt
# If Windows Firewall asks, Allow Python on private networks.
.\scripts\install-fire-tv.ps1 -TvIp 192.168.1.110
```

When the TV shows **Allow USB debugging?**, tick **Always allow** and **Allow**.

That installs only Couch TV Home. TiviMate is left alone. Keep watching is off. Amazon's launcher stays installed; Home may still flash briefly (Fire OS starts it by name; without root that cannot be removed). This copy intercepts Home when Fire OS delivers the key, covers the flash with an overlay, and turns off window animations.

Hold OK on a dock app to move it if the three tiles are not YouTube, Crave, TiviMate.

To update after `git pull` (same PC, same TV):

```powershell
cd C:\Users\Phili\couch-tv
git pull
.\scripts\install-fire-tv.ps1 -TvIp 192.168.1.110
```

### Undo (Couch TV only; TiviMate stays)

```powershell
.\scripts\install-fire-tv.ps1 -Undo -TvIp 192.168.1.110
```

Or uninstall **Couch TV** / `app.couchtv.launcher` in Settings → Applications.

## Limits

- Amazon's home can still flash for a moment. Fire OS 7 starts `com.amazon.tv.launcher` by name; it is not disabled. The overlay + no window animation is as short as this path allows.
- This PC is needed once, then it can sleep. The cloud agent cannot reach 192.168.1.110.
- Keep watching is hidden (`home.keep_watching: false`). Turn it back on in Home settings, Look, if wanted.
- Phone-remote keys do not work on Fire OS 7.7.1.7 without a computer left on (skipped).

## Upstream

MIT, Copyright (c) 2026 Seagey. `git remote add upstream https://github.com/Seagey/couch-tv.git` then `git fetch upstream`.
