"""Restore the demo into disposable infrastructure and smoke six packaged deployments.

Usage: python scripts/demo_25_10/rehearse_snapshot.py [--keep]
With --keep, the stack stays up for UI rehearsal until its evidence/stop file exists.
Requires Docker Compose, Python 3 and the eight already-built application JARs.
Never connects to existing databases, volumes or broker queues.
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

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
NAMES = ["eureka-server", "core-service", "clinical-service", "lab-service",
         "pharmacy-service", "billing-service", "support-service", "gateway"]
PORTS = dict(zip(NAMES, [28761, 28089, 28082, 28084, 28085, 28086, 28087, 28080]))
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
        except (OSError, ValueError):
            pass
        time.sleep(2)
    raise RuntimeError("Readiness deadline exceeded")


def main():
    keep = argparse.ArgumentParser()
    keep.add_argument("--keep", action="store_true")
    keep.add_argument("--scheduling", action="store_true",
                      help="Enable the opt-in outpatient revision 2 rehearsal overlay")
    keep.add_argument("--prepare-ui-demo", action="store_true",
                      help="Prepare current schedules and a completed follow-up source through Gateway")
    options = keep.parse_args()
    if options.prepare_ui_demo and not options.scheduling:
        keep.error("--prepare-ui-demo requires --scheduling")
    stamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    evidence = HERE / "evidence" / ("snapshot-" + stamp)
    evidence.mkdir(parents=True)
    containers, applications, streams = [], [], []
    prefix = "mediflow-rehearsal-" + stamp
    pg, rabbit = prefix + "-pg", prefix + "-rabbit"
    try:
        for port in PORTS.values():
            with socket.socket() as candidate:
                candidate.bind(("127.0.0.1", port))
        compose_args = ["docker", "compose", "-f", "docker-compose.yml"]
        if options.scheduling:
            compose_args.extend(["-f", "compose.demo.local.yml", "-f", "compose.outpatient.local.yml"])
        compose = json.loads(command(*compose_args, "config", "--format", "json").stdout)["services"]
        for name in NAMES:
            jar = ROOT / "backend" / name / "target" / (name + "-0.0.1-SNAPSHOT.jar")
            if not jar.is_file():
                raise RuntimeError("Build missing JAR: " + str(jar))
        command("docker", "run", "-d", "--name", pg, "-e", "POSTGRES_PASSWORD=postgres",
                "-p", "127.0.0.1::5432", "postgres:16-alpine", log=evidence / "postgres-container.log")
        containers.append(pg)
        pgport = command("docker", "port", pg, "5432/tcp").stdout.strip().rsplit(":", 1)[1]
        await_ready(lambda: command("docker", "exec", pg, "pg_isready", "-U", "postgres", check=False).returncode == 0)
        command("docker", "cp", str(HERE / "MediFlow_Demo_Database_2026-10-25.sql"), pg + ":/tmp/demo.sql")
        command("docker", "exec", pg, "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", "postgres",
                "-f", "/tmp/demo.sql", log=evidence / "restore.log")
        # Reimport must refuse before loading any data into a populated target.
        refused = command("docker", "exec", pg, "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "postgres",
                          "-d", "postgres", "-f", "/tmp/demo.sql", log=evidence / "restore-refusal.log", check=False)
        if refused.returncode == 0 or "demo restore refused" not in (refused.stdout + refused.stderr).lower():
            raise RuntimeError("The populated-target restore guard was not confirmed")
        if options.scheduling:
            command("docker", "cp", str(HERE / "seed_outpatient_scheduling.sql"), pg + ":/tmp/scheduling.sql")
            command("docker", "exec", pg, "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "postgres",
                    "-d", "mediflow_organization", "-f", "/tmp/scheduling.sql", log=evidence / "scheduling-seed.log")
        counts = {}
        for context in DATABASES:
            query = "SELECT count(*) FROM pg_tables WHERE schemaname='public'"
            value = command("docker", "exec", pg, "psql", "-XAt", "-U", "postgres", "-d", "mediflow_" + context,
                            "-c", query).stdout.strip()
            counts[context] = {"publicTables": int(value)}
            if not int(value):
                raise RuntimeError("Empty restored context: " + context)
        (evidence / "restored-contexts.json").write_text(json.dumps(counts, indent=2), encoding="utf-8")
        command("docker", "run", "-d", "--name", rabbit, "--hostname", "rehearsal-rabbit",
                "-e", "RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS=+S 2:2", "-e", "RABBITMQ_DEFAULT_USER=rehearsal",
                "-e", "RABBITMQ_DEFAULT_PASS=rehearsal-local", "-p", "127.0.0.1::5672", "rabbitmq:3.13-alpine")
        containers.append(rabbit)
        rabbitport = command("docker", "port", rabbit, "5672/tcp").stdout.strip().rsplit(":", 1)[1]
        def broker_ready():
            state = command("docker", "inspect", rabbit, "--format", "{{.State.Status}}").stdout.strip()
            if state == "exited":
                raise RuntimeError("Rehearsal broker exited; inspect rabbit.log")
            boot_log = command("docker", "logs", rabbit)
            return "Server startup complete" in boot_log.stdout + boot_log.stderr
        # Do not run Erlang CLI as root during boot: it can create a root-owned
        # .erlang.cookie before the broker user, causing an eacces startup failure.
        await_ready(broker_ready)
        command("docker", "exec", "--user", "rabbitmq", rabbit, "rabbitmq-diagnostics", "-q", "ping")
        health = {}
        for name in NAMES:
            env = os.environ.copy()
            for key, value in compose[name].get("environment", {}).items():
                if value is not None:
                    value = str(value).replace("postgres:5432", "127.0.0.1:" + pgport)
                    value = value.replace("http://eureka-server:8761", "http://127.0.0.1:28761")
                    env[key] = value
            env.update({"SERVER_PORT": str(PORTS[name]), "SPRING_PROFILES_ACTIVE": "outpatient-mvp",
                        "EUREKA_DEFAULT_ZONE": "http://127.0.0.1:28761/eureka/",
                        "EUREKA_CLIENT_SERVICEURL_DEFAULTZONE": "http://127.0.0.1:28761/eureka/",
                        "EUREKA_INSTANCE_PREFER_IP_ADDRESS": "true", "EUREKA_INSTANCE_IP_ADDRESS": "127.0.0.1",
                        "EUREKA_INSTANCE_HOSTNAME": "127.0.0.1", "SPRING_RABBITMQ_HOST": "127.0.0.1",
                        "SPRING_RABBITMQ_PORT": rabbitport, "SPRING_RABBITMQ_USERNAME": "rehearsal",
                        "SPRING_RABBITMQ_PASSWORD": "rehearsal-local", "MEDIFLOW_RABBIT_HOST": "127.0.0.1",
                        "MEDIFLOW_RABBIT_PORT": rabbitport, "MEDIFLOW_RABBIT_USER": "rehearsal",
                        "MEDIFLOW_RABBIT_PASSWORD": "rehearsal-local", "MEDIFLOW_DEMO_SEED_ENABLED": "false"})
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
                env["MEDIFLOW_CLIENTS_CLINICAL_SERVICE"] = "http://127.0.0.1:28082"
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
            def ready():
                if process.poll() is not None:
                    raise RuntimeError(name + " exited; inspect its log")
                return get_json(f"http://127.0.0.1:{PORTS[name]}/actuator/health").get("status") == "UP"
            await_ready(ready)
            health[name] = get_json(f"http://127.0.0.1:{PORTS[name]}/actuator/health")
            print(name + " UP", flush=True)
        (evidence / "health.json").write_text(json.dumps(health, indent=2), encoding="utf-8")
        manifest = {"evidence": str(evidence), "gateway": "http://127.0.0.1:28080", "ports": PORTS,
                    "scheduling": options.scheduling,
                    "containers": containers, "pids": [p.pid for p in applications], "stopFile": str(evidence / "stop")}
        (HERE / "evidence" / "active-rehearsal.local.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
        if options.prepare_ui_demo:
            from prepare_ui_demo import DemoApi, prepare, write_fixtures, ZONE
            fixtures = prepare(DemoApi(manifest['gateway'], evidence / 'ui-demo-journal.local.json'), dt.datetime.now(ZONE))
            write_fixtures(fixtures, evidence)
        (evidence / "result.txt").write_text("PASS: eight database restores, populated-target refusal, six business health checks plus Gateway/Eureka.\nThis is topology smoke, not all business workflows.\n", encoding="utf-8")
        print("READY " + str(evidence), flush=True)
        if options.keep:
            deadline = time.monotonic() + 7200
            while not (evidence / "stop").exists() and time.monotonic() < deadline:
                time.sleep(2)
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
        for stream in streams:
            stream.close()
        for container in reversed(containers):
            command("docker", "logs", container, log=evidence / (container.rsplit("-", 1)[-1] + ".log"), check=False)
            command("docker", "rm", "-f", container, check=False)


if __name__ == "__main__":
    main()
