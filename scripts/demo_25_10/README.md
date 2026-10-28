# MediFlow demo 25/10/2026

## Khởi động nhanh trên Windows — 28/10/2026

Tại thư mục source chính, mở Docker Desktop và chạy lần lượt:

1. `start-backend.bat`: kiểm tra Docker/Java/Maven/Python, build JAR hiện tại rồi
   chạy snapshot ngoại trú V2 cách ly, chuẩn bị lịch và lượt nguồn tái khám qua API.
   Chờ `READY` và giữ cửa sổ mở; đọc dòng `UI DATA` / `FOLLOW-UP SOURCE READY` hoặc `PARTIAL`.
2. `start-frontend.bat`: cài dependencies theo lockfile và chạy web tại
   `http://127.0.0.1:3000`, nối Gateway `http://127.0.0.1:28080`, bật các UI gates demo.
3. `clean-docker.bat`: yêu cầu demo backend dừng qua stop file, xóa container MediFlow
   và các image/tag dự án nhận diện được; image còn được container dự án khác dùng
   được giữ lại. Không xóa volume, không prune toàn máy. Dừng frontend bằng `Ctrl+C`.

Chạy `clean-docker.bat -Preview` để xem trước mà không thay đổi Docker hoặc tạo stop file.
Các file dùng đường dẫn tương đối từ chính file `.bat`, không cần đặt working directory.
Lỗi trả exit code 1 và giữ cửa sổ để đọc. Có thể đặt `MEDIFLOW_NO_PAUSE=1` khi tự động hóa.

Yêu cầu Java 21, Maven, Python 3, Node/pnpm và Docker Compose v2 trên PATH.
Backend dùng lại runner hiện có: dữ liệu demo là tạm thời, tự dừng/dọn sau hai giờ;
lần chạy mới nạp lại snapshot. Launcher bổ sung lịch hôm nay/ngày mai bằng
`prepare_ui_demo.py`, không sửa snapshot SQL. Ngoài giờ chỉ chuẩn bị lịch chưa kết thúc;
lượt nguồn tái khám cần chạy trong 07:00–16:45 giờ Việt Nam. Chạy demo trọn luồng
trong 07:00–17:00, nên bắt đầu trước 16:00.

**Hướng dẫn click từng bước, đủ ba trường hợp và đến nhận thuốc:**
[DEMO_UI_TUNG_BUOC.md](DEMO_UI_TUNG_BUOC.md). Đọc `DEMO-DATA.local.md` tại đường dẫn
`UI DATA` của lần chạy để biết đúng tên khoa, CCCD bệnh nhân và ngày có lịch.

Nếu đã có stack `--scheduling --keep` đang chạy, chuẩn bị dữ liệu bằng:

```powershell
python scripts/demo_25_10/prepare_ui_demo.py
```

Lượt nền của `demo.patient2` có khoản thu thật trong DB demo để làm nguồn tái khám;
ba lượt bạn sẽ thao tác không được đăng ký trước. Không xóa journal khi gặp timeout.

Kiểm tra an toàn file dọn (Docker giả lập, không xóa tài nguyên thật):

```powershell
powershell -NoProfile -File scripts/demo_25_10/tests/Test-DemoLauncher.ps1
python -m unittest discover -s scripts/demo_25_10/tests -p "test_prepare_ui_demo.py"
```

**Luồng mới có lịch khám/giữ suất/tái khám/quote:** dùng
[runbook ngoại trú V2 ngày 26/10](MediFlow_Ngoai_Tru_V2_Test_2026-10-26.md).
Runbook này có overlay opt-in, cách chạy JAR cách ly, bước Postman/UI theo role và giới hạn nghiệm thu.

**Đọc trước khi bảo vệ:** [nghiệp vụ hiện hành 25/10](../../docs/plans/02-business-flows.md)
đã chốt lịch khám, tái khám và BHYT mô phỏng. Các runbook ở đây vẫn là baseline runtime;
chưa chứng minh các yêu cầu mới đã có. Xem [gap và tiêu chí chuyển demo](../../docs/plans/07-outpatient-workflow-implementation.md).

Use the source commit recorded in the selected guide and rebuild matching binaries; the old
`codex/fix-lab-clearance-identity` branch name is historical, not a required checkout. The SQL snapshot
was generated at baseline `64a1f043`; it contains explicitly audited historical
`EMERGENCY_DEMO` cases. Keep their audit intact. A fresh normal paid flow is verified
separately by the packaged acceptance runner.

## Verification and rehearsal

Run from the repository root with Java 21, Maven, Docker, Python 3, pnpm and Flutter
on PATH. Docker must be running. These runners stop at a failed step and retain logs.

```powershell
# Real fresh Clinical -> Lab -> Billing payment -> Lab result -> Clinical attachment
./scripts/demo_25_10/Run-LabNormalAcceptance.ps1

# Build the default six business deployments and Gateway/Eureka
mvn -q -pl backend/core-service,backend/clinical-service,backend/lab-service,backend/pharmacy-service,backend/billing-service,backend/support-service,backend/gateway,backend/eureka-server -am -DskipTests package

# Restore all eight databases into NEW disposable PostgreSQL, then smoke six deployments
python scripts/demo_25_10/rehearse_snapshot.py --keep
```

The second runner uses ports 28080 (Gateway), 28761 (Eureka), 28089/28082/28084/28085/
28086/28087 (Core/Clinical/Lab/Pharmacy/Billing/Support), plus random loopback-only
database/broker ports. It checks that application ports are free first. No existing
business database or volume is used. `evidence/active-rehearsal.local.json` supplies
the current ports and stop-file path. Create that stop file to stop only the
runner's own JVMs and containers; it also stops after two hours. Do not use global
Docker cleanup commands.

The local rehearsal sets a 15-second Organization authentication timeout while the
demo laptop runs multiple JVMs. The repository's production timeout is unchanged.

```powershell
./scripts/demo_25_10/Run-ClientChecks.ps1 -Gateway http://127.0.0.1:28080
./scripts/demo_25_10/Smoke-Gateway.ps1 -Gateway http://127.0.0.1:28080
```

Node 24 LTS is the verified frontend runtime. The runner handles the Node 25+
native Web Storage/jsdom conflict and bounds test workers for a machine also
running six JVMs. A 15-second test timeout allows loaded-machine scheduling;
assertions are unchanged. Web typecheck/lint/tests/production build and Flutter
format/analyzer/tests/web build are distinct from browser and native acceptance.
Run the packaged Lab acceptance separately from the full topology/Billing suite on
this laptop: a concurrent rehearsal hit an Organization upstream deadline.

The Gateway smoke logs in through the real synthetic ADMIN account and reads all
six deployments. Report uses the snapshot's accepted closed day `2025-06-15` and
department `9a6dd24a-1c1f-4122-9e31-7cfea345ec45`. An uncovered current period returns
`404 REPORT_NOT_FOUND`; do not fabricate coverage or change SQL to make it appear.

After the web build, launch Next with the same UI gates:

```powershell
$env:MEDIFLOW_OUTPATIENT_UI_ENABLED='true'
$env:MEDIFLOW_PHARMACY_PRESCRIPTION_MODE='outpatient'
$env:MEDIFLOW_REPORT_OUTPATIENT_UI_ENABLED='true'
cd frontend
pnpm start --hostname 127.0.0.1 --port 3000
```

The Gateway rewrite was captured at build time by `Run-ClientChecks.ps1`. Rebuild if
the Gateway changes. `next start` reports the repository's standalone-output
recommendation; the existing detailed guide also describes standalone asset copy
and startup. Do not run both servers on port 3000.

For the already-built Flutter Web client:

```powershell
cd mobile
dart run tool/dev_server.dart 4300 http://127.0.0.1:28080
```

Open `http://localhost:3000` and `http://localhost:4300`. Use the synthetic demo
accounts documented in the detailed guide; credentials are not populated by the
clients. Native Android/iOS/Windows have separate toolchain requirements in
`mobile/README.md`. Do not equate Flutter Web with native device acceptance.

## Evidence and scenario guide

### Nurse onboarding rehearsal

After building the candidate JARs, start the disposable scheduling stack with
`python scripts/demo_25_10/rehearse_snapshot.py --scheduling --keep`. In a second
terminal, before 17:00 Asia/Ho_Chi_Minh, run:

```powershell
python scripts/demo_25_10/accept_scheduling.py --onboarding --output scripts/demo_25_10/evidence/onboarding.local.json
```

The runner checks that the Gateway matches `active-rehearsal.local.json`. It creates
a synthetic patient/account through NURSE commands, completes paid EXAM/Lab and
follow-up advice before the first patient login, then tests password/session gates,
own results, in-app history, linked revisit and existing ADMIN password recovery.
Credentials stay in memory; evidence records statuses, error codes and operation IDs.
It does not prove real-world identity verification or all UI failure/recovery cases.

Additional ONB-06 variants (each still requires `--onboarding`):

- `--issuance-recovery`: two distinct NURSE actors race to issue one account; a loopback
  proxy drops a committed Gateway response and the client retries the same command.
- `--login-before-lab`: first login/password change during the open visit; pending results
  are hidden and completed Lab is readable before exam completion, keeping the EXAM receipt.
- `--login-after-return`: second AT_COUNTER NEW_VISIT after the first completed visit,
  before the first patient login. Synthetic same-day return; initial password change still applies.
- `--follow-up-session-id <UUID>`: reuse a tomorrow session when repeating onboarding
  alongside `--session-id <today UUID>`; both must belong to this disposable rehearsal.

Choose at most one of the two login timing variants. Run early-login separately from
late-login to retain evidence that web use is optional throughout the clinical journey.
See [107 + 99 Gateway observations and limits](../../docs/acceptance/onboarding-recovery-2026-10-27/README.md).

Use `--session-id <UUID>` only to reuse a session from this disposable stack while
repeating with a new synthetic patient; a fresh stack avoids overlapping demo sessions.
Do not use the runner against a project/deployed database. Stop the stack with the
`stopFile` in its manifest so its runner removes only its own processes/containers.

### Historical and current evidence

- [Complete changed-file list](CHANGED-FILES.txt): production, regression, demo and proof files in this PR.
- [Follow-up verification](FOLLOWUP-VERIFICATION.md): Billing listener, SQL restore,
  topology/client results and remaining limits.
- [Lab verification](LAB-NORMAL-VERIFICATION.md): original ID regression, timestamp
  precision regression and the first fresh normal paid runtime proof.
- [Detailed four-developer guide](MediFlow_Demo_4_Dev_Chi_Tiet_2026-10-25.md): roles,
  presenter steps, gates, routes, errors and historical scenario IDs.
- [SQL snapshot](MediFlow_Demo_Database_2026-10-25.sql): import with `psql`, empty
  isolated targets only. Do not reset delivery rows or edit clearance/state IDs.

Topology health and snapshot restore smoke do not certify every refund, Pharmacy
dispense or Report coverage workflow. The normal paid Lab acceptance specifically
exercises Core/Clinical/Lab/Billing with real PostgreSQL/RabbitMQ.

### Result replay and browser recovery acceptance — 27/10

On the disposable scheduling stack, before 17:00 Asia/Ho_Chi_Minh:

```powershell
# Optional broker tool dependency; use an isolated environment
python -m venv scripts/demo_25_10/evidence/replay-tools.local
scripts/demo_25_10/evidence/replay-tools.local/Scripts/python.exe -m pip install pika==1.3.2
scripts/demo_25_10/evidence/replay-tools.local/Scripts/python.exe scripts/demo_25_10/accept_scheduling.py --onboarding --result-event-replay --output scripts/demo_25_10/evidence/result-replay.local.json
```

The observer captures real producer bytes/properties, republishes each fact three times,
checks active Notification queue drain/DLQ and unchanged patient notification IDs before
the initial patient login. Early-login mode is incompatible. Evidence records source SHA
and runner/JAR hashes. Never replay against a project broker.

For browser-only response-loss rehearsal, start `gateway_fault_proxy.py --drop-next-issuance-response --stop-with-rehearsal` against the active manifest, then
point the local Next server at `GATEWAY_URL=http://127.0.0.1:28081`. It drops one successful
issuance response after upstream commit and writes sanitized metadata inside that rehearsal
evidence folder. Preserve the browser intent and patient; reload and reconcile the same
request. Stop the proxy/Next server after testing; do not change deployment defaults.

[Evidence and limits](../../docs/acceptance/demo-readiness-2026-10-27/README.md),
[three-developer manual runbook](../../docs/plans/16-three-developer-demo-acceptance-2026-10-27.md).
