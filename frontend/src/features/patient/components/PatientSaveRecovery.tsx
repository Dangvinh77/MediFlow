"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/Button";
import { getSessionKey } from "@/lib/session";
import { patientApi } from "../api";
import { patientToDraft, patientUpdateBody, type PatientDraft } from "../logic";
import type { PatientDTO } from "../types";

/** Read-only reconciliation. Absence or different bytes never unlock a write with an unknown result. */
export function PatientSaveRecovery({ patientId, draft, onVerified }: {
  patientId?: string; draft: PatientDraft; onVerified: (patient: PatientDTO) => void;
}) {
  const alive = useRef(true);
  const lock = useRef(false);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<PatientDTO | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  const matches = result && JSON.stringify(patientUpdateBody(patientToDraft(result))) === JSON.stringify(patientUpdateBody(draft));

  async function check() {
    if (lock.current) return;
    const session = getSessionKey();
    lock.current = true; setBusy(true); setResult(null); setMessage(null);
    try {
      let found: PatientDTO | null = null;
      if (patientId) found = await patientApi.getById(patientId);
      else {
        for (let page = 0; ; page++) {
          if (!alive.current || getSessionKey() !== session) return;
          const batch = await patientApi.search({ keyword: draft.soCmnd.trim(), page, size: 100 });
          found = batch.content.find(patient => patient.soCmnd === draft.soCmnd.trim()) ?? null;
          if (found || page + 1 >= batch.totalPages) break;
        }
      }
      if (!alive.current || getSessionKey() !== session) return;
      setResult(found);
      if (!found) setMessage("Chưa tìm thấy hồ sơ có đúng CMND/CCCD. Kết quả này chưa chứng minh yêu cầu trước đã thất bại. Bạn có thể kiểm tra lại; bản nháp vẫn được giữ và chưa gửi lại.");
    } catch (cause) {
      if (alive.current && getSessionKey() === session) setMessage(cause instanceof Error ? cause.message : "Chưa kiểm tra được hồ sơ.");
    } finally {
      lock.current = false;
      if (alive.current && getSessionKey() === session) setBusy(false);
    }
  }

  return <section aria-label="Đối soát kết quả lưu" className="space-y-3 rounded-xl border border-warning/40 bg-surface p-5">
    <h2 className="font-semibold">Kiểm tra hồ sơ sau khi mất phản hồi</h2>
    <p className="text-sm text-muted-foreground">Bản nháp vẫn được giữ. Kiểm tra chỉ đọc dữ liệu hiện tại, không gửi lại yêu cầu lưu.</p>
    <Button type="button" loading={busy} onClick={() => void check()}>Kiểm tra hồ sơ đã lưu</Button>
    {message ? <p role="status" className="text-sm">{message}</p> : null}
    {result ? <div className="space-y-3 text-sm">
      <p role="status">{matches ? "Hồ sơ hiện tại khớp toàn bộ thông tin bạn gửi." : "Đã tìm thấy hồ sơ nhưng thông tin hiện tại khác bản nháp. Cần kiểm tra trước khi thực hiện yêu cầu mới."}</p>
      <dl className="grid gap-2 sm:grid-cols-2">{Object.entries(patientToDraft(result)).map(([field, value]) => <div key={field}><dt className="font-semibold">{({ hoTen: "Họ tên", ngaySinh: "Ngày sinh", gioiTinh: "Giới tính", soCmnd: "CMND/CCCD", diaChi: "Địa chỉ", soDienThoai: "Điện thoại", email: "Email", bhytSo: "BHYT" } as Record<string, string>)[field]}</dt><dd className="break-words">{field === "gioiTinh" ? value === "M" ? "Nam" : "Nữ" : value || "Chưa cập nhật"}</dd></div>)}</dl>
      {matches ? <Button type="button" onClick={() => onVerified(result)}>Mở hồ sơ đã đối chiếu</Button>
        : <Link href={`/patients/${encodeURIComponent(result.maBenhNhan)}`} target="_blank" rel="noopener noreferrer" className="inline-flex min-h-11 items-center text-primary underline">Mở hồ sơ để kiểm tra ở tab mới</Link>}
    </div> : null}
  </section>;
}
