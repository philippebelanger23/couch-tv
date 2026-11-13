"""Philippe's Fire TV copy: Keep watching off, Amazon launcher left installed."""
import json
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parent.parent
CFG = json.loads((ROOT / "config.fire-tv.example.json").read_text())
INSTALL = (ROOT / "scripts/install-fire-tv.py").read_text()


class FireTvHome(unittest.TestCase):
    def test_keep_watching_off(self):
        self.assertIs(CFG["home"]["keep_watching"], False)
        app = (ROOT / "tv-home/web/app.js").read_text()
        self.assertIn("KW_ON = false;", app)
        self.assertIn("ca.bellmedia.cravetv", (ROOT / "config.fire-tv.example.json").read_text())
        css = (ROOT / "tv-home/web/app.css").read_text()
        self.assertIn("html.kw-off #shelf-section.empty", css)

    def test_lighter_home_for_slow_tcl(self):
        self.assertIs(CFG["home"]["lighter"], True)
        self.assertEqual(CFG["home"]["glass"], "off")
        self.assertIs(CFG["home"]["moving_tiles"], False)

    def test_installer_never_uninstalls_tivimate(self):
        self.assertNotIn("pm uninstall ar.tvplayer.tv", INSTALL)
        self.assertIn("pm uninstall app.couchtv.launcher", INSTALL)
        self.assertIn("keep_watching", INSTALL)

    def test_animation_scale_shortens_flash(self):
        self.assertIn("window_animation_scale 0", INSTALL)
        self.assertNotIn("pm disable-user com.amazon.tv.launcher", INSTALL)
        self.assertNotIn("pm uninstall com.amazon.tv.launcher", INSTALL)


if __name__ == "__main__":
    unittest.main()
