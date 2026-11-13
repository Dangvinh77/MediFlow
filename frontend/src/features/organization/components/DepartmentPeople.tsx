"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import Link from "next/link";
import { useRouter } from "next/navigation";
import { ApiRequestError } from "@/lib/api";
import { useEffect, useState } from "react";
import { AsyncState } from "@/components/ui/AsyncState";
import { Pagination } from "@/components/ui/Pagination";
import { getSessionKey } from "@/lib/auth";
import { organizationApi } from "../api";
import { isRequestSessionCurrent } from "../request-session";
const jobs = { DOCTOR: "Bác sĩ", NURSE: "Điều dưỡng", TECHNICIAN: "Kỹ thuật viên", PHARMACIST: "Dược sĩ", CASHIER: "Thu ngân", ADMINISTRATIVE: "Hành chính" };
import type { DepartmentDTO, StaffDTO } from "../types";

export function DepartmentPeople({ department }: { department: DepartmentDTO }) {
  const { localize } = useLocale();
  const router = useRouter();
  const [members, setMembers] = useState<StaffDTO[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [page, setPage] = useState(0);
  const clinical = department.departmentType === "CLINICAL";
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
    <div className="border-b border-border px-5 py-4"><h3 className="font-semibold"><L10nValue>{clinical ? "Trưởng khoa" : "Tổ chức làm việc"}</L10nValue></h3>
      <L10nValue>{!clinical ? <p className="mt-2 text-sm text-muted-foreground"><L10n>{"Bộ phận làm việc chung tại một phòng; không bổ nhiệm trưởng bộ phận. Mọi nhân sự có tài khoản và thao tác trực tiếp trên hệ thống."}</L10n></p>
        : !department.departmentHeadId ? <p className="mt-2 text-sm text-muted-foreground"><L10n>{"Chưa bổ nhiệm. Lịch làm việc của khoa do quản trị viên quản lý."}</L10n></p>
        : head ? <div className="mt-2 space-y-1 text-sm"><p className="font-medium">{head.fullName} · <L10nValue>{jobs[head.jobTitle]}</L10nValue></p><L10nValue>{head.phoneNumber ? <p><L10n>{"Điện thoại: "}</L10n>{head.phoneNumber}</p> : null}</L10nValue><L10nValue>{head.email ? <p>Email: {head.email}</p> : null}</L10nValue><Link href={`/organization/staff/${head.staffId}`} className="inline-flex min-h-11 items-center font-semibold text-primary hover:underline"><L10n>{"Xem chi tiết"}</L10n></Link></div>
        : members ? <p className="mt-2 text-sm text-muted-foreground"><L10n>{"Chưa có thông tin người phụ trách trong danh sách nhân sự của đơn vị."}</L10n></p> : null}</L10nValue>
    </div>
    <L10nValue>{error ? <AsyncState kind="error" message={localize(error)} onRetry={() => { setError(null); setMembers(null); setPage(0); setAttempt(value => value + 1); }} />
      : !members ? <AsyncState kind="loading" message={localize("Đang tải nhân sự trực thuộc…")} />
      : <>
        <div className="px-5 py-4"><h3 className="font-semibold"><L10n>{"Nhân sự trực thuộc"}</L10n></h3><p className="mt-1 text-sm text-muted-foreground">{members.filter(member => member.active).length}<L10n>{" đang làm việc · "}</L10n>{members.length}<L10n>{" nhân sự trong đơn vị"}</L10n></p></div>
        <L10nValue>{members.length === 0 ? <AsyncState kind="empty" message={localize("Chưa có nhân sự trực thuộc.")} /> : <>
          <div className="overflow-x-auto"><table className="w-full text-left text-sm"><caption className="sr-only"><L10n>{"Nhân sự trực thuộc"}</L10n></caption><thead className="text-muted-foreground"><tr><L10nValue>{["Họ tên", "Chức danh", "Chuyên môn", "Trạng thái", "Thao tác"].map(label => <th key={label} scope="col" className="px-4 py-3"><L10nValue>{label}</L10nValue></th>)}</L10nValue></tr></thead>
            <tbody><L10nValue>{members.slice(page * 20, (page + 1) * 20).map(member => <tr key={member.staffId} className="border-t border-border"><td className="px-4 py-3 font-medium">{member.fullName}</td><td className="px-4 py-3"><L10nValue>{jobs[member.jobTitle]}</L10nValue></td><td className="px-4 py-3"><L10nValue>{member.specialization || "Chưa cập nhật"}</L10nValue></td><td className="px-4 py-3"><L10nValue>{member.active ? "Đang làm việc" : "Đã ngừng làm việc"}</L10nValue></td><td className="px-4 py-3"><Link href={`/organization/staff/${member.staffId}`} aria-label={localize(`Xem chi tiết ${member.fullName}`)} className="inline-flex min-h-11 items-center rounded-lg px-2 py-2 font-medium text-primary underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-primary"><L10n>{"Xem chi tiết"}</L10n></Link></td></tr>)}</L10nValue></tbody></table></div>
          <div className="p-4"><Pagination page={page} totalPages={Math.ceil(members.length / 20)} totalElements={members.length} loading={false} onPageChange={setPage} /></div>
        </>}</L10nValue>
      </>}</L10nValue>
  </section>;
}
