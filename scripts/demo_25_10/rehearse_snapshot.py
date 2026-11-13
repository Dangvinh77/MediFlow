"""Restore the demo into disposable infrastructure and smoke six packaged deployments.

Usage: python scripts/demo_25_10/rehearse_snapshot.py [--keep] [--persistent] [--reset-data]
With --keep, the stack stays up for UI rehearsal until its evidence/stop file exists.
Requires Docker Compose, Python 3 and the eight already-built application JARs.
Only --persistent reuses this checkout's verified isolated rehearsal infrastructure.
"""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import socket
import subprocess
import time
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
NAMES = ["eureka-server", "core-service", "clinical-service", "lab-service",
         "pharmacy-service", "billing-service", "support-service", "gateway"]
PORTS = dict(zip(NAMES, [28761, 28089, 28082, 28084, 28085, 28086, 28087, 28080]))
BASE_PORTS = PORTS.copy()
DATABASES = ["organization", "patient", "clinical", "lab", "pharmacy", "billing", "notification", "report"]


def command(*args, log=None, check=True):
    result = subprocess.run(args, cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if log:
        log.write_text(result.stdout + result.stderr, encoding="utf-8")
    if check and result.returncode:
        raise RuntimeError(f"{args[0]} {args[1]} failed ({result.returncode}); see {log}")
    return result


def get_json(url):
    with urllib.request.urlopen(url, timeout=5) as response:
        return json.load(response)


def await_ready(probe, timeout=180):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            if probe():
                return
        except InterruptedError:
            raise
        except (OSError, ValueError):
            pass
        time.sleep(2)
    raise RuntimeError("Readiness deadline exceeded")


def parse_options(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--keep", action="store_true")
    parser.add_argument("--scheduling", action="store_true",
                        help="Enable the opt-in outpatient revision 2 rehearsal overlay")
    parser.add_argument("--prepare-ui-demo", action="store_true",
                        help="Prepare current schedules and a completed follow-up source through Gateway")
    parser.add_argument("--lan", action="store_true",
                        help="Expose only the disposable Gateway on the local network")
    parser.add_argument("--persistent", action="store_true",
                        help="Retain this checkout's isolated infrastructure and reuse its preparation journals")
    parser.add_argument("--reset-data", action="store_true",
                        help="Replace only the verified persistent demo infrastructure")
    parser.add_argument("--startup-parallelism", type=int, choices=(1, 2, 3), default=2)
    parser.add_argument("--prepare-report-demo", action="store_true",
                        help="Seed and accept the closed synthetic Report fixture through ADMIN Gateway APIs")
    parser.add_argument("--port-offset", type=int, default=0,
                        help="Add a positive offset to all local rehearsal ports (for parallel disposable stacks)")
    options = parser.parse_args(argv)
    if options.reset_data and not options.persistent:
        parser.error("--reset-data requires --persistent")
    if options.prepare_ui_demo and not options.scheduling:
        parser.error("--prepare-ui-demo requires --scheduling")
    if options.prepare_report_demo and not options.scheduling:
        parser.error("--prepare-report-demo requires --scheduling")
    if options.port_offset < 0 or max(BASE_PORTS.values()) + options.port_offset > 65535:
        parser.error("--port-offset must keep every local port in 1..65535")
    if options.port_offset and options.prepare_ui_demo:
        parser.error("--port-offset cannot be combined with UI preparation, whose fixture scripts require port 28080")
    return options


def server_bind_address(name, lan=False):
    return "0.0.0.0" if name == "gateway" and lan else "127.0.0.1"


def snapshot_environment_value(value, pgport, eureka_port=28761):
    # The immutable snapshot uses mediflow_pharmacy; Compose's Docker store has
    # a different name. Both the endpoint and this name belong to this runner.
    return (str(value).replace("postgres:5432", "127.0.0.1:" + pgport)
            .replace("/mediflow_pharmacy_docker", "/mediflow_pharmacy")
            .replace("http://eureka-server:8761", "http://127.0.0.1:" + str(eureka_port)))


def check_stop(stop_file):
    if stop_file and stop_file.exists():
        raise InterruptedError('Demo stopped by request')


def await_broker(rabbit, stop_file=None):
    def broker_ready():
        check_stop(stop_file)
        state = command("docker", "inspect", rabbit, "--format", "{{.State.Status}}").stdout.strip()
        if state == "exited":
            raise RuntimeError("Rehearsal broker exited; inspect rabbit.log")
        boot_log = command("docker", "logs", rabbit)
        if "Server startup complete" not in boot_log.stdout + boot_log.stderr:
            return False
        # Retained containers keep logs from previous boots. Only a live ping is readiness.
        return command("docker", "exec", "--user", "rabbitmq", rabbit,
                       "rabbitmq-diagnostics", "-q", "ping", check=False).returncode == 0
    # Do not run Erlang CLI as root during boot: it can create a root-owned
    # .erlang.cookie before the broker user, causing an eacces startup failure.
    await_ready(broker_ready)


def initialize_infrastructure(options, evidence, containers, pg, rabbit, workspace):
    def checked_command(*args, **kwargs):
        check_stop(evidence / 'stop')
        return command(*args, **kwargs)
    labels = workspace.docker_labels if workspace else []
    pg_id = checked_command("docker", "run", "-d", "--name", pg, *labels, "-e", "POSTGRES_PASSWORD=postgres",
            "-p", "127.0.0.1::5432", "postgres:16-alpine", log=evidence / "postgres-container.log")
    containers.append(pg)
    if workspace:
        workspace.track("pg", pg, pg_id.stdout.strip())
    pgport = checked_command("docker", "port", pg, "5432/tcp").stdout.strip().rsplit(":", 1)[1]
    await_ready(lambda: checked_command("docker", "exec", pg, "pg_isready", "-U", "postgres", check=False).returncode == 0)
    checked_command("docker", "cp", str(HERE / "MediFlow_Demo_Database_2026-10-25.sql"), pg + ":/tmp/demo.sql")
    checked_command("docker", "exec", pg, "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", "postgres",
            "-f", "/tmp/demo.sql", log=evidence / "restore.log")
    # Reimport must refuse before loading any data into a populated target.
    refused = checked_command("docker", "exec", pg, "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "postgres",
                      "-d", "postgres", "-f", "/tmp/demo.sql", log=evidence / "restore-refusal.log", check=False)
    if refused.returncode == 0 or "demo restore refused" not in (refused.stdout + refused.stderr).lower():
        raise RuntimeError("The populated-target restore guard was not confirmed")
    checked_command("docker", "cp", str(HERE / "seed_report_demo.sql"), pg + ":/tmp/report-demo.sql")
    checked_command("docker", "exec", pg, "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "postgres",
            "-d", "mediflow_report", "-f", "/tmp/report-demo.sql", log=evidence / "report-demo-seed.log")
    if options.scheduling:
        checked_command("docker", "cp", str(HERE / "seed_outpatient_scheduling.sql"), pg + ":/tmp/scheduling.sql")
        checked_command("docker", "exec", pg, "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "postgres",
                "-d", "mediflow_organization", "-f", "/tmp/scheduling.sql", log=evidence / "scheduling-seed.log")
        checked_command("docker", "cp", str(HERE / "seed_retire_stale_charges.sql"), pg + ":/tmp/retire-stale-charges.sql")
        checked_command("docker", "exec", pg, "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "postgres",
                "-d", "postgres", "-f", "/tmp/retire-stale-charges.sql", log=evidence / "retire-stale-charges.log")
    counts = {}
    for context in DATABASES:
        query = "SELECT count(*) FROM pg_tables WHERE schemaname='public'"
        value = checked_command("docker", "exec", pg, "psql", "-XAt", "-U", "postgres", "-d", "mediflow_" + context,
                        "-c", query).stdout.strip()
        counts[context] = {"publicTables": int(value)}
        if not int(value):
            raise RuntimeError("Empty restored context: " + context)
    (evidence / "restored-contexts.json").write_text(json.dumps(counts, indent=2), encoding="utf-8")
    rabbit_id = checked_command("docker", "run", "-d", "--name", rabbit, *labels, "--hostname", "rehearsal-rabbit",
            "-e", "RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS=+S 2:2", "-e", "RABBITMQ_DEFAULT_USER=rehearsal",
            "-e", "RABBITMQ_DEFAULT_PASS=rehearsal-local", "-p", "127.0.0.1::5672", "rabbitmq:3.13-alpine")
    containers.append(rabbit)
    if workspace:
        workspace.track("rabbit", rabbit, rabbit_id.stdout.strip())
    rabbitport = checked_command("docker", "port", rabbit, "5672/tcp").stdout.strip().rsplit(":", 1)[1]
    await_broker(rabbit, evidence / "stop")
    if workspace:
        workspace.complete()
    return pgport, rabbitport


def start_application_batches(launch, wait, parallelism=2):
    business = NAMES[2:-1]
    groups = [[NAMES[0]], [NAMES[1]]]
    groups.extend(business[index:index + parallelism] for index in range(0, len(business), parallelism))
    groups.append([NAMES[-1]])
    for group in groups:
        pending = [(name, launch(name)) for name in group]
        for name, process in pending:
            wait(name, process)


def main():
    options = parse_options()
    PORTS.update({name: port + options.port_offset for name, port in BASE_PORTS.items()})
    stamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    evidence = HERE / "evidence" / ("workspace" if options.persistent else "snapshot-" + stamp)
    evidence.mkdir(parents=True, exist_ok=options.persistent)
    def run_command(*args, **kwargs):
        check_stop(evidence / 'stop')
        return command(*args, **kwargs)
    workspace = None
    lock = None
    if options.persistent:
        from demo_runtime import Workspace, WorkspaceLock
        workspace = Workspace(ROOT, evidence, run_command, options.scheduling, options.prepare_report_demo)
        lock = WorkspaceLock(evidence / 'runner.lock')
        lock.acquire()
    containers, applications, streams = [], [], []
    report_evidence = None
    prefix = "mediflow-rehearsal-" + stamp + "-" + uuid.uuid4().hex[:8]
    pg, rabbit = prefix + "-pg", prefix + "-rabbit"
    try:
        for name, port in PORTS.items():
            with socket.socket() as candidate:
                candidate.bind((server_bind_address(name, options.lan), port))
        (evidence / 'stop').unlink(missing_ok=True)
        manifest = {"persistent": options.persistent, "runnerPid": os.getpid(), "evidence": str(evidence),
                    "gateway": f"http://127.0.0.1:{PORTS['gateway']}", "ports": PORTS,
                    "lan": options.lan, "gatewayBindAddress": server_bind_address("gateway", options.lan),
                    "scheduling": options.scheduling, "reportDemo": options.prepare_report_demo,
                    "containers": containers, "pids": [],
                    "stopFile": str(evidence / "stop")}
        manifest_path = HERE / "evidence" / "active-rehearsal.local.json"
        def save_manifest():
            temporary = manifest_path.with_suffix('.tmp')
            temporary.write_text(json.dumps(manifest, indent=2), encoding='utf-8')
            temporary.replace(manifest_path)
        save_manifest()
        compose_args = ["docker", "compose", "-f", "docker-compose.yml"]
        if options.scheduling:
            compose_args.extend(["-f", "compose.demo.local.yml", "-f", "compose.outpatient.local.yml"])
        compose = json.loads(run_command(*compose_args, "config", "--format", "json").stdout)["services"]
        for name in NAMES:
            jar = ROOT / "backend" / name / "target" / (name + "-0.0.1-SNAPSHOT.jar")
            if not jar.is_file():
                raise RuntimeError("Build missing JAR: " + str(jar))
        phase_started = time.monotonic()
        if workspace:
            reused = workspace.open(reset=options.reset_data)
        else:
            reused = None
        if reused:
            pg, rabbit = reused["pg"], reused["rabbit"]
            containers.extend([pg, rabbit])
            pgport = run_command("docker", "port", pg, "5432/tcp").stdout.strip().rsplit(":", 1)[1]
            rabbitport = run_command("docker", "port", rabbit, "5672/tcp").stdout.strip().rsplit(":", 1)[1]
            await_ready(lambda: run_command("docker", "exec", pg, "pg_isready", "-U", "postgres", check=False).returncode == 0)
            await_broker(rabbit, evidence / "stop")
            print("Reusing isolated database/broker; snapshot restore skipped.", flush=True)
        else:
            if workspace:
                workspace.begin()
            pgport, rabbitport = initialize_infrastructure(options, evidence, containers, pg, rabbit, workspace)
        check_stop(evidence / "stop")
        save_manifest()
        print(f"TIMING infrastructure: {time.monotonic() - phase_started:.1f}s", flush=True)
        health = {}
        phase_started = time.monotonic()
        app_processes = {}
        def launch(name, report_acceptance=None):
            if (evidence / 'stop').exists():
                raise InterruptedError('Demo startup stopped by request')
            env = os.environ.copy()
            for key, value in compose[name].get("environment", {}).items():
                if value is not None:
                    env[key] = snapshot_environment_value(value, pgport, PORTS["eureka-server"])
            env.update({"SERVER_PORT": str(PORTS[name]), "SPRING_PROFILES_ACTIVE": "outpatient-mvp",
                        "SERVER_ADDRESS": server_bind_address(name, options.lan),
                        "EUREKA_DEFAULT_ZONE": f"http://127.0.0.1:{PORTS['eureka-server']}/eureka/",
                        "EUREKA_CLIENT_SERVICEURL_DEFAULTZONE": f"http://127.0.0.1:{PORTS['eureka-server']}/eureka/",
                        "EUREKA_INSTANCE_PREFER_IP_ADDRESS": "true", "EUREKA_INSTANCE_IP_ADDRESS": "127.0.0.1",
                        "EUREKA_INSTANCE_HOSTNAME": "127.0.0.1", "SPRING_RABBITMQ_HOST": "127.0.0.1",
                        "SPRING_RABBITMQ_PORT": rabbitport, "SPRING_RABBITMQ_USERNAME": "rehearsal",
                        "SPRING_RABBITMQ_PASSWORD": "rehearsal-local", "MEDIFLOW_RABBIT_HOST": "127.0.0.1",
                        "MEDIFLOW_RABBIT_PORT": rabbitport, "MEDIFLOW_RABBIT_USER": "rehearsal",
                        "MEDIFLOW_RABBIT_PASSWORD": "rehearsal-local", "MEDIFLOW_DEMO_SEED_ENABLED": "false"})
            if report_acceptance is None:
                report_acceptance = options.prepare_report_demo
            if name == "support-service":
                env["MEDIFLOW_REPORT_OUTPATIENT_COVERAGE_ACCEPTANCE_ENABLED"] = "true" if report_acceptance else "false"
            gates = {
                "clinical-service": ["MEDIFLOW_CARE_FINANCE_V2_ENABLED", "MEDIFLOW_CLINICAL_PRESCRIPTION_CONTEXT_LOOKUP_ENABLED"],
                "lab-service": ["MEDIFLOW_CARE_FINANCE_V2", "MEDIFLOW_LAB_CLINICAL_CONTEXT_ENABLED"],
                "pharmacy-service": ["MEDIFLOW_CARE_FINANCE_V2", "MEDIFLOW_PHARMACY_IDENTITY_ENABLED",
                    "MEDIFLOW_PHARMACY_CLINICAL_CONTEXT_ENABLED", "MEDIFLOW_PHARMACY_CLEARANCE_CONSUMER_ENABLED",
                    "MEDIFLOW_PHARMACY_CLEARANCE_AUTHORITY_ENABLED", "MEDIFLOW_PHARMACY_OUTPATIENT_API_ENABLED",
                    "MEDIFLOW_PHARMACY_OUTPATIENT_EVENTS_ENABLED"],
                "billing-service": ["MEDIFLOW_BILLING_CLEARANCE_LOOKUP_ENABLED", "MEDIFLOW_BILLING_LEDGER_ENABLED",
                    "MEDIFLOW_BILLING_REFUNDS_ENABLED", "MEDIFLOW_BILLING_EXAM_CHARGE_CONSUMER_ENABLED",
                    "MEDIFLOW_BILLING_LAB_TEST_CHARGE_CONSUMER_ENABLED", "MEDIFLOW_BILLING_PRESCRIPTION_CHARGE_CONSUMER_ENABLED",
                    "MEDIFLOW_BILLING_PAYMENT_REQUEST_DISCOVERY_ENABLED", "MEDIFLOW_BILLING_OUTPATIENT_PUBLICATION_ENABLED"],
                "support-service": ["MEDIFLOW_CARE_FINANCE_V2", "MEDIFLOW_REPORT_OUTPATIENT_CONSUMER_ENABLED",
                    "MEDIFLOW_REPORT_OUTPATIENT_READ_ENABLED", "MEDIFLOW_NOTIFICATION_CARE_V1_ENABLED",
                    "MEDIFLOW_NOTIFICATION_OUTPATIENT_PRESCRIPTION_CONSUMER_ENABLED", "MEDIFLOW_NOTIFICATION_REFUND_CONSUMER_ENABLED"]}
            env.update({gate: "true" for gate in gates.get(name, [])})
            # Concurrent JVM/test startup on the demo laptop needs a bounded local
            # auth warm-up budget; preserve the production default configuration.
            if name == "gateway":
                env["MEDIFLOW_ORGANIZATION_AUTH_TIMEOUT_MILLIS"] = "15000"
            env["MEDIFLOW_PHARMACY_OUTPATIENT_EVENTS_ACCEPTANCE_REFERENCE"] = "LOCAL-DEMO-REHEARSAL"
            if name == "billing-service":
                # The scheduling lock adapter uses direct internal HTTP in Docker.
                # Packaged JAR rehearsal must use the isolated Clinical port instead.
                env["MEDIFLOW_CLIENTS_CLINICAL_SERVICE"] = f"http://127.0.0.1:{PORTS['clinical-service']}"
                env["MEDIFLOW_BILLING_PRESCRIPTION_PRICE_CODES"] = "RX-DEMO"
                env["MEDIFLOW_BILLING_OUTPATIENT_PUBLICATION_PURPOSES"] = "EXAM,LAB_TEST,PRESCRIPTION"
                env["MEDIFLOW_BILLING_OUTPATIENT_PUBLICATION_ACCEPTANCE_REFERENCE"] = "LOCAL-DEMO-REHEARSAL"
                if not options.scheduling:
                    env["SPRING_APPLICATION_JSON"] = json.dumps({
                        "billing.care-finance.prices.OUTPATIENT_EXAM.unitAmount": 150000,
                        "billing.care-finance.prices.OUTPATIENT_EXAM.description": "Phi kham ngoai tru demo",
                        "billing.care-finance.prices.LAB.unitAmount": 80000,
                        "billing.care-finance.prices.LAB.description": "Phi xet nghiem demo",
                        "mediflow.billing.outpatient-refund-publication.enabled": True,
                        "mediflow.billing.outpatient-refund-publication.acceptance-reference": "LOCAL-DEMO-REHEARSAL"})
            jar = ROOT / "backend" / name / "target" / (name + "-0.0.1-SNAPSHOT.jar")
            stream = (evidence / (name + ".log")).open("w", encoding="utf-8")
            streams.append(stream)
            process = subprocess.Popen(["java", "-Xms64m", "-Xmx256m", "-jar", str(jar)],
                                       cwd=ROOT, env=env, stdout=stream, stderr=subprocess.STDOUT,
                                       creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            applications.append(process)
            app_processes[name] = process
            manifest['pids'].append(process.pid)
            save_manifest()
            return process

        def wait_application(name, process):
            def ready():
                if (evidence / 'stop').exists():
                    raise InterruptedError('Demo startup stopped by request')
                if process.poll() is not None:
                    raise RuntimeError(name + " exited; inspect its log")
                return get_json(f"http://127.0.0.1:{PORTS[name]}/actuator/health").get("status") == "UP"
            await_ready(ready)
            health[name] = get_json(f"http://127.0.0.1:{PORTS[name]}/actuator/health")
            print(name + " UP", flush=True)
        start_application_batches(launch, wait_application, options.startup_parallelism)
        print(f"TIMING applications: {time.monotonic() - phase_started:.1f}s", flush=True)
        (evidence / "health.json").write_text(json.dumps(health, indent=2), encoding="utf-8")
        phase_started = time.monotonic()
        if options.prepare_report_demo:
            from prepare_ui_demo import DemoApi
            from prepare_report_demo import prepare as prepare_report
            report_api = DemoApi(manifest['gateway'], evidence / 'report-demo-journal.local.json', evidence / 'stop')
            report_evidence = prepare_report(report_api, evidence, accept=True)
            # The chart seed's 2025 rows supersede the snapshot's small 2025 coverage on purpose.
            if not report_evidence['accepted']:
                raise RuntimeError("Expected the accepted synthetic 2024 Report period")
            print("REPORT DEMO READY: " + str(report_evidence['availableSyntheticPeriods'])
                  + " accepted synthetic periods; source evidence: "
                  + str(evidence / 'report-demo-acceptance.local.json'), flush=True)

            # Keep the acceptance surface open only while the reviewed synthetic fixture is submitted.
            previous = app_processes['support-service']
            previous.terminate()
            try:
                previous.wait(timeout=15)
            except subprocess.TimeoutExpired:
                previous.kill()
                previous.wait(timeout=5)
            applications.remove(previous)
            manifest['pids'].remove(previous.pid)
            save_manifest()
            old_stream = next(stream for stream in streams if stream.name.endswith('support-service.log'))
            old_stream.close()
            streams.remove(old_stream)
            replacement = launch('support-service', report_acceptance=False)
            wait_application('support-service', replacement)
            print("Report acceptance gate disabled after demo fixture preparation.", flush=True)
            (evidence / "health.json").write_text(json.dumps(health, indent=2), encoding="utf-8")
        if options.prepare_ui_demo:
            # UI schedules, cashier charges and per-presenter staged patients; seed-demo.bat reruns the same.
            from prepare_ui_demo import DemoApi
            from seed_demo import prepare_seeds
            prepare_seeds(manifest['gateway'], evidence, evidence / 'stop')
            from prepare_demo_calendar import prepare as prepare_calendar, write_fixtures as write_calendar
            calendar_api = DemoApi(manifest['gateway'], evidence / 'calendar-journal.local.json', evidence / 'stop')
            cached_calendar = evidence / 'calendar-fixtures.local.json'
            if reused and cached_calendar.exists():
                from prepare_demo_calendar import verify
                calendar = json.loads(cached_calendar.read_text(encoding='utf-8'))
                calendar_api.login('admin', 'demo.staff01@example.test')
                verify(calendar_api, calendar)
                write_calendar(calendar, evidence)
                print("Reused and verified the existing calendar; calendar writes skipped.", flush=True)
            else:
                write_calendar(prepare_calendar(calendar_api), evidence)
            # Doctors with an active, a locked and no account, for the head-appointment demo.
            from prepare_head_candidates import prepare as prepare_heads, write_fixtures as write_heads
            write_heads(prepare_heads(DemoApi(manifest['gateway'], evidence / 'head-candidates-journal.local.json',
                                              evidence / 'stop')), evidence)
        print(f"TIMING UI preparation: {time.monotonic() - phase_started:.1f}s", flush=True)
        result_text = "PASS: isolated infrastructure ready, six business health checks plus Gateway/Eureka.\nThis is topology smoke, not all business workflows.\n"
        if report_evidence:
            result_text += (f"Report fixture: {report_evidence['availableSyntheticPeriods']} accepted synthetic periods; "
                            "the 2024 annual scope was verified through ADMIN API and current statistics remain provisional.\n")
            result_text += "Acceptance endpoint gate disabled after preparation.\n"
        (evidence / "result.txt").write_text(result_text, encoding="utf-8")
        print("READY " + str(evidence), flush=True)
        if options.keep:
            deadline = time.monotonic() + 7200
            while not (evidence / "stop").exists() and time.monotonic() < deadline:
                time.sleep(2)
    except InterruptedError:
        if not (evidence / 'stop').exists():
            raise
        (evidence / 'result.txt').write_text('STOPPED by request.\n', encoding='utf-8')
        print('STOPPED by request.', flush=True)
    except Exception as error:
        (evidence / "result.txt").write_text("FAILED: " + str(error), encoding="utf-8")
        raise
    finally:
        for process in reversed(applications):
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)
        for stream in streams:
            stream.close()
        for container in ([] if workspace else reversed(containers)):
            command("docker", "logs", container, log=evidence / (container.rsplit("-", 1)[-1] + ".log"), check=False)
            command("docker", "rm", "-f", container, check=False)
        if lock:
            lock.release()


if __name__ == "__main__":
    main()
