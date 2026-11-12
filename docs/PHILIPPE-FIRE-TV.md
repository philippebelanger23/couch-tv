# Philippe's Fire TV (TCL 55S450F-CA)

This copy of [Seagey/couch-tv](https://github.com/Seagey/couch-tv) is for the living-room set:

| | |
|---|---|
| Name on the TV | TV - Philippe |
| Model | TCL_55S450F-CA (55" S Class, Fire TV Edition, Canada) |
| Software | Fire OS 7.7.1.7 (PS7717/5746), Fire TV home 7321874.1 |
| Path | **Option A:** sideload the home app onto the TV. No computer stays on. |

Amazon's home cannot be replaced. couch-tv sits on top of it. Pressing Home flashes Amazon's home for a moment, then couch-tv comes back. That is as far as Fire OS allows without root. Never disable `com.amazon.tv.launcher`.

The same Fire OS build (7.7.1.7) is already in the upstream issue tracker ([#12](https://github.com/Seagey/couch-tv/issues/12)). A Fire TV Edition TV (not a stick) is also reported working as a home screen ([#50](https://github.com/Seagey/couch-tv/issues/50)).

## What this path does

- New home screen on the TV (scenes, clock, weather, your apps, Keep watching from Android's Watch Next list).
- The Fire TV remote keeps working.
- Bluetooth / Network / Input shortcuts that Fire OS hides in long-press Home.
- Amazon's YouTube app is recognised as YouTube.

What it does **not** do on this Fire OS:

- No always-on computer / NAS / Raspberry Pi.
- The on-TV phone remote's D-pad, typing and "what's playing" do **not** work: current Fire OS (including 7.7.1.7) blocks an app from using the TV's own ADB. Opening apps from a phone still works if you later install the on-TV app. For a full phone remote you would need a computer on the LAN (option B), which this setup skips.

## One-time computer

A laptop on the **same Wi-Fi** as the TV is needed **once** (macOS or Linux, Python 3.11+). After `install-fire-tv.sh` finishes, that computer can go away.

### 1. On the TV

1. Settings → **My Fire TV** → **About**. Highlight the first line (usually the device name) and press OK **seven times** until it says you are a developer.
2. Back, **Developer options**:
   - **ADB debugging** on
   - **Apps from Unknown Sources** on
3. Settings → **My Fire TV** → **About** → **Network**. Note the IPv4 address (four numbers). Give the TV a DHCP reservation in the router if you can, so that address stays put.
4. Stay on the TV. The first ADB connection asks **Allow USB debugging?** Tick **Always allow**, then **Allow**.

### 2. On the computer

```sh
git clone https://github.com/philippebelanger23/couch-tv.git
cd couch-tv
python3 -m venv .venv && .venv/bin/pip install -r bridge/requirements.txt
cp config.fire-tv.example.json config.json   # then put the TV's IP in tvs[0].ip
sh scripts/fetch-apks.sh
TV_IP=<the address from step 1> sh scripts/install-fire-tv.sh
```

`install-fire-tv.sh` installs `couch-tv-home.apk`, makes couch-tv the chosen home, and switches on the Home button watch. Amazon's launcher stays installed.

Press Home: Amazon's home may flash, then couch-tv. Open the gear tile on the dock for language, city/weather, people and app order.

### Undo

```sh
TV_IP=<the address> sh tv-home/tools/undo.sh
```

Or uninstall `app.couchtv.launcher` from Settings → Applications. Amazon's home is then the Home key again.

## Honest limits

- Amazon's home still flashes first. Do not switch Amazon's launcher off.
- Keep watching is Android's Watch Next row (same for everyone on the TV), not a per-person history from a computer.
- A Google / Fire OS update can drop the Home watch. Run `install-fire-tv.sh` again; it is safe to repeat.
- This TCL is a Fire TV Edition, not a TCL Google TV. If Home falls back to Amazon after a full power cut, say so: some TCL sets also need `AUTO_START` (the installer already tries that grant).
- About 21 MB for the home APK. The TV had ~6 GB free.

## Upstream

MIT, Copyright (c) 2026 Seagey. This repo keeps their history and adds the Fire TV home path above. Pull updates with `git fetch upstream && git merge upstream/main` after `git remote add upstream https://github.com/Seagey/couch-tv.git`.
