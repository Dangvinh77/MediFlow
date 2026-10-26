# MediFlow demo 25/10/2026

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
