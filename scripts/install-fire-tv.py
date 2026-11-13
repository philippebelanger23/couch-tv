#!/usr/bin/env python3
"""Sideload couch-tv home onto a Fire TV (Option A). Works on Windows, macOS and Linux. No bash.

From the repo root, after a venv with bridge/requirements.txt:

  python scripts/install-fire-tv.py --prep          # clone-time: APKs + config.json, no ADB
  python scripts/install-fire-tv.py                 # uses tvs[0].ip or TV_IP
  python scripts/install-fire-tv.py 192.168.1.110
  python scripts/install-fire-tv.py --undo

Does not print or store a serial or MAC. Leaves Amazon's launcher installed.
Never uninstalls other apps (TiviMate / ar.tvplayer.tv stays; the dock only opens it if present).
On Fire OS, Android often keeps Amazon as the listed home; the Home button watch brings couch-tv back.
"""
from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(ROOT)
sys.path.insert(0, os.path.join(ROOT, "tv-home", "tools"))

APK = os.path.join(ROOT, "dist", "couch-tv-home.apk")
EXAMPLE = os.path.join(ROOT, "config.fire-tv.example.json")
CONFIG = os.path.join(ROOT, "config.json")
RELEASE = os.environ.get(
    "RELEASE_BASE",
    "https://github.com/philippebelanger23/couch-tv/releases/latest/download",
)
HOME_ACT = "app.couchtv.launcher/.MainActivity"
WATCH = "app.couchtv.launcher/.FireWatch"
AMAZON_HOME = "com.amazon.tv.launcher"


def py():
    if os.path.isfile(os.path.join(ROOT, ".venv", "Scripts", "python.exe")):
        return os.path.join(ROOT, ".venv", "Scripts", "python.exe")
    if os.path.isfile(os.path.join(ROOT, ".venv", "bin", "python")):
        return os.path.join(ROOT, ".venv", "bin", "python")
    return sys.executable


def fetch_apks():
    os.makedirs(os.path.join(ROOT, "dist"), exist_ok=True)
    for name in ("couch-tv-home.apk", "couch-tv-ontv.apk"):
        dest = os.path.join(ROOT, "dist", name)
        url = f"{RELEASE}/{name}"
        print(f"Downloading {url}")
        urllib.request.urlretrieve(url, dest)
        print(f"  {dest}  {os.path.getsize(dest)} bytes")


def ensure_config(ip: str | None):
    if not os.path.isfile(CONFIG):
        shutil.copyfile(EXAMPLE, CONFIG)
        print("Wrote config.json from config.fire-tv.example.json")
    with open(CONFIG, encoding="utf-8") as f:
        cfg = json.load(f)
    home = cfg.setdefault("home", {})
    if not isinstance(home, dict):
        home = {}
        cfg["home"] = home
    home["keep_watching"] = False
    skip = home.get("skip_watch_next")
    if not isinstance(skip, list):
        skip = []
    if "ca.bellmedia.cravetv" not in skip:
        skip.append("ca.bellmedia.cravetv")
    home["skip_watch_next"] = skip
    home["lighter"] = True
    home["glass"] = "off"
    home["moving_tiles"] = False
    with open(CONFIG, "w", encoding="utf-8") as f:
        json.dump(cfg, f, indent=2)
        f.write("\n")
    if ip:
        for tv in cfg.get("tvs") or []:
            if tv.get("id") == "living-room" or tv.get("ip") in ("SET_YOUR_TV_IP", "", None):
                tv["ip"] = ip
                break
        else:
            cfg.setdefault("tvs", [{}])[0]["ip"] = ip
        with open(CONFIG, "w", encoding="utf-8") as f:
            json.dump(cfg, f, indent=2)
            f.write("\n")
        print("config.json tvs ip ->", ip)
    return cfg


def config_ip(cfg) -> str:
    env = (os.environ.get("TV_IP") or "").strip()
    if env:
        return env
    tvs = cfg.get("tvs") or []
    return str((tvs[0] if tvs else {}).get("ip") or "")


def packages(text: str) -> set[str]:
    return {ln.replace("package:", "").strip() for ln in text.splitlines() if ln.strip()}


def apply(ip: str):
    os.environ["TV_IP"] = ip
    os.environ.setdefault("TVADB_AUTH", "30")
    import tvadb  # noqa: WPS433  (repo tool, after sys.path)

    print()
    print("On the TV: a box will ask Allow USB debugging?")
    print("Tick Always allow from this computer, then Allow. You have about 30 seconds.")
    print()
    try:
        tv = tvadb.Tv(tvadb.connect)
    except ConnectionError as e:
        sys.exit(
            f"{e}\n"
            "On the TV: Settings, My Fire TV, About (press the first line 7 times), "
            "Developer options: ADB debugging ON and Apps from Unknown Sources ON. "
            "PC and TV must be on the same Wi-Fi. Then run this again."
        )

    def sh(cmd, timeout=60):
        return (tv.shell(cmd, timeout_s=timeout) or "").replace("\r", "")

    def sh_ok(cmd):
        try:
            sh(cmd)
        except Exception:
            pass

    print("1/4  Installing couch-tv-home.apk only (does not touch TiviMate or other apps)")
    out = tvadb.push_and_install(tv, APK)
    if "Success" not in out:
        mismatch = "UPDATE_INCOMPATIBLE" in out or "signatures" in out.lower()
        if mismatch:
            print("     Replacing Couch TV only (new signing key). TiviMate is not uninstalled.")
            sh("pm uninstall app.couchtv.launcher")
            out = tvadb.push_and_install(tv, APK)
        if "Success" not in out:
            sys.exit("the TV did not install couch-tv-home.apk (TiviMate was not changed)")
    sh(f"cmd package set-home-activity {HOME_ACT}")
    now = sh("cmd shortcut get-default-launcher")
    present = packages(sh("pm list packages"))
    fire = AMAZON_HOME in present
    tivimate = "ar.tvplayer.tv"
    if tivimate in present:
        print("     TiviMate is still installed (untouched). Couch TV will open that same app.")
    else:
        print("     TiviMate is not on this TV. Couch TV will not install or replace it.")
    if "app.couchtv.launcher" not in now:
        if fire:
            print("     Fire OS kept Amazon as the listed home (normal). Home button watch will bring couch-tv back.")
        else:
            sys.exit(f"The TV did not take couch-tv as its home (it says: {now.strip() or now!r}). Nothing else was changed.")

    sh_ok("pm grant app.couchtv.launcher android.permission.READ_TV_LISTINGS")
    sh_ok("appops set app.couchtv.launcher WRITE_SETTINGS allow")
    sh_ok("appops set app.couchtv.launcher GET_USAGE_STATS allow")
    sh_ok("appops set app.couchtv.launcher SYSTEM_ALERT_WINDOW allow")
    sh_ok("appops set app.couchtv.launcher AUTO_START allow")
    # Shorten the Amazon-home flash: no window/transition animation. Does not disable Amazon's launcher.
    sh_ok("settings put global window_animation_scale 0")
    sh_ok("settings put global transition_animation_scale 0")
    sh_ok("settings put global animator_duration_scale 0")

    print("2/4  Amazon's launcher stays on")
    print("3/4  Home button watch + settings")
    exe = py()
    cfg64 = subprocess.check_output([exe, os.path.join(ROOT, "tv-home", "tools", "home-config.py")], cwd=ROOT, text=True).strip()
    ha64 = subprocess.check_output(
        [exe, os.path.join(ROOT, "tv-home", "tools", "home-config.py"), "--ha"], cwd=ROOT, text=True
    ).strip()
    start = (
        f"am force-stop app.couchtv.launcher; am start -n {HOME_ACT} "
        f"--es bridge none --es config_b64 {cfg64} --es ha_b64 {ha64} --es tv living-room --es key \"\""
    )
    sh(start, timeout=90)

    if fire or "com.google.android.apps.tv.launcherx" in present or "com.google.android.tvlauncher" in present:
        sh_ok("pm grant app.couchtv.launcher android.permission.WRITE_SECURE_SETTINGS")
        cur = sh("settings get secure enabled_accessibility_services").strip()
        parts = [p for p in cur.split(":") if p and p != "null" and "FireWatch" not in p]
        rest = ":".join(parts)
        if rest:
            sh(f"settings put secure enabled_accessibility_services {rest}")
            time.sleep(2)
            sh(f"settings put secure enabled_accessibility_services {rest}:{WATCH}")
        else:
            sh("settings delete secure enabled_accessibility_services")
            time.sleep(2)
            sh(f"settings put secure enabled_accessibility_services {WATCH}")
        sh("settings put secure accessibility_enabled 1")
        print("     Home button watch is on (Amazon's home may flash, then couch-tv)")

    print("4/4  Home")
    try:
        tvadb.Tv.keys  # noqa: B018
    except Exception:
        pass
    try:
        tv.shell("input keyevent KEYCODE_HOME")
    except Exception:
        pass
    time.sleep(3)
    front = sh("dumpsys window")
    if "app.couchtv.launcher" in front:
        print("home screen now: couch-tv")
    else:
        print("Press Home on the TV remote. If Amazon's home stays, run this script again.")
    print("If TiviMate is on the TV it stays; hold OK on a dock app to put YouTube, Crave, TiviMate first.")
    print("Done. Undo: python scripts/install-fire-tv.py --undo")
    tv.close()


def undo(ip: str):
    os.environ["TV_IP"] = ip
    import tvadb

    tv = tvadb.Tv(tvadb.connect)

    def sh(cmd, timeout=60):
        return (tv.shell(cmd, timeout_s=timeout) or "").replace("\r", "")

    def sh_ok(cmd):
        try:
            sh(cmd)
        except Exception:
            pass

    sh_ok("pm revoke app.couchtv.launcher android.permission.WRITE_SECURE_SETTINGS")
    cur = sh("settings get secure enabled_accessibility_services").strip()
    parts = [p for p in cur.split(":") if p and "app.couchtv.launcher" not in p]
    if parts:
        sh("settings put secure enabled_accessibility_services " + ":".join(parts))
    else:
        sh("settings delete secure enabled_accessibility_services")
    sh_ok("cmd package set-home-activity com.amazon.tv.launcher/.ui.Home")
    sh_ok("settings put global window_animation_scale 1")
    sh_ok("settings put global transition_animation_scale 1")
    sh_ok("settings put global animator_duration_scale 1")
    print("Home button watch is off. Amazon's home is the Home key again.")
    print("Uninstall Couch TV in Settings, Applications if you want that APK gone.")
    print("TiviMate and other apps are not uninstalled.")
    tv.close()


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    flags = set(sys.argv[1:])
    prep = "--prep" in flags
    do_undo = "--undo" in flags
    ip_arg = args[0] if args else None

    if not do_undo:
        fetch_apks()
    cfg = ensure_config(ip_arg)
    if prep and not do_undo:
        print("Prep done. After ADB debugging is on:")
        print("  python scripts/install-fire-tv.py 192.168.1.110")
        return
    ip = ip_arg or config_ip(cfg)
    if not ip or ip == "SET_YOUR_TV_IP":
        sys.exit("Pass the TV IP: python scripts/install-fire-tv.py 192.168.1.110")
    os.environ["TV_IP"] = ip
    if do_undo:
        undo(ip)
        return
    apply(ip)


if __name__ == "__main__":
    main()
