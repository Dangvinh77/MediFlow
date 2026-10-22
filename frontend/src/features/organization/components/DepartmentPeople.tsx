"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "@/lib/api";
import { useEffect, useState } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Pagination } from "@/components/ui/Pagination";
import { getSessionKey } from "@/lib/auth";
import { organizationApi } from "../api";
import { isRequestSessionCurrent } from "../request-session";
const jobs = { DOCTOR: "Bác sĩ", NURSE: "Điều dưỡng", TECHNICIAN: "Kỹ thuật viên", PHARMACIST: "Dược sĩ", CASHIER: "Thu ngân", MANAGER: "Quản lý", ADMINISTRATIVE: "Hành chính" };
import type { DepartmentDTO, StaffDTO } from "../types";

export function DepartmentPeople({ department }: { department: DepartmentDTO }) {
  const router = useRouter();
  const [members, setMembers] = useState<StaffDTO[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [page, setPage] = useState(0);
  const unit = department.departmentType === "ADMINISTRATIVE" ? "phòng" : "khoa";
  useEffect(() => {
    let active = true;
    const session = getSessionKey();
    async function load() {
      const people: StaffDTO[] = [];
      for (let index = 0; ; index++) {
        if (!active || !isRequestSessionCurrent(session)) return;
        const batch = await organizationApi.staff({ departmentId: department.departmentId, page: index, size: 100 });
        people.push(...batch.content);
        if (index + 1 >= batch.totalPages) break;
      }
      if (active && isRequestSessionCurrent(session)) setMembers(people);
    }
    load().catch((cause: unknown) => {
      if (!active || !isRequestSessionCurrent(session, cause)) return;
      if (cause instanceof ApiRequestError && cause.status === 401) router.replace("/login");
      else setError("Không thể tải nhân sự và người phụ trách của đơn vị.");
    });
    return () => { active = false; };
  }, [department.departmentId, attempt, router]);
  const head = members?.find(member => member.staffId === department.departmentHeadId);
  return <section className="overflow-hidden rounded-xl border border-border bg-surface">
    <div className="border-b border-border px-5 py-4"><h3 className="font-semibold">{unit === "phòng" ? "Trưởng phòng" : "Trưởng khoa"}</h3>
      {!department.departmentHeadId ? <p className="mt-2 text-sm text-muted-foreground">Chưa phân công</p>
        : head ? <div className="mt-2 space-y-1 text-sm"><p className="font-medium">{head.fullName} · {jobs[head.jobTitle]}</p>{head.phoneNumber ? <p>Điện thoại: {head.phoneNumber}</p> : null}{head.email ? <p>Email: {head.email}</p> : null}<Link href={`/organization/staff/${head.staffId}`} className="inline-flex min-h-11 items-center font-semibold text-primary hover:underline">Detail</Link></div>
        : members ? <p className="mt-2 text-sm text-muted-foreground">Chưa có thông tin người phụ trách trong danh sách nhân sự của đơn vị.</p> : null}
    </div>
    {error ? <AsyncState kind="error" message={error} onRetry={() => { setError(null); setMembers(null); setPage(0); setAttempt(value => value + 1); }} />
      : !members ? <AsyncState kind="loading" message="Đang tải nhân sự trực thuộc…" />
      : <>
        <div className="px-5 py-4"><h3 className="font-semibold">Nhân sự trực thuộc</h3><p className="mt-1 text-sm text-muted-foreground">{members.filter(member => member.active).length} đang làm việc · {members.length} nhân sự trong đơn vị</p></div>
        {members.length === 0 ? <AsyncState kind="empty" message="Chưa có nhân sự trực thuộc." /> : <>
          <div className="overflow-x-auto"><table className="w-full text-left text-sm"><caption className="sr-only">Nhân sự trực thuộc</caption><thead className="bg-surface-muted"><tr>{["Họ tên", "Chức danh", "Chuyên môn", "Trạng thái", "Action"].map(label => <th key={label} scope="col" className="px-4 py-3">{label}</th>)}</tr></thead>
            <tbody>{members.slice(page * 20, (page + 1) * 20).map(member => <tr key={member.staffId} className="border-t border-border"><td className="px-4 py-3 font-medium">{member.fullName}</td><td className="px-4 py-3">{jobs[member.jobTitle]}</td><td className="px-4 py-3">{member.specialization || "Chưa cập nhật"}</td><td className="px-4 py-3">{member.active ? "Đang làm việc" : "Đã ngừng làm việc"}</td><td className="px-4 py-3"><Link href={`/organization/staff/${member.staffId}`} aria-label={`Detail ${member.fullName}`} className="inline-flex min-h-11 items-center rounded-lg border border-control-border px-4 py-2 font-semibold hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-primary">Detail</Link></td></tr>)}</tbody></table></div>
          <div className="p-4"><Pagination page={page} totalPages={Math.ceil(members.length / 20)} totalElements={members.length} loading={false} onPageChange={setPage} /></div>
        </>}
      </>}
  </section>;
}
