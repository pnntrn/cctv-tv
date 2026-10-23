# Security / ความปลอดภัย

Report suspected vulnerabilities privately using [GitHub private vulnerability reporting](https://github.com/pnntrn/cctv-tv/security/advisories/new). Do not disclose exploit details in public issues or pull requests. Include the affected version, impact, minimal reproduction and sanitized evidence. No response-time or supported-version commitment is currently published.

หากพบช่องโหว่ ให้ใช้ช่องทางส่วนตัวด้านบน ไม่โพสต์รหัสผ่าน URL กล้องที่มีข้อมูลเข้าสู่ระบบ ภาพจากกล้องจริง คีย์ลงนาม หรือ log ส่วนตัวใน issue สาธารณะ ระบุเวอร์ชัน ผลกระทบ และขั้นตอนทำซ้ำที่ลบข้อมูลสำคัญแล้ว

## Privacy and deployment boundaries

- Saved profiles are encrypted locally using Android Keystore AES-GCM; backups are disabled. Clearing data or uninstalling deletes profiles. Non-secret recorder draft fields are stored separately; password drafts are never retained.
- RTSP is normally unencrypted. Use cameras only on a trusted LAN or VPN. The app connects directly and has no cloud account or analytics.
- Phone setup opens a temporary local HTTP service. Credentials traverse that local connection, so use only trusted networks. Pairing expires after ten minutes, closes on dialog dismissal/backgrounding and permits one paired phone. It is not an Internet-facing administration service.
- Technician entry requires holding Menu for three seconds and confirming. This protects the normal viewing flow from accidental edits; it is not an authentication boundary against someone with physical remote access.
- Raw exceptions and credential-bearing URLs are not intentionally shown or logged by the app. Android/media-library diagnostics can still contain sensitive information; review and redact them before sharing.
- Install APKs only from the project's release channel and check the published hashes and retained signing certificate as described in [RELEASING.md](docs/RELEASING.md).

A candidate release and passing synthetic tests do not establish compatibility with every recorder or physical TV. See the [README](README.md) for current limitations.
