"""Populate the disposable six-service acceptance environment through Gateway APIs.

Run after MEDIFLOW_FE_ACCEPTANCE_HOLD=true has written its browser manifest.
Credentials and the generated identity manifest stay under .git, outside report assets.
"""
import datetime as dt
import json
import pathlib
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[3]
BASE = ROOT / "backend/support-service/target/six-service-acceptance/fe-browser-manifest.json"
OUT = ROOT / ".git/review3-seed.json"
PASSWORD = "Acceptance-pass-2026"
if OUT.exists():
    raise SystemExit("A seed manifest already exists. Reuse the current demo; do not seed duplicate masters.")
manifest = json.loads(BASE.read_text(encoding="utf-8"))
gateway = manifest["gateway"]
today = dt.date.today().isoformat()
tokens = {}
journal = []


def call(method, path, role="ADMIN", body=None, headers=None):
    h = {"Content-Type": "application/json"}
    if role:
        h["Authorization"] = "Bearer " + tokens[role]
    h.update(headers or {})
    req = urllib.request.Request(gateway + "/api/v1" + path,
        data=None if body is None else json.dumps(body, ensure_ascii=False).encode(),
        headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            result = json.loads(response.read())
            journal.append({"method": method, "path": path, "status": response.status})
            return result.get("data", result)
    except urllib.error.HTTPError as error:
        detail = error.read().decode()
        raise RuntimeError(f"{method} {path}: {error.code} {detail}") from error


def login(username):
    return call("POST", "/auth/login", None,
                {"username": username, "password": PASSWORD})["accessToken"]


def wait_read(path, role="ADMIN", predicate=lambda result: True):
    deadline = time.monotonic() + 75
    while True:
        try:
            result = call("GET", path, role)
            if predicate(result):
                return result
        except RuntimeError as error:
            if ": 404 " not in str(error):
                raise
        if time.monotonic() > deadline:
            raise TimeoutError(path)
        time.sleep(1)


def pay(purpose, source, patient, episode):
    query = urllib.parse.urlencode(dict(purpose=purpose, sourceId=source,
                                      patientId=patient, careEpisodeId=episode))
    request = wait_read("/billing/payment-requests/by-source?" + query, "CASHIER")
    return call("POST", "/billing/payment-requests/" + request["paymentRequestId"] + "/payments",
                "CASHIER", dict(idempotencyKey="review3-" + str(uuid.uuid4()),
                    amount=request["remainingAmount"], currency=request["currency"], paymentMethod="CASH"))


tokens["ADMIN"] = login(manifest["accounts"]["ADMIN"])
data = dict(sourceCommit=subprocess.check_output(["git", "rev-parse", "--short", "HEAD"], cwd=ROOT, text=True).strip(), createdAt=dt.datetime.now().isoformat(),
            gateway=gateway, date=today, accounts={}, departments=[], staff=[],
            patients=[], appointments=[], records=[], drugs=[], lab=[], prescriptions=[], payments=[])

for name, short, kind, location in [
    ("Khoa Nội tổng quát", "NTQ", "CLINICAL", "Tầng 1 · Khu khám A"),
    ("Khoa Nhi", "NHI", "CLINICAL", "Tầng 1 · Khu khám B"),
    ("Khoa Xét nghiệm", "XN", "PARACLINICAL", "Tầng 2 · Khu cận lâm sàng"),
    ("Phòng Hành chính", "HC", "ADMINISTRATIVE", "Tầng 3")]:
    data["departments"].append(call("POST", "/org/departments", body=dict(
        departmentName=name, abbreviation=short, departmentType=kind, location=location)))
department = data["departments"][0]["departmentId"]
roles = [
    ("ADMIN", "Nguyễn Minh Quân", "ADMINISTRATIVE"),
    ("MANAGER", "Trần Thu Hà", "MANAGER"),
    ("DOCTOR", "Lê Hoàng Phúc", "DOCTOR"),
    ("NURSE", "Phạm Ngọc Mai", "NURSE"),
    ("LAB_TECH", "Võ Thanh Tâm", "TECHNICIAN"),
    ("PHARMACIST", "Đặng Thảo Nguyên", "PHARMACIST"),
    ("CASHIER", "Bùi Khánh Linh", "CASHIER")]
for index, (role, name, job) in enumerate(roles, 1):
    staff = call("POST", "/org/staff", body=dict(fullName=name, departmentId=department,
        jobTitle=job, specialization="Nội tổng quát" if job == "DOCTOR" else None,
        licenseNumber="R3-BS-2026" if job == "DOCTOR" else None,
        phoneNumber=f"090100{index:04}", email=f"review3.{role.lower()}@example.test",
        identityNumber=f"999260{index:06}"))
    data["staff"].append(staff)
    username = "review3." + role.lower()
    call("POST", "/org/accounts", body=dict(username=username, password=PASSWORD,
        staffId=staff["staffId"], patientId=None, role=role))
    data["accounts"][role] = username
    tokens[role] = login(username)
doctor = next(s["staffId"] for s in data["staff"] if s["jobTitle"] == "DOCTOR")
names = ["Nguyễn Văn An", "Trần Thị Hương", "Lê Minh Khang", "Phạm Ngọc Anh",
         "Võ Quốc Bảo", "Đặng Thanh Trúc", "Bùi Gia Huy", "Đỗ Thị Lan",
         "Hồ Minh Đức", "Ngô Thảo Vy", "Dương Tuấn Kiệt", "Vũ Ngọc Hà"]
reasons = ["Khám sức khỏe định kỳ", "Đau đầu và mệt mỏi ba ngày", "Ho và đau họng",
           "Tái khám huyết áp", "Đau vùng thượng vị", "Kiểm tra đường huyết"]
for index, name in enumerate(names):
    patient = call("POST", "/patients", "NURSE", dict(hoTen=name,
        ngaySinh=f"{1982+index}-04-12", gioiTinh="M" if index % 2 == 0 else "F",
        soCmnd=f"999261{index:06}", diaChi=f"{12+index} Đường Hoa Mai, TP. Hồ Chí Minh",
        soDienThoai=f"090200{index:04}", email=f"review3.patient{index}@example.test", bhytSo=None))
    data["patients"].append(patient)
    appointment = call("POST", "/appointments", "NURSE", dict(patientId=patient["maBenhNhan"],
        doctorId=doctor, departmentId=department, appointmentDate=today,
        appointmentTime=f"{8+index//4:02}:{(index%4)*15:02}", reason=reasons[index%len(reasons)]))
    data["appointments"].append(appointment)
patient = data["patients"][0]["maBenhNhan"]
call("POST", "/org/accounts", body=dict(username="review3.patient", password=PASSWORD,
    staffId=None, patientId=patient, role="PATIENT"))
data["accounts"]["PATIENT"] = "review3.patient"
tokens["PATIENT"] = login("review3.patient")

for name, ingredient, price, stock in [
    ("Paracetamol 500 mg", "Paracetamol", 1500, 250),
    ("Vitamin C 500 mg", "Ascorbic acid", 2000, 180),
    ("Nước muối sinh lý 0,9%", "Natri clorid", 12000, 65),
    ("Oresol", "Electrolytes", 5500, 90),
    ("Loratadin 10 mg", "Loratadin", 2500, 8),
    ("Omeprazol 20 mg", "Omeprazol", 3500, 120)]:
    data["drugs"].append(call("POST", "/pharmacy/drugs", body=dict(drugName=name,
        activeIngredient=ingredient, unit="chai" if "muối" in name else "viên",
        price=price, stockQuantity=stock, expiryDate="2028-06-30", manufacturer="MediFlow Demo",
        lowStockThreshold=10)))

OUT.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

for index, appointment in enumerate(data["appointments"][:6]):
    aid, pid = appointment["appointmentId"], appointment["patientId"]
    call("PUT", f"/appointments/{aid}/check-in", "NURSE", {})
    if index == 5:
        continue
    data["payments"].append(pay("EXAM", aid, pid, aid))
    wait_read(f"/appointments/{aid}", "NURSE", lambda a: a["status"] == "READY_FOR_EXAM")
    if index == 4:
        continue
    call("PUT", f"/appointments/{aid}/start-exam", "DOCTOR", {"emergencyOverride": None})
    record = next(r for r in call("GET", f"/records/patient/{pid}", "DOCTOR") if r["appointmentId"] == aid)
    rid = record["recordId"]
    call("PUT", f"/records/{rid}", "DOCTOR", dict(symptoms=reasons[index]+". Tỉnh táo, tiếp xúc tốt; mạch 76 lần/phút."))
    call("POST", f"/records/{rid}/diagnoses", "DOCTOR", dict(diagnosisName="Theo dõi nhiễm khuẩn hô hấp trên",
        description="Theo dõi triệu chứng và tái khám theo hẹn", icdCode="J06.9"))
    source = call("POST", f"/records/{rid}/lab-orders", "DOCTOR",
                  dict(labType="CBC", requestedDate=today, priceCode="LAB"))
    test = call("POST", "/lab", "DOCTOR", dict(recordId=rid, patientId=pid,
        requestingDepartmentId=department, labType=source["labType"], requestedDate=source["requestedDate"],
        sourceOrderId=source["sourceOrderId"], careEpisodeType=source["careEpisodeType"],
        careEpisodeId=source["careEpisodeId"], priceCode=source["priceCode"]))
    tid = test["testId"]
    execution_started = False
    if index < 3:
        data["payments"].append(pay("LAB_TEST", tid, pid, source["careEpisodeId"]))
        wait_read(f"/lab/{tid}", "LAB_TECH", lambda t: t["status"] == "READY")
        if index < 2:
            try:
                call("PUT", f"/lab/{tid}/start", "LAB_TECH", {"emergencyOverride": None})
                execution_started = True
            except RuntimeError as error:
                if "LAB_CLEARANCE_REQUIRED" not in str(error):
                    raise
                data.setdefault("observedLimitations", []).append(dict(testId=tid,
                    code="LAB_CLEARANCE_REQUIRED", operation="start laboratory execution"))
        if index == 0 and execution_started:
            call("PUT", f"/lab/{tid}/results", "LAB_TECH", dict(performedDate=today,
                conclusion="Các chỉ số trong khoảng tham chiếu.", results=[
                    dict(indicator="WBC", value="6.8", unit="G/L", referenceRange="4.0–10.0"),
                    dict(indicator="HGB", value="142", unit="g/L", referenceRange="130–170"),
                    dict(indicator="PLT", value="245", unit="G/L", referenceRange="150–400")]))
    data["lab"].append(call("GET", f"/lab/{tid}", "LAB_TECH"))
    rx = call("POST", "/pharmacy/prescriptions", "DOCTOR", dict(recordId=rid, patientId=pid,
        doctorId=doctor, departmentId=department, prescribedDate=today,
        lines=[dict(drugId=data["drugs"][0]["drugId"], quantity=6, dosage="Theo hướng dẫn của bác sĩ")],
        careContractVersion=1, careContext="OUTPATIENT", careEpisodeType=source["careEpisodeType"],
        careEpisodeId=source["careEpisodeId"], admissionId=None, priceCode="DRUG"),
        {"Idempotency-Key": str(uuid.uuid4())})
    if index < 2:
        data["payments"].append(pay("PRESCRIPTION", rx["prescriptionId"], pid, source["careEpisodeId"]))
        wait_read("/pharmacy/prescriptions/"+rx["prescriptionId"], "PHARMACIST", lambda r: r["paymentConfirmed"])
    if index == 0:
        call("PUT", "/pharmacy/prescriptions/"+rx["prescriptionId"]+"/dispense", "PHARMACIST")
        if execution_started:
            call("PUT", f"/records/{rid}/complete", "DOCTOR", dict(disposition="OUTPATIENT_FOLLOW_UP",
                dispositionNote="Theo dõi tại nhà, tái khám sau 7 ngày hoặc khi có dấu hiệu bất thường."))
    data["prescriptions"].append(call("GET", "/pharmacy/prescriptions/"+rx["prescriptionId"], "PHARMACIST"))
    data["records"].append(call("GET", f"/records/{rid}", "DOCTOR"))
    OUT.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

call("POST", "/notifications/send", body=dict(patientId=patient, title="Nhắc lịch tái khám",
    content="Mời anh Nguyễn Văn An tái khám theo lịch hẹn. Vui lòng mang theo kết quả xét nghiệm.",
    channel="IN_APP", recipientAddress=None))
data["appointments"] = [call("GET", "/appointments/"+a["appointmentId"], "NURSE") for a in data["appointments"]]
data["journal"] = journal
OUT.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps({key:len(data[key]) for key in ["accounts","departments","staff","patients","appointments","records","drugs","lab","prescriptions","payments"]}))
