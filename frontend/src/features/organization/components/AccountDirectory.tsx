"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { getSessionKey } from "@/lib/auth";
import { Button } from "@/components/ui/Button";
import { AsyncState } from "@/components/ui/AsyncState";
import { DataTableShell } from "@/components/ui/DataTableShell";
import { Pagination } from "@/components/ui/Pagination";
import { controlClassName, Field } from "@/components/ui/Field";
import { organizationApi } from "../api";
import type { AccountDTO } from "../types";
import type { PageResult } from "@/lib/types";

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
    <form className="grid gap-3 border-t border-border p-4 sm:grid-cols-4" onSubmit={e => { e.preventDefault(); reload({ keyword, role, active, page: 0 }); }}>
      <Field label="Tên đăng nhập" htmlFor="account-search"><input id="account-search" className={controlClassName} value={keyword} onChange={e => setKeyword(e.target.value)} maxLength={100} /></Field>
      <Field label="Lọc vai trò" htmlFor="account-role-filter"><select id="account-role-filter" className={controlClassName} value={role} onChange={e => setRole(e.target.value)}><option value="">Tất cả</option>{["ADMIN", "MANAGER", "DOCTOR", "NURSE", "PHARMACIST", "CASHIER", "LAB_TECH", "PATIENT", "SYSTEM"].map(value => <option key={value}>{value}</option>)}</select></Field>
      <Field label="Lọc trạng thái" htmlFor="account-status-filter"><select id="account-status-filter" className={controlClassName} value={active} onChange={e => setActive(e.target.value)}><option value="">Tất cả</option><option value="true">Đang hoạt động</option><option value="false">Đã khóa</option></select></Field>
      <Button type="submit" className="self-end">Tìm tài khoản</Button>
    </form>
    {state.error ? <AsyncState kind="error" message={state.error} onRetry={() => reload({})} /> : !state.data ? <AsyncState kind="loading" message="Đang tải tài khoản…" /> : state.data.content.length === 0 ? <AsyncState kind="empty" message="Không tìm thấy tài khoản." /> : <>
      <table className="w-full text-left text-sm"><thead className="bg-surface-muted"><tr>{["Tên đăng nhập", "Vai trò", "Trạng thái", "Action"].map(title => <th scope="col" key={title} className="p-3">{title}</th>)}</tr></thead><tbody>{state.data.content.map(account => <tr key={account.accountId} className="border-t border-border"><td className="p-3">{account.username}</td><td className="p-3">{account.role}</td><td className="p-3">{account.isActive ? "Đang hoạt động" : "Đã khóa"}</td><td className="p-3"><Link href={`/organization/accounts/${account.accountId}`} aria-label={`Detail ${account.username}`} className="inline-flex min-h-11 items-center rounded-lg border border-control-border px-4 py-2 font-semibold hover:bg-surface-muted">Detail</Link></td></tr>)}</tbody></table>
      <div className="p-4"><Pagination loading={false} page={state.data.number} totalPages={state.data.totalPages} totalElements={state.data.totalElements} onPageChange={page => reload({ page })} /></div>
    </>}
  </DataTableShell>;
}
