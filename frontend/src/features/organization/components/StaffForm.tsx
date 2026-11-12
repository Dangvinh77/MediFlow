"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useUnsavedChanges } from "../use-unsaved-changes";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import { getSessionKey } from "@/lib/auth";
import { FormSection } from "./RecordLayout";
import { organizationApi } from "../api";
import { CreationIntentError, prepareCreation } from "../creation-intent";
import { isRequestSessionCurrent } from "../request-session";
import { isSaveOutcomeUnknown, staffMatchesDraft, type StaffSaveDraft } from "../save-outcome";
import { SaveOutcomeRecovery } from "./SaveOutcomeRecovery";
import type { DepartmentDTO, JobTitle, StaffProfile } from "../types";

interface Props { staffId?: string }
const jobs: { value: JobTitle; label: string }[] = [{ value: "DOCTOR", label: "Bác sĩ" }, { value: "NURSE", label: "Điều dưỡng" }, { value: "TECHNICIAN", label: "Kỹ thuật viên" }, { value: "PHARMACIST", label: "Dược sĩ" }, { value: "CASHIER", label: "Thu ngân" }, { value: "ADMINISTRATIVE", label: "Hành chính" }];

export function StaffForm({ staffId }: Props) {
  return <StaffFormWorkspace key={staffId ?? "new"} staffId={staffId} />;
}

function StaffFormWorkspace({ staffId }: Props) {
  const { localize } = useLocale();
  const { markDirty, markSaved, confirmLeave } = useUnsavedChanges();
  const [identityNumber, setIdentityNumber] = useState("");
  const [identityChangeReason, setIdentityChangeReason] = useState("");
  const [originalIdentity, setOriginalIdentity] = useState("");
  const [originalEmail, setOriginalEmail] = useState<string | null>(null);
  const router = useRouter(); const editing = Boolean(staffId);
  const [departments, setDepartments] = useState<DepartmentDTO[]>([]); const [departmentId, setDepartmentId] = useState(""); const [fullName, setFullName] = useState(""); const [jobTitle, setJobTitle] = useState<JobTitle>("DOCTOR"); const [specialization, setSpecialization] = useState(""); const [licenseNumber, setLicenseNumber] = useState(""); const [phoneNumber, setPhoneNumber] = useState(""); const [email, setEmail] = useState(""); const [loading, setLoading] = useState(true); const [loadedStaffId, setLoadedStaffId] = useState<string | null>(null); const [retryToken, setRetryToken] = useState(0); const [saving, setSaving] = useState(false); const [error, setError] = useState<string | null>(null); const [loadError, setLoadError] = useState<{ key: string; message: string } | null>(null);
  const [profile, setProfile] = useState<StaffProfile>({ dateOfBirth: null, gender: null, address: null, hireDate: null, qualification: null });
  const mounted = useRef(true);
  const submitting = useRef(false);
  const savedProfile = useRef<{ fingerprint: string; session: string | null } | null>(null);
  const originalDepartment = useRef<string | null>(null);
  const unresolved = useRef<StaffSaveDraft | null>(null);
  const [unknownSave, setUnknownSave] = useState<StaffSaveDraft | null>(null);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    let mounted = true;
    const sessionToken = getSessionKey();
    Promise.all([organizationApi.departments(!staffId), staffId ? organizationApi.staffMember(staffId) : Promise.resolve(null)]).then(([departmentList, staff]) => {
      if (!mounted || !isRequestSessionCurrent(sessionToken)) return;
      setDepartments(departmentList.filter((department) => department.active || department.departmentId === staff?.departmentId));
      if (staff) {
        originalDepartment.current = staff.departmentId;
        setFullName(staff.fullName);
        setIdentityNumber(staff.identityNumber ?? "");
        setOriginalIdentity(staff.identityNumber ?? "");
        setDepartmentId(staff.departmentId);
        setJobTitle(staff.jobTitle);
        setSpecialization(staff.specialization ?? "");
        setLicenseNumber(staff.licenseNumber ?? "");
        setPhoneNumber(staff.phoneNumber ?? "");
        setEmail(staff.email ?? "");
        setOriginalEmail(staff.email);
        setProfile({ dateOfBirth: staff.dateOfBirth ?? null, gender: staff.gender ?? null, address: staff.address ?? null, hireDate: staff.hireDate ?? null, qualification: staff.qualification ?? null });
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
    setOriginalIdentity(staff.identityNumber ?? "");
    setIdentityChangeReason("");
    setJobTitle(staff.jobTitle);
    setSpecialization(staff.specialization ?? "");
    setLicenseNumber(staff.licenseNumber ?? "");
    setPhoneNumber(staff.phoneNumber ?? "");
    setEmail(staff.email ?? "");
    setOriginalEmail(staff.email);
        setProfile({ dateOfBirth: staff.dateOfBirth ?? null, gender: staff.gender ?? null, address: staff.address ?? null, hireDate: staff.hireDate ?? null, qualification: staff.qualification ?? null });
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current) return;
    if (jobTitle === "DOCTOR" && !licenseNumber.trim()) {
      setError("Bác sĩ phải có số giấy phép hành nghề.");
      return;
    }
    if ((!editing || identityNumber.trim() !== originalIdentity) && !/^[0-9]{12}$/.test(identityNumber.trim())) {
      setError("CCCD phải gồm đúng 12 chữ số.");
      return;
    }
    if (editing && identityNumber.trim() !== originalIdentity && !identityChangeReason.trim()) {
      setError("Vui lòng nhập lý do thay đổi CCCD."); return;
    }
    const today = new Date();
    const localToday = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;
    if (profile.dateOfBirth && profile.dateOfBirth >= localToday) { setError("Ngày sinh phải trước ngày hiện tại."); return; }
    if (profile.dateOfBirth && profile.hireDate && profile.hireDate <= profile.dateOfBirth) { setError("Ngày vào làm phải sau ngày sinh."); return; }
    const normalizedEmail = email.trim().toLowerCase();
    if ((!editing || Boolean(originalEmail?.trim())) && !normalizedEmail) {
      setError("Email là bắt buộc."); return;
    }
    if (normalizedEmail && (normalizedEmail.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail))) {
      setError("Email không đúng định dạng."); return;
    }
    submitting.current = true;
    setSaving(true); setError(null);
    const sessionToken = getSessionKey();
    // Capture the user's transfer intent before a save/read refreshes the baseline.
    const wantsTransfer = departmentId !== originalDepartment.current;
    const shared = { fullName: fullName.trim(), jobTitle, specialization: specialization.trim() || null, licenseNumber: licenseNumber.trim() || null, phoneNumber: phoneNumber.trim() || null, email: normalizedEmail || null, profile: { ...profile, address: profile.address?.trim() || null, qualification: profile.qualification?.trim() || null }, ...(editing && identityNumber.trim() !== originalIdentity ? { identityNumber: identityNumber.trim(), identityChangeReason: identityChangeReason.trim() } : {}) };
    let creationIntent: Awaited<ReturnType<typeof prepareCreation>> | undefined;
    let completed = false;
    let profileWriteSent = false;
    try {
      if (staffId) {
        const fingerprint = JSON.stringify(shared);
        // Retry a partially completed transfer against a fresh authoritative read.
        // Do not repeat the successful profile command when its fields are unchanged.
        const profileAlreadySaved = savedProfile.current?.fingerprint === fingerprint && savedProfile.current.session === sessionToken;
        profileWriteSent = !profileAlreadySaved;
        const updatedStaff = profileAlreadySaved ? await organizationApi.staffMember(staffId)
          : await organizationApi.updateStaff(staffId, shared);
        if (!mounted.current || getSessionKey() !== sessionToken) return;
        savedProfile.current = { fingerprint, session: sessionToken };
        applyStaff(updatedStaff);
        if (wantsTransfer && departmentId !== updatedStaff.departmentId) {
          const requestedDepartmentId = departmentId;
          try {
            const movedStaff = await organizationApi.changeStaffDepartment(staffId, requestedDepartmentId);
            if (!mounted.current || getSessionKey() !== sessionToken) return;
            applyStaff(movedStaff);
            setDepartmentId(movedStaff.departmentId);
            markSaved(); router.push(`/organization/staff/${movedStaff.staffId}?notice=updated`);
            completed = true;
            return;
          } catch (transferCause: unknown) {
            if (!mounted.current || !isRequestSessionCurrent(sessionToken, transferCause)) return;
            if (transferCause instanceof ApiRequestError && transferCause.status === 401) {
              router.replace("/login");
              return;
            }
            const draft: StaffSaveDraft = { body: shared, departmentId: requestedDepartmentId, transferRequested: true, stage: "transfer" };
            const uncertain = isSaveOutcomeUnknown(transferCause);
            if (uncertain) { unresolved.current = draft; setUnknownSave(draft); markDirty(); }

            try {
              const currentStaff = await organizationApi.staffMember(staffId);
              if (!mounted.current || getSessionKey() !== sessionToken) return;
              if (currentStaff.departmentId === requestedDepartmentId && (!uncertain || staffMatchesDraft(currentStaff, draft))) {
                applyStaff(currentStaff);
                setDepartmentId(currentStaff.departmentId);
                markSaved(); router.push(`/organization/staff/${currentStaff.staffId}?notice=updated`);
                completed = true;
                return;
              }
              if (uncertain) {
                setError("Thông tin nhân sự đã được lưu, nhưng chưa xác định kết quả chuyển khoa. Bản nháp được giữ nguyên; hãy đối soát trước khi gửi lệnh mới.");
                return;
              }
              applyStaff(currentStaff);
              setDepartmentId(requestedDepartmentId);
              setError(`Thông tin nhân sự đã được lưu, nhưng chưa thể xác nhận việc chuyển khoa: ${transferCause instanceof Error ? transferCause.message : "Lỗi không xác định."} Bạn có thể thử lưu lại để hoàn tất chuyển khoa.`);
            } catch (refreshCause: unknown) {
              if (!mounted.current || !isRequestSessionCurrent(sessionToken, refreshCause)) return;
              if (refreshCause instanceof ApiRequestError && refreshCause.status === 401) {
                router.replace("/login");
                return;
              }
              if (uncertain) {
                setError(`Chưa xác định kết quả chuyển khoa và chưa đọc được hồ sơ hiện tại. ${refreshCause instanceof Error ? refreshCause.message : "Hãy kiểm tra lại kết nối."}`);
                return;
              }
              applyStaff(updatedStaff);
              setDepartmentId(requestedDepartmentId);
              setError(`Thông tin nhân sự đã được lưu, nhưng chưa xác minh được khoa hiện tại sau lỗi chuyển khoa (${transferCause instanceof Error ? transferCause.message : "lỗi không xác định"}). Hãy thử lưu lại để hệ thống xác minh và hoàn tất chuyển khoa. ${refreshCause instanceof Error ? refreshCause.message : "Không thể tải hồ sơ hiện tại."}`);
            }
            return;
          }
        }
        markSaved(); router.push(`/organization/staff/${updatedStaff.staffId}?notice=updated`);
        completed = true;
      } else {
        const body = { ...shared, email: normalizedEmail, departmentId, identityNumber: identityNumber.trim() };
        creationIntent = await prepareCreation("staff", sessionToken, body, body.identityNumber);
        if (!mounted.current || getSessionKey() !== sessionToken) { creationIntent.cancelUnsent(); return; }
        const staff = await organizationApi.createStaff(body, creationIntent.key);
        creationIntent.settle();
        if (!mounted.current || getSessionKey() !== sessionToken) return;
        markSaved(); router.push(`/organization/staff/${staff.staffId}?notice=created`);
        completed = true;
      }
    } catch (cause: unknown) {
      creationIntent?.settle(cause);
      if (!mounted.current || !isRequestSessionCurrent(sessionToken, cause)) return;
      if (cause instanceof ApiRequestError && cause.status === 401) {
        router.replace("/login");
      } else if (staffId && profileWriteSent && isSaveOutcomeUnknown(cause)) {
        const draft: StaffSaveDraft = { body: shared, departmentId, transferRequested: wantsTransfer, stage: "profile" };
        unresolved.current = draft; setUnknownSave(draft); markDirty();
        setError(`Chưa xác định kết quả lưu thông tin nhân sự. ${cause instanceof Error ? cause.message : "Kết nối bị gián đoạn."}`);
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
        submitting.current = unresolved.current !== null;
        if (mounted.current && getSessionKey() === sessionToken) setSaving(false);
      }
    }
  }

  const currentLoadError = loadError;
  if (currentLoadError && currentLoadError.key === (staffId ?? "new")) {
    const message = currentLoadError.message;
    return <AsyncState kind="error" message={localize(message)} onRetry={() => { setLoading(true); setLoadError(null); setRetryToken((value) => value + 1); }} />;
  }
  if (loading || loadedStaffId !== (staffId ?? "new")) return <AsyncState kind="loading" message={localize("Đang tải dữ liệu nhân sự…")} />;
  return <form onChangeCapture={markDirty} onSubmit={submit} className="mt-6 max-w-5xl space-y-5 [&_input]:scroll-mb-32 [&_select]:scroll-mb-32 [&_textarea]:scroll-mb-32">
    <L10nValue>{error ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger"><L10nValue>{error}</L10nValue></p> : null}</L10nValue>
    <p className="text-sm text-muted-foreground"><L10n>{"Các trường có dấu * là bắt buộc."}</L10n></p>
    <L10nValue>{unknownSave && staffId ? <SaveOutcomeRecovery read={() => organizationApi.staffMember(staffId)}
      matches={staff => staffMatchesDraft(staff, unknownSave)} onUnauthorized={() => router.replace("/login")}
      onVerified={staff => {
        savedProfile.current = { fingerprint: JSON.stringify(unknownSave.body), session: getSessionKey() };
        applyStaff(staff);
        if (unknownSave.stage === "profile" && unknownSave.transferRequested && staff.departmentId !== unknownSave.departmentId) {
          setDepartmentId(unknownSave.departmentId);
          unresolved.current = null; setUnknownSave(null); submitting.current = false;
          setError("Đã xác nhận thông tin nhân sự được lưu. Lệnh chuyển khoa chưa được gửi; chọn Lưu thay đổi để tiếp tục.");
        } else {
          markSaved(); router.push(`/organization/staff/${staff.staffId}?notice=updated`);
        }
      }} /> : null}</L10nValue>
    <fieldset disabled={saving || Boolean(unknownSave)} className="min-w-0 space-y-5">
      <FormSection title={localize("Thông tin định danh")} description={localize("Họ tên và CCCD dùng để xác định hồ sơ nhân sự.")}>
        <Field label={localize("Họ tên")} htmlFor="staff-name" required><input id="staff-name" required maxLength={100} value={fullName} onChange={(event) => setFullName(event.target.value)} className={controlClassName} /></Field>
        <Field label={localize("CCCD")} htmlFor="staff-identity" required={!editing} hint="Nhập đủ 12 chữ số. Thay đổi CCCD không làm đổi email đăng nhập."><input id="staff-identity" required={!editing} aria-describedby="staff-identity-hint" autoComplete="off" inputMode="numeric" pattern="[0-9]{12}" maxLength={12} value={identityNumber} placeholder={localize(editing && !identityNumber ? "Chưa có CCCD" : undefined)} onChange={(event) => setIdentityNumber(event.target.value)} className={controlClassName} /></Field>
        <L10nValue>{editing && identityNumber.trim() !== originalIdentity ? <Field label={localize("Lý do thay đổi CCCD")} htmlFor="staff-identity-reason" required><textarea id="staff-identity-reason" required maxLength={500} rows={3} value={identityChangeReason} onChange={event => setIdentityChangeReason(event.target.value)} className={controlClassName} /></Field> : null}</L10nValue>
      </FormSection>
      <FormSection title={localize("Thông tin cá nhân bổ sung")} description={localize("Thông tin tùy chọn, chỉ admin và chính nhân viên được xem.")}>
        <Field label={localize("Ngày sinh")} htmlFor="staff-birth"><input id="staff-birth" type="date" value={profile.dateOfBirth ?? ""} onChange={event => setProfile(previous => ({ ...previous, dateOfBirth: event.target.value || null }))} className={controlClassName} /></Field>
        <Field label={localize("Giới tính")} htmlFor="staff-gender"><select id="staff-gender" value={profile.gender ?? ""} onChange={event => setProfile(previous => ({ ...previous, gender: (event.target.value || null) as StaffProfile["gender"] }))} className={controlClassName}><option value=""><L10n>{"Chưa cập nhật"}</L10n></option><option value="MALE"><L10n>{"Nam"}</L10n></option><option value="FEMALE"><L10n>{"Nữ"}</L10n></option><option value="OTHER"><L10n>{"Khác"}</L10n></option></select></Field>
        <Field label={localize("Địa chỉ liên hệ")} htmlFor="staff-address"><input id="staff-address" maxLength={255} value={profile.address ?? ""} onChange={event => setProfile(previous => ({ ...previous, address: event.target.value || null }))} className={controlClassName} /></Field>
      </FormSection>
      <FormSection title={localize("Phân công & chuyên môn")} description={localize("Khoa công tác, chức danh và thông tin hành nghề.")}>
        <Field label={localize("Đơn vị công tác")} htmlFor="staff-department" required><select id="staff-department" required value={departmentId} onChange={(event) => setDepartmentId(event.target.value)} className={controlClassName}><option value=""><L10n>{"Chọn khoa/phòng"}</L10n></option><L10nValue>{departments.map((department) => <option key={department.departmentId} value={department.departmentId}>{department.departmentName}<L10nValue>{!department.active ? " (ngừng hoạt động)" : ""}</L10nValue></option>)}</L10nValue></select></Field>
        <Field label={localize("Chức danh")} htmlFor="staff-job" required><select id="staff-job" value={jobTitle} onChange={(event) => setJobTitle(event.target.value as JobTitle)} className={controlClassName}><L10nValue>{jobs.map((job) => <option key={job.value} value={job.value}><L10nValue>{job.label}</L10nValue></option>)}</L10nValue></select></Field>
        <Field label={localize("Ngày vào làm")} htmlFor="staff-hired"><input id="staff-hired" type="date" value={profile.hireDate ?? ""} onChange={event => setProfile(previous => ({ ...previous, hireDate: event.target.value || null }))} className={controlClassName} /></Field>
        <Field label={localize("Trình độ chuyên môn")} htmlFor="staff-qualification"><input id="staff-qualification" maxLength={100} value={profile.qualification ?? ""} onChange={event => setProfile(previous => ({ ...previous, qualification: event.target.value || null }))} className={controlClassName} /></Field>
        <Field label={localize("Chuyên môn")} htmlFor="staff-specialization"><input id="staff-specialization" maxLength={100} value={specialization} onChange={(event) => setSpecialization(event.target.value)} className={controlClassName} /></Field>
        <Field label={localize("Số giấy phép")} htmlFor="staff-license" required={jobTitle === "DOCTOR"} hint={jobTitle === "DOCTOR" ? "Bắt buộc đối với bác sĩ." : undefined}><input id="staff-license" required={jobTitle === "DOCTOR"} maxLength={50} value={licenseNumber} onChange={(event) => setLicenseNumber(event.target.value)} className={controlClassName} /></Field>
      </FormSection>
      <FormSection title={localize("Thông tin liên hệ")} description={localize("Thông tin liên lạc của nhân sự.")}>
        <Field label={localize("Số điện thoại")} htmlFor="staff-phone" hint="10 đến 15 chữ số."><input id="staff-phone" type="tel" autoComplete="tel" pattern="[0-9]{10,15}" value={phoneNumber} onChange={(event) => setPhoneNumber(event.target.value)} className={controlClassName} /></Field>
        <Field label={localize("Email")} htmlFor="staff-email" required={!editing || Boolean(originalEmail?.trim())} hint="Sau khi lưu hồ sơ, quản lý tài khoản và email đăng nhập tại chi tiết hồ sơ nhân sự."><input id="staff-email" type="email" autoComplete="email" required={!editing || Boolean(originalEmail?.trim())} maxLength={254} value={email} onChange={(event) => setEmail(event.target.value)} className={controlClassName} /></Field>
      </FormSection>
    </fieldset>
    <div className="sticky bottom-4 z-10 flex flex-wrap items-center justify-end gap-3 rounded-xl border border-border bg-surface p-4 shadow-lg"><Button type="button" disabled={saving} onClick={() => { if (confirmLeave()) router.back(); }}><L10n>{"Quay lại"}</L10n></Button><Button type="submit" variant="primary" loading={saving} disabled={Boolean(unknownSave)}><L10nValue>{editing ? "Lưu thay đổi" : "Tạo nhân sự"}</L10nValue></Button></div>
  </form>;
}
