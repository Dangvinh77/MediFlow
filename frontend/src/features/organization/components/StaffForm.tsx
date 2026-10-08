"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Button } from "@/components/ui/Button";
import { controlClassName, Field } from "@/components/ui/Field";
import { ApiRequestError } from "@/lib/api";
import { organizationApi } from "../api";
import type { DepartmentDTO, JobTitle } from "../types";

interface Props { staffId?: string }
const jobs: { value: JobTitle; label: string }[] = [{ value: "DOCTOR", label: "Bác sĩ" }, { value: "NURSE", label: "Điều dưỡng" }, { value: "TECHNICIAN", label: "Kỹ thuật viên" }, { value: "PHARMACIST", label: "Dược sĩ" }, { value: "CASHIER", label: "Thu ngân" }, { value: "MANAGER", label: "Quản lý" }, { value: "ADMINISTRATIVE", label: "Hành chính" }];

export function StaffForm({ staffId }: Props) {
  const router = useRouter(); const editing = Boolean(staffId);
  const [departments, setDepartments] = useState<DepartmentDTO[]>([]); const [originalDepartmentId, setOriginalDepartmentId] = useState(""); const [departmentId, setDepartmentId] = useState(""); const [fullName, setFullName] = useState(""); const [jobTitle, setJobTitle] = useState<JobTitle>("DOCTOR"); const [specialization, setSpecialization] = useState(""); const [licenseNumber, setLicenseNumber] = useState(""); const [phoneNumber, setPhoneNumber] = useState(""); const [email, setEmail] = useState(""); const [loading, setLoading] = useState(true); const [saving, setSaving] = useState(false); const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    Promise.all([organizationApi.departments(true), staffId ? organizationApi.staffMember(staffId) : Promise.resolve(null)]).then(([departmentList, staff]) => {
      if (!mounted) return; setDepartments(departmentList);
      if (staff) { setFullName(staff.fullName); setDepartmentId(staff.departmentId); setOriginalDepartmentId(staff.departmentId); setJobTitle(staff.jobTitle); setSpecialization(staff.specialization ?? ""); setLicenseNumber(staff.licenseNumber ?? ""); setPhoneNumber(staff.phoneNumber ?? ""); setEmail(staff.email ?? ""); }
    }).catch((cause: unknown) => { if (mounted) setError(cause instanceof Error ? cause.message : "Không thể tải dữ liệu nhân sự."); }).finally(() => { if (mounted) setLoading(false); });
    return () => { mounted = false; };
  }, [staffId]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSaving(true); setError(null);
    const shared = { fullName: fullName.trim(), jobTitle, specialization: specialization.trim() || null, licenseNumber: licenseNumber.trim() || null, phoneNumber: phoneNumber.trim() || null, email: email.trim() || null };
    try {
      let staff;
      if (staffId) { staff = await organizationApi.updateStaff(staffId, shared); if (departmentId !== originalDepartmentId) staff = await organizationApi.changeStaffDepartment(staffId, departmentId); }
      else staff = await organizationApi.createStaff({ ...shared, departmentId });
      router.push(`/organization/staff/${staff.staffId}?notice=${editing ? "updated" : "created"}`);
    } catch (cause: unknown) { if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login"); else setError(cause instanceof Error ? cause.message : "Không thể lưu nhân sự."); }
    finally { setSaving(false); }
  }

  if (loading) return <AsyncState kind="loading" message="Đang tải dữ liệu nhân sự…" />;
  return <form onSubmit={submit} className="mt-6 space-y-6">{error ? <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-4 text-sm text-danger">{error}</p> : null}<div className="grid gap-5 rounded-xl border border-border bg-surface p-6 md:grid-cols-2"><Field label="Họ tên" htmlFor="staff-name" required><input id="staff-name" required maxLength={100} value={fullName} onChange={(event) => setFullName(event.target.value)} className={controlClassName} /></Field><Field label="Khoa" htmlFor="staff-department" required><select id="staff-department" required value={departmentId} onChange={(event) => setDepartmentId(event.target.value)} className={controlClassName}><option value="">Chọn khoa</option>{departments.map((department) => <option key={department.departmentId} value={department.departmentId}>{department.departmentName}</option>)}</select></Field><Field label="Chức danh" htmlFor="staff-job" required><select id="staff-job" value={jobTitle} onChange={(event) => setJobTitle(event.target.value as JobTitle)} className={controlClassName}>{jobs.map((job) => <option key={job.value} value={job.value}>{job.label}</option>)}</select></Field><Field label="Chuyên môn" htmlFor="staff-specialization"><input id="staff-specialization" maxLength={100} value={specialization} onChange={(event) => setSpecialization(event.target.value)} className={controlClassName} /></Field><Field label="Số giấy phép" htmlFor="staff-license"><input id="staff-license" maxLength={50} value={licenseNumber} onChange={(event) => setLicenseNumber(event.target.value)} className={controlClassName} /></Field><Field label="Số điện thoại" htmlFor="staff-phone" hint="10 đến 15 chữ số."><input id="staff-phone" pattern="[0-9]{10,15}" value={phoneNumber} onChange={(event) => setPhoneNumber(event.target.value)} className={controlClassName} /></Field><Field label="Email" htmlFor="staff-email"><input id="staff-email" type="email" maxLength={100} value={email} onChange={(event) => setEmail(event.target.value)} className={controlClassName} /></Field></div><div className="flex gap-2"><Button type="submit" variant="primary" loading={saving}>{editing ? "Lưu thay đổi" : "Tạo nhân sự"}</Button><Button type="button" disabled={saving} onClick={() => router.back()}>Quay lại</Button></div></form>;
}
