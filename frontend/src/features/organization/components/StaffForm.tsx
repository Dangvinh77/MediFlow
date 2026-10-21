"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import { getToken } from "@/lib/auth";
import { organizationApi } from "../api";
import { CreationIntentError, prepareCreation } from "../creation-intent";
import { isRequestSessionCurrent } from "../request-session";
import type { DepartmentDTO, JobTitle } from "../types";

interface Props { staffId?: string }
const jobs: { value: JobTitle; label: string }[] = [{ value: "DOCTOR", label: "Bác sĩ" }, { value: "NURSE", label: "Điều dưỡng" }, { value: "TECHNICIAN", label: "Kỹ thuật viên" }, { value: "PHARMACIST", label: "Dược sĩ" }, { value: "CASHIER", label: "Thu ngân" }, { value: "MANAGER", label: "Quản lý" }, { value: "ADMINISTRATIVE", label: "Hành chính" }];

export function StaffForm({ staffId }: Props) {
  return <StaffFormWorkspace key={staffId ?? "new"} staffId={staffId} />;
}

function StaffFormWorkspace({ staffId }: Props) {
  const [identityNumber, setIdentityNumber] = useState("");
  const router = useRouter(); const editing = Boolean(staffId);
  const [departments, setDepartments] = useState<DepartmentDTO[]>([]); const [departmentId, setDepartmentId] = useState(""); const [fullName, setFullName] = useState(""); const [jobTitle, setJobTitle] = useState<JobTitle>("DOCTOR"); const [specialization, setSpecialization] = useState(""); const [licenseNumber, setLicenseNumber] = useState(""); const [phoneNumber, setPhoneNumber] = useState(""); const [email, setEmail] = useState(""); const [loading, setLoading] = useState(true); const [loadedStaffId, setLoadedStaffId] = useState<string | null>(null); const [retryToken, setRetryToken] = useState(0); const [saving, setSaving] = useState(false); const [error, setError] = useState<string | null>(null); const [loadError, setLoadError] = useState<{ key: string; message: string } | null>(null);
  const mounted = useRef(true);
  const submitting = useRef(false);
  const savedProfile = useRef<{ fingerprint: string; session: string | null } | null>(null);
  const originalDepartment = useRef<string | null>(null);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    let mounted = true;
    const sessionToken = getToken();
    Promise.all([organizationApi.departments(!staffId), staffId ? organizationApi.staffMember(staffId) : Promise.resolve(null)]).then(([departmentList, staff]) => {
      if (!mounted || !isRequestSessionCurrent(sessionToken)) return;
      setDepartments(departmentList.filter((department) => department.active || department.departmentId === staff?.departmentId));
      if (staff) {
        originalDepartment.current = staff.departmentId;
        setFullName(staff.fullName);
        setIdentityNumber(staff.identityNumber ?? "");
        setDepartmentId(staff.departmentId);
        setJobTitle(staff.jobTitle);
        setSpecialization(staff.specialization ?? "");
        setLicenseNumber(staff.licenseNumber ?? "");
        setPhoneNumber(staff.phoneNumber ?? "");
        setEmail(staff.email ?? "");
      }
      setLoadedStaffId(staffId ?? "new");
    }).catch((cause: unknown) => {
      if (!mounted || !isRequestSessionCurrent(sessionToken, cause)) return;
      if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login");
      setLoadError({ key: staffId ?? "new", message: cause instanceof Error ? cause.message : "Không thể tải dữ liệu nhân sự." });
    }).finally(() => { if (mounted) setLoading(false); });
    return () => { mounted = false; };
  }, [staffId, retryToken, router]);

  function applyStaff(staff: Awaited<ReturnType<typeof organizationApi.staffMember>>) {
    originalDepartment.current = staff.departmentId;
    setFullName(staff.fullName);
    setIdentityNumber(staff.identityNumber ?? "");
    setJobTitle(staff.jobTitle);
    setSpecialization(staff.specialization ?? "");
    setLicenseNumber(staff.licenseNumber ?? "");
    setPhoneNumber(staff.phoneNumber ?? "");
    setEmail(staff.email ?? "");
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current) return;
    if (!editing && !/^[0-9]{12}$/.test(identityNumber.trim())) {
      setError("CCCD phải gồm đúng 12 chữ số.");
      return;
    }
    submitting.current = true;
    setSaving(true); setError(null);
    const sessionToken = getToken();
    // Capture the user's transfer intent before a save/read refreshes the baseline.
    const wantsTransfer = departmentId !== originalDepartment.current;
    const shared = { fullName: fullName.trim(), jobTitle, specialization: specialization.trim() || null, licenseNumber: licenseNumber.trim() || null, phoneNumber: phoneNumber.trim() || null, email: email.trim() || null };
    let creationIntent: Awaited<ReturnType<typeof prepareCreation>> | undefined;
    let completed = false;
    try {
      if (staffId) {
        const fingerprint = JSON.stringify(shared);
        // Retry a partially completed transfer against a fresh authoritative read.
        // Do not repeat the successful profile command when its fields are unchanged.
        const updatedStaff = savedProfile.current?.fingerprint === fingerprint && savedProfile.current.session === sessionToken
          ? await organizationApi.staffMember(staffId)
          : await organizationApi.updateStaff(staffId, shared);
        if (!mounted.current || getToken() !== sessionToken) return;
        savedProfile.current = { fingerprint, session: sessionToken };
        applyStaff(updatedStaff);
        if (wantsTransfer && departmentId !== updatedStaff.departmentId) {
          const requestedDepartmentId = departmentId;
          try {
            const movedStaff = await organizationApi.changeStaffDepartment(staffId, requestedDepartmentId);
            if (!mounted.current || getToken() !== sessionToken) return;
            applyStaff(movedStaff);
            setDepartmentId(movedStaff.departmentId);
            router.push(`/organization/staff/${movedStaff.staffId}?notice=updated`);
            completed = true;
            return;
          } catch (transferCause: unknown) {
            if (!mounted.current || !isRequestSessionCurrent(sessionToken, transferCause)) return;
            if (transferCause instanceof ApiRequestError && transferCause.status === 401) {
              router.replace("/login");
              return;
            }

            try {
              const currentStaff = await organizationApi.staffMember(staffId);
              if (!mounted.current || getToken() !== sessionToken) return;
              applyStaff(currentStaff);
              if (currentStaff.departmentId === requestedDepartmentId) {
                setDepartmentId(currentStaff.departmentId);
                router.push(`/organization/staff/${currentStaff.staffId}?notice=updated`);
                completed = true;
                return;
              }
              setDepartmentId(requestedDepartmentId);
              setError(`Thông tin nhân sự đã được lưu, nhưng chưa thể xác nhận việc chuyển khoa: ${transferCause instanceof Error ? transferCause.message : "Lỗi không xác định."} Bạn có thể thử lưu lại để hoàn tất chuyển khoa.`);
            } catch (refreshCause: unknown) {
              if (!mounted.current || !isRequestSessionCurrent(sessionToken, refreshCause)) return;
              if (refreshCause instanceof ApiRequestError && refreshCause.status === 401) {
                router.replace("/login");
                return;
              }
              applyStaff(updatedStaff);
              setDepartmentId(requestedDepartmentId);
              setError(`Thông tin nhân sự đã được lưu, nhưng chưa xác minh được khoa hiện tại sau lỗi chuyển khoa (${transferCause instanceof Error ? transferCause.message : "lỗi không xác định"}). Hãy thử lưu lại để hệ thống xác minh và hoàn tất chuyển khoa. ${refreshCause instanceof Error ? refreshCause.message : "Không thể tải hồ sơ hiện tại."}`);
            }
            return;
          }
        }
        router.push(`/organization/staff/${updatedStaff.staffId}?notice=updated`);
        completed = true;
      } else {
        const body = { ...shared, departmentId, identityNumber: identityNumber.trim() };
        creationIntent = await prepareCreation("staff", sessionToken, body, body.identityNumber);
        if (!mounted.current || getToken() !== sessionToken) { creationIntent.cancelUnsent(); return; }
        const staff = await organizationApi.createStaff(body, creationIntent.key);
        creationIntent.settle();
        if (!mounted.current || getToken() !== sessionToken) return;
        router.push(`/organization/staff/${staff.staffId}?notice=created`);
        completed = true;
      }
    } catch (cause: unknown) {
      creationIntent?.settle(cause);
      if (!mounted.current || !isRequestSessionCurrent(sessionToken, cause)) return;
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
      } else if (cause instanceof ApiRequestError && cause.code === "STAFF_IDENTITY_DUPLICATE") {
        setError("CCCD này đã có hồ sơ nhân sự. Hãy kiểm tra hồ sơ hiện có trước khi tạo mới.");
      } else if (cause instanceof CreationIntentError) {
        setError(cause.message);
      } else if (!staffId && (!(cause instanceof ApiRequestError) || cause.status < 400 || cause.status >= 500 || cause.status === 408)) {
        setError("Chưa xác nhận được kết quả tạo nhân sự. Hãy thử lưu lại cùng thông tin để hệ thống đối soát và trả về hồ sơ đã tạo. "
          + (cause instanceof Error ? cause.message : "Kết nối bị gián đoạn."));
      } else {
        setError(cause instanceof Error ? cause.message : "Không thể lưu nhân sự.");
      }
    }
    finally {
      if (!completed) {
        submitting.current = false;
        if (mounted.current && getToken() === sessionToken) setSaving(false);
      }
    }
  }

  const currentLoadError = loadError;
  if (currentLoadError && currentLoadError.key === (staffId ?? "new")) {
    const message = currentLoadError.message;
    return <AsyncState kind="error" message={message} onRetry={() => { setLoading(true); setLoadError(null); setRetryToken((value) => value + 1); }} />;
  }
  if (loading || loadedStaffId !== (staffId ?? "new")) return <AsyncState kind="loading" message="Đang tải dữ liệu nhân sự…" />;
  return <form onSubmit={submit} className="mt-6 space-y-6">{error ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">{error}</p> : null}<fieldset disabled={saving} className="grid gap-5 rounded-xl border border-border bg-surface p-6 md:grid-cols-2"><Field label="Họ tên" htmlFor="staff-name" required><input id="staff-name" required maxLength={100} value={fullName} onChange={(event) => setFullName(event.target.value)} className={controlClassName} /></Field><Field label="CCCD" htmlFor="staff-identity" required={!editing} hint={editing ? "CCCD được xác lập khi tạo hồ sơ." : "Nhập đủ 12 chữ số."}><input id="staff-identity" required={!editing} readOnly={editing} inputMode="numeric" pattern="[0-9]{12}" maxLength={12} value={identityNumber} placeholder={editing && !identityNumber ? "Chưa có CCCD" : undefined} onChange={(event) => setIdentityNumber(event.target.value)} className={controlClassName} /></Field><Field label="Khoa" htmlFor="staff-department" required><select id="staff-department" required value={departmentId} onChange={(event) => setDepartmentId(event.target.value)} className={controlClassName}><option value="">Chọn khoa</option>{departments.map((department) => <option key={department.departmentId} value={department.departmentId}>{department.departmentName}</option>)}</select></Field><Field label="Chức danh" htmlFor="staff-job" required><select id="staff-job" value={jobTitle} onChange={(event) => setJobTitle(event.target.value as JobTitle)} className={controlClassName}>{jobs.map((job) => <option key={job.value} value={job.value}>{job.label}</option>)}</select></Field><Field label="Chuyên môn" htmlFor="staff-specialization"><input id="staff-specialization" maxLength={100} value={specialization} onChange={(event) => setSpecialization(event.target.value)} className={controlClassName} /></Field><Field label="Số giấy phép" htmlFor="staff-license"><input id="staff-license" maxLength={50} value={licenseNumber} onChange={(event) => setLicenseNumber(event.target.value)} className={controlClassName} /></Field><Field label="Số điện thoại" htmlFor="staff-phone" hint="10 đến 15 chữ số."><input id="staff-phone" pattern="[0-9]{10,15}" value={phoneNumber} onChange={(event) => setPhoneNumber(event.target.value)} className={controlClassName} /></Field><Field label="Email" htmlFor="staff-email"><input id="staff-email" type="email" maxLength={100} value={email} onChange={(event) => setEmail(event.target.value)} className={controlClassName} /></Field></fieldset><div className="flex gap-2"><Button type="submit" variant="primary" loading={saving}>{editing ? "Lưu thay đổi" : "Tạo nhân sự"}</Button><Button type="button" disabled={saving} onClick={() => router.back()}>Quay lại</Button></div></form>;
}
