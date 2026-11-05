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


if __name__ == "__main__":
    unittest.main()
