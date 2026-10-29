"use client";

import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/Button";
import { ApiRequestError } from "@/lib/api";
import { getSessionKey } from "@/lib/auth";
import { isRequestSessionCurrent } from "../request-session";

/** Reads never prove an absent/different result is safe to retry. */
export function SaveOutcomeRecovery<T>({ read, matches, onVerified, onUnauthorized }: {
  read: () => Promise<T | null>;
  matches: (result: T) => boolean;
  onVerified: (result: T) => void;
  onUnauthorized: () => void;
}) {
  const alive = useRef(true);
  const checking = useRef(false);
  const [busy, setBusy] = useState(false);
  const [verified, setVerified] = useState<{ value: T; session: string | null } | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);

  async function check() {
    if (checking.current) return;
    const session = getSessionKey();
    checking.current = true; setBusy(true); setVerified(null); setMessage(null);
    try {
      const current = await read();
      if (!alive.current || getSessionKey() !== session) return;
      if (!current) {
        setMessage("Chưa tìm thấy dữ liệu. Yêu cầu trước có thể vẫn đang xử lý; bản nháp tiếp tục được khóa. Bạn có thể kiểm tra lại.");
      } else if (!matches(current)) {
        setMessage("Thông tin hiện tại khác bản nháp đã gửi. Chưa xác định được kết quả yêu cầu trước; bản nháp tiếp tục được khóa. Hãy kiểm tra lại hoặc liên hệ quản trị viên.");
      } else {
        setVerified({ value: current, session });
        setMessage("Dữ liệu hiện tại khớp toàn bộ thông tin của lệnh cần đối soát.");
      }
    } catch (cause) {
      if (!alive.current || !isRequestSessionCurrent(session, cause)) return;
      if (cause instanceof ApiRequestError && cause.status === 401) onUnauthorized();
      setMessage(`Chưa thể đối soát. Bản nháp tiếp tục được khóa. ${cause instanceof Error ? cause.message : "Hãy kiểm tra lại kết nối."}`);
    } finally {
      checking.current = false;
      if (alive.current && getSessionKey() === session) setBusy(false);
    }
  }

  return <section aria-label="Đối soát kết quả lưu" className="space-y-3 rounded-xl border border-warning/40 bg-surface p-5">
    <h2 className="font-semibold">Chưa xác định kết quả lưu</h2>
    <p className="text-sm text-muted-foreground">Bản nháp đã gửi được giữ nguyên. Kiểm tra chỉ đọc dữ liệu hiện tại, không gửi lại yêu cầu lưu.</p>
    <div className="flex flex-wrap gap-3">
      <Button type="button" loading={busy} onClick={() => void check()}>Kiểm tra dữ liệu đã lưu</Button>
      {verified ? <Button type="button" onClick={() => {
        if (alive.current && getSessionKey() === verified.session) onVerified(verified.value);
      }}>Xác nhận kết quả đã lưu</Button> : null}
    </div>
    {message ? <p role="status" className="text-sm break-words">{message}</p> : null}
  </section>;
}
