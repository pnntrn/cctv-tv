import importlib.util
from pathlib import Path
import sys
import unittest

SPEC = importlib.util.spec_from_file_location("prepare_release", Path(__file__).parents[1] / "prepare-release.py")
release = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = release
SPEC.loader.exec_module(release)


class ReleaseValidationTest(unittest.TestCase):
    BADGING = "package: name='com.pnntrn.cctv' versionCode='1009' versionName='0.4.1' platformBuildVersionName='15'\nsdkVersion:'26'\n"

    def test_parse_real_aapt_package_metadata(self):
        info = release.parse_badging(self.BADGING)
        self.assertEqual((info.package, info.version_code, info.version_name, info.min_sdk),
                         ("com.pnntrn.cctv", 1009, "0.4.1", 26))

    def test_wrong_package_is_rejected(self):
        with self.assertRaisesRegex(release.ReleaseError, "package"):
            release.parse_badging(self.BADGING.replace("com.pnntrn.cctv", "com.example.other"))

    def test_missing_minimum_android_is_rejected(self):
        with self.assertRaisesRegex(release.ReleaseError, "metadata"):
            release.parse_badging(self.BADGING.splitlines()[0])

    def test_changed_android_floor_is_rejected(self):
        with self.assertRaisesRegex(release.ReleaseError, "Android"):
            release.parse_badging(self.BADGING.replace("sdkVersion:'26'", "sdkVersion:'28'"))

    def test_invalid_version_metadata_is_rejected(self):
        for before, after in [("1009", "0"), ("0.4.1", "0.4.1/dev")]:
            with self.subTest(after=after), self.assertRaises(release.ReleaseError):
                release.parse_badging(self.BADGING.replace(before, after))

    def test_apk_matches_source_version(self):
        release.validate_source_version(release.parse_badging(self.BADGING),
                                        "    versionCode 1009\n    versionName '0.4.1'\n")

    def test_old_code_or_wrong_name_is_rejected(self):
        for source in ["versionCode 1010\nversionName '0.4.1'", "versionCode 1009\nversionName '0.4.2'", ""]:
            with self.subTest(source=source), self.assertRaises(release.ReleaseError):
                release.validate_source_version(release.parse_badging(self.BADGING), source)

    def test_stable_tag_matches_apk_version(self):
        self.assertFalse(release.validate_tag("v0.4.1", "0.4.1"))

    def test_candidate_tag_is_identified(self):
        self.assertTrue(release.validate_tag("v0.4.1-rc.1", "0.4.1"))

    def test_wrong_or_unsafe_tags_are_rejected(self):
        for tag in ["v0.4.0", "v0.4.1-rc.0", "v0.4.1;echo bad", "0.4.1", "v0.4.10"]:
            with self.subTest(tag=tag), self.assertRaises(release.ReleaseError):
                release.validate_tag(tag, "0.4.1")

    def test_retained_certificate_is_accepted(self):
        release.validate_certificate("Signer #1 certificate SHA-256 digest: " + release.EXPECTED_CERT.upper())

    def test_wrong_or_additional_signer_is_rejected(self):
        for report in ["", "Signer #1 certificate SHA-256 digest: " + "0" * 64,
                       "Signer #1 certificate SHA-256 digest: " + release.EXPECTED_CERT +
                       "\nSigner #2 certificate SHA-256 digest: " + release.EXPECTED_CERT]:
            with self.subTest(report=report), self.assertRaises(release.ReleaseError):
                release.validate_certificate(report)


if __name__ == "__main__":
    unittest.main()
