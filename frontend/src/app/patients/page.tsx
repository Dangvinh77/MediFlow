"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api, ApiRequestError } from "@/lib/api";
import { getRole, isAuthenticated, logout } from "@/lib/auth";
import type { Page, PatientDTO } from "@/lib/types";

export default function PatientsPage() {
  const router = useRouter();
  const [patients, setPatients] = useState<PatientDTO[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [role, setRole] = useState<string | null>(null);

  useEffect(() => {
    if (!isAuthenticated()) {
      router.replace("/login");
      return;
    }
    setRole(getRole());
    api
      .get<Page<PatientDTO>>("/v1/patients?page=0&size=20")
      .then((page) => setPatients(page.content))
      .catch((err) => {
        if (err instanceof ApiRequestError && (err.status === 401 || err.status === 403)) {
          router.replace("/login");
        } else {
          setError(err instanceof Error ? err.message : "Lỗi tải dữ liệu");
        }
      })
      .finally(() => setLoading(false));
  }, [router]);

  function onLogout() {
    logout();
    router.replace("/login");
  }

  return (
    <main className="mx-auto max-w-4xl px-6 py-10">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Bệnh nhân</h1>
        <div className="flex items-center gap-3 text-sm">
          {role && <span className="text-zinc-500">Role: {role}</span>}
          <button onClick={onLogout} className="rounded-lg border border-zinc-300 px-3 py-1.5 dark:border-zinc-700">
            Đăng xuất
          </button>
        </div>
      </div>

      {loading && <p className="mt-6 text-zinc-500">Đang tải…</p>}
      {error && <p className="mt-6 text-red-600">{error}</p>}

      {!loading && !error && (
        <table className="mt-6 w-full border-collapse text-sm">
          <thead>
            <tr className="border-b border-zinc-300 text-left dark:border-zinc-700">
              <th className="py-2 pr-4">Họ tên</th>
              <th className="py-2 pr-4">Ngày sinh</th>
              <th className="py-2 pr-4">Giới tính</th>
              <th className="py-2 pr-4">Số CMND</th>
              <th className="py-2 pr-4">Điện thoại</th>
            </tr>
          </thead>
          <tbody>
            {patients.length === 0 ? (
              <tr>
                <td colSpan={5} className="py-4 text-zinc-500">
                  Chưa có bệnh nhân nào.
                </td>
              </tr>
            ) : (
              patients.map((p) => (
                <tr key={p.maBenhNhan} className="border-b border-zinc-100 dark:border-zinc-800">
                  <td className="py-2 pr-4">{p.hoTen}</td>
                  <td className="py-2 pr-4">{p.ngaySinh}</td>
                  <td className="py-2 pr-4">{p.gioiTinh}</td>
                  <td className="py-2 pr-4">{p.soCmnd}</td>
                  <td className="py-2 pr-4">{p.soDienThoai ?? "—"}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      )}
    </main>
  );
}
