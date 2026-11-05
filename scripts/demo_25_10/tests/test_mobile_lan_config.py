"""Native configuration checks runnable on Windows without Xcode."""
from pathlib import Path
import plistlib
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]


class MobileLanConfigTest(unittest.TestCase):
    def test_http_exception_excludes_release_and_metadata_matches_release(self):
        runner = ROOT / "mobile/ios/Runner"
        release = plistlib.loads((runner / "Info.plist").read_bytes())
        debug = plistlib.loads((runner / "Info-Debug.plist").read_bytes())
        self.assertNotIn("NSAppTransportSecurity", release)
        self.assertTrue(debug.pop("NSAppTransportSecurity")["NSAllowsArbitraryLoads"])
        self.assertTrue(debug.pop("NSLocalNetworkUsageDescription"))
        self.assertEqual(debug, release)
        project = (ROOT / "mobile/ios/Runner.xcodeproj/project.pbxproj").read_text()
        # Demo IPAs build in Profile against the plain-HTTP Gateway; only Release keeps ATS.
        plist_by_configuration = {
            name: plist
            for plist, name in re.findall(
                r"INFOPLIST_FILE = Runner/([\w-]+\.plist);.*?name = (\w+);", project, re.S
            )
        }
        self.assertEqual(plist_by_configuration, {
            "Debug": "Info-Debug.plist",
            "Profile": "Info-Debug.plist",
            "Release": "Info.plist",
        })


    def test_every_demo_build_enables_the_exam_fee_workspace(self):
        # Dart defines are compile-time, so a build path that omits them hides the tab for good.
        for path in ("mobile/tool/run_web.ps1", ".github/workflows/mobile-ios-unsigned.yml",
                     "scripts/demo_25_10/Demo-Launcher.ps1", "scripts/demo_25_10/Run-ClientChecks.ps1",
                     "mobile/README.md"):
            source = (ROOT / path).read_text(encoding="utf-8")
            for flag in ("MEDIFLOW_SCHEDULING_UI_ENABLED", "MEDIFLOW_OUTPATIENT_QUOTES_UI_ENABLED",
                         "MEDIFLOW_ZERO_PAYABLE_UI_ENABLED"):
                self.assertIn(f"--dart-define={flag}=true", source, f"{path} is missing {flag}")


if __name__ == "__main__":
    unittest.main()
