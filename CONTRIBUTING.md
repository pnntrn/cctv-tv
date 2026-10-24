# Contributing / การมีส่วนร่วม

Start with a focused issue or pull request describing the problem and expected behavior. For a larger change or a new production dependency, discuss it with the maintainer first. Keep changes small, preserve strict typing and explicit errors, and put fakes and mocks in tests only.

รายงานปัญหาพร้อมเวอร์ชันแอป รุ่นทีวี และขั้นตอนทำซ้ำ ห้ามแนบรหัสผ่าน URL ที่มีข้อมูลเข้าสู่ระบบ ภาพจากกล้องจริง หรือบันทึกส่วนตัวที่ยังไม่ลบข้อมูลสำคัญ ช่องทางรายงานช่องโหว่อยู่ใน [SECURITY.md](SECURITY.md)

## Build and check

Use Java 17, Android SDK platform 35 and build-tools 35.0.0. Set `ANDROID_HOME` or the SDK path in a local, uncommitted `local.properties`. The committed wrapper pins Gradle 8.11.1; do not substitute a system Gradle version.

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest
bash scripts/check-core.sh
bash scripts/check-digest.sh
bash scripts/check-updates.sh
python3 -m unittest discover -s scripts/tests -v
git diff --check
```

On Windows use `.\gradlew.bat` and `python` if those are the installed command names. The shell scripts need Bash and a Java toolchain. For native TV checks, use an Android 9 / API 28 x86_64 emulator with a physical 1920×1080 TV display. See [the CI workflow](.github/workflows/android.yml) and [native harness](scripts/ui-smoke.sh) for APK locations and emulator setup; run `bash scripts/ui-smoke.sh` with the emulator connected. Inspect its screenshots as well as the test results.

Add meaningful regression tests for changed behavior. Exercise remote focus, paging, fullscreen/back, technician entry, background release and recorder drafts when those paths change. Use synthetic camera fixtures; do not add production credentials, private footage, signing files or raw device logs. Separate emulator evidence from real-device evidence in the PR.

Without retained signing configuration, a release build is unsigned and cannot be installed directly. Debug builds use a different signing identity. Do not uninstall a user's installation to make a test build fit: that removes local profiles. See [releasing](docs/RELEASING.md) for the supported upgrade and signing path.

## Review and licensing

State what changed, how it was checked and what remains unverified. Follow the [code of conduct](CODE_OF_CONDUCT.md). Report vulnerabilities privately through [GitHub security advisories](https://github.com/pnntrn/cctv-tv/security/advisories/new).

The original project code currently has no project license. Do not assume that public visibility grants permission to reuse or distribute it. The owner is deciding the project license; preserve all existing third-party notices in the meantime. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
