"""Native configuration checks runnable on Windows without Xcode."""
from pathlib import Path
import plistlib
import unittest

ROOT = Path(__file__).resolve().parents[3]


class MobileLanConfigTest(unittest.TestCase):
    def test_http_exception_is_debug_only_and_metadata_matches_release(self):
        runner = ROOT / "mobile/ios/Runner"
        release = plistlib.loads((runner / "Info.plist").read_bytes())
        debug = plistlib.loads((runner / "Info-Debug.plist").read_bytes())
        self.assertNotIn("NSAppTransportSecurity", release)
        self.assertTrue(debug.pop("NSAppTransportSecurity")["NSAllowsArbitraryLoads"])
        self.assertTrue(debug.pop("NSLocalNetworkUsageDescription"))
        self.assertEqual(debug, release)
        project = (ROOT / "mobile/ios/Runner.xcodeproj/project.pbxproj").read_text()
        self.assertEqual(project.count("INFOPLIST_FILE = Runner/Info-Debug.plist;"), 1)
        debug_block = project.split("97C147061CF9000F007C117D /* Debug */ = {")[1].split("name = Debug;")[0]
        self.assertIn("INFOPLIST_FILE = Runner/Info-Debug.plist;", debug_block)
        self.assertEqual(project.count("INFOPLIST_FILE = Runner/Info.plist;"), 2)


if __name__ == "__main__":
    unittest.main()
