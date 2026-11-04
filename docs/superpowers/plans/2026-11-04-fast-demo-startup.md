# Fast demo startup

Approved scope: reuse current JARs safely, bounded parallel application startup,
retain isolated demo infrastructure/data, and stop applications without deleting it.

1. Add regression tests for build fingerprints, persistent infrastructure ownership,
   incomplete initialization, startup grouping and repeated startup journal reuse.
2. Add a local build cache helper: hash Maven inputs and all eight packaged JARs.
   Unknown/stale artifacts require a build; write the cache only after success.
3. Extend the Python runner with opt-in persistent infrastructure. Store identities
   and journals under its evidence directory. Verify Docker labels/container IDs
   before reuse or reset. Never attach to an arbitrary existing database/broker.
   Keep legacy disposable execution as the default Python runner behavior.
4. Start Eureka/Core first, business services in bounded groups, Gateway last.
   Preserve individual health checks and cleanup of every launched process on failure.
5. Wire Windows -Fast, -Rebuild, -ResetData and stop-backend.bat. Retain the existing
   default clean demo path; Fast/Rebuild/ResetData use the persistent workspace.
6. Reuse the same journals on restart. Refresh current UI fixtures, reuse/verify
   the calendar instead of replaying its writes. Add phase timings and document commands.
7. Run Python demo tests, mocked PowerShell launcher/LAN tests, and diff checks.
   Do not reset or stop the user's active demo for validation.

Acceptance: stale/missing JARs build automatically; warm starts restore no SQL;
stop retains data/images; reset targets only verified owned persistent containers;
foreign/partial state fails safely; process concurrency never exceeds its bound.

Implemented files: `demo_build.py` (content-based artifact validation),
`demo_runtime.py` (owned infrastructure and OS-held runner lock), the existing
runner/launcher, cancellation support in UI preparation, and `stop-backend.bat`.

Validation: 52 Python tests, 14 mocked cleanup scenarios, 9 mocked build/stop
scenarios, LAN/firewall checks and `git diff --check` passed. Coverage includes
warm/cold/disposable orchestration, process cleanup after failed launch, journal
reuse, calendar read verification, interrupted health checks, missing containers,
ownership refusal, source changes during build and lock release. No active demo
was stopped/reset, and no real full-stack startup timing was claimed.

Independent review identified two lifecycle gaps, both fixed with regressions:
publish runner identity before first infrastructure restore so Stop can cancel it,
and retry live RabbitMQ diagnostics instead of trusting a retained container's old
startup logs. Persistent Stop awaits runner exit as well as JVM termination.
