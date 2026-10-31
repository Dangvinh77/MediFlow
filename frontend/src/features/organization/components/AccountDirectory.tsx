"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { getSessionKey } from "@/lib/auth";
import { Button } from "@/components/ui/Button";
import { AsyncState } from "@/components/ui/AsyncState";
import { DirectorySection as DataTableShell } from "./RecordLayout";
import { Pagination } from "@/components/ui/Pagination";
import { controlClassName, Field } from "@/components/ui/Field";
import { organizationApi } from "../api";
import type { AccountDTO } from "../types";
import type { PageResult } from "@/lib/types";
import { accountRoleLabels } from "../account-roles";
import { ReferenceText } from "@/components/references/ReferenceText";
import { StatusBadge } from "@/components/ui/StatusBadge";

export function AccountDirectory({ revision = 0 }: { revision?: number }) {
  const [keyword, setKeyword] = useState(""), [role, setRole] = useState(""), [active, setActive] = useState("");
  const [request, setRequest] = useState({ keyword: "", role: "", active: "", page: 0, attempt: 0 });
  const [state, setState] = useState<{ data?: PageResult<AccountDTO>; error?: string }>({});
  useEffect(() => {
    let disposed = false;
    const session = getSessionKey();
    organizationApi.accounts({ keyword: request.keyword, role: request.role, active: request.active ? request.active === "true" : undefined, page: request.page })
      .then(data => { if (!disposed && getSessionKey() === session) setState({ data }); })
      .catch(cause => { if (!disposed && getSessionKey() === session) setState({ error: cause instanceof Error ? cause.message : "Không thể tải tài khoản." }); });
    return () => { disposed = true; };
  }, [request, revision]);
  function reload(patch: Partial<typeof request>) { setState({}); setRequest(previous => ({ ...previous, ...patch, attempt: previous.attempt + 1 })); }
  return <DataTableShell title="Danh sách tài khoản" description="Xem chi tiết, quản lý trạng thái và đặt lại mật khẩu." actions={<Link href="/organization/accounts/new" className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">Tạo tài khoản</Link>}>
    <form className="grid gap-3 border-t border-border p-5 lg:grid-cols-[2fr_1fr_1fr_auto] lg:px-6" onSubmit={e => { e.preventDefault(); reload({ keyword, role, active, page: 0 }); }}>
      <Field label="Tên đăng nhập" htmlFor="account-search"><input id="account-search" className={controlClassName} value={keyword} onChange={e => setKeyword(e.target.value)} maxLength={100} /></Field>
      <Field label="Lọc vai trò" htmlFor="account-role-filter"><select id="account-role-filter" className={controlClassName} value={role} onChange={e => setRole(e.target.value)}><option value="">Tất cả vai trò</option>{Object.entries(accountRoleLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></Field>
      <Field label="Lọc trạng thái" htmlFor="account-status-filter"><select id="account-status-filter" className={controlClassName} value={active} onChange={e => setActive(e.target.value)}><option value="">Tất cả</option><option value="true">Đang hoạt động</option><option value="false">Đã khóa</option></select></Field>
      <Button type="submit" className="self-end">Tìm tài khoản</Button>
    </form>
    {state.error ? <AsyncState kind="error" message={state.error} onRetry={() => reload({})} /> : !state.data ? <AsyncState kind="loading" message="Đang tải tài khoản…" /> : state.data.content.length === 0 ? <AsyncState kind="empty" message="Không tìm thấy tài khoản." /> : <>
      <div className="overflow-x-auto border-t border-border"><table className="w-full min-w-2xl text-left text-sm"><caption className="sr-only">Danh sách tài khoản</caption><thead className="text-muted-foreground"><tr>{["Tên đăng nhập", "Hồ sơ liên kết", "Vai trò", "Trạng thái", "Thao tác"].map(title => <th scope="col" key={title} className="px-5 py-3">{title}</th>)}</tr></thead><tbody>{state.data.content.map(account => <tr key={account.accountId} className="border-t border-border"><td className="px-5 py-3">{account.username}</td><td className="px-5 py-3">{account.staffId ? <Link href={`/organization/staff/${encodeURIComponent(account.staffId)}`} className="text-primary underline">Nhân viên: <ReferenceText kind="staff" value={account.staffId} fallback="Xem hồ sơ" /></Link> : account.patientId ? <Link href={`/patients/${encodeURIComponent(account.patientId)}`} className="text-primary underline">Bệnh nhân: <ReferenceText kind="patient" value={account.patientId} fallback="Xem hồ sơ" /></Link> : "Chưa liên kết"}</td><td className="px-5 py-3">{accountRoleLabels[account.role]}</td><td className="px-5 py-3"><StatusBadge tone={account.isActive ? "success" : "neutral"}>{account.isActive ? "Đang hoạt động" : "Đã khóa"}</StatusBadge></td><td className="px-5 py-3"><Link href={`/organization/accounts/${account.accountId}`} aria-label={`Xem chi tiết ${account.username}`} className="inline-flex min-h-11 items-center rounded-lg px-2 py-2 font-medium text-primary underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">Xem chi tiết</Link></td></tr>)}</tbody></table></div>
      <div className="p-4"><Pagination loading={false} page={state.data.number} totalPages={state.data.totalPages} totalElements={state.data.totalElements} onPageChange={page => reload({ page })} /></div>
    </>}
  </DataTableShell>;
}
