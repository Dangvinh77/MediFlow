"use client";
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";


import Link from "next/link";
import { ReferenceText } from "@/components/references/ReferenceText";
import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/Button";
import { CommandFeedback } from "@/components/ui/CommandFeedback";
import { useCommand } from "@/lib/use-command";
import { transferLabSource } from "../source-transfer";
import type { CreateLabRequest, LabTestDTO } from "../types";

export function CreateSourceLabRequest({ request, autoSubmit = false, onCompleted }: { request: CreateLabRequest; autoSubmit?: boolean; onCompleted?: () => void }) {
  const command = useCommand();
  const [created, setCreated] = useState<LabTestDTO | null>(null);
  const started = useRef(false);
  useEffect(() => {
    if (created) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ""; };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [created]);
  useEffect(() => {
    if (!autoSubmit || started.current) return;
    started.current = true;
    void command.run(() => transferLabSource(request), value => { setCreated(value); onCompleted?.(); });
  }, [autoSubmit, command, request, onCompleted]);
  return <section className="space-y-4 rounded-xl border border-border bg-surface p-6">
    <h2 className="text-lg font-semibold"><L10n>{"Chuyển chỉ định sang xét nghiệm"}</L10n></h2>
    <L10nValue>{!created ? <p role="status" className="rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm"><L10n>{"Đã lập chỉ định trong hồ sơ. Đang chuyển sang Lab; nếu gặp lỗi, tiếp tục với chỉ định này, không lập lại."}</L10n></p> : null}</L10nValue>
    <p className="text-sm text-muted-foreground">{request.labType} · {request.requestedDate}<L10n>{". Thông tin chỉ định đã được khóa theo hồ sơ khám."}</L10n></p>
    <p><ReferenceText value={request.sourceOrderId} fallback="Chỉ định xét nghiệm đã chọn" /></p>
    <CommandFeedback error={command.error} uncertain={command.uncertain} />
    <L10nValue>{created ? <div role="status" className="space-y-3">
      <p className="text-sm text-success"><L10n>{"Đã tạo yêu cầu xét nghiệm. Hướng dẫn người bệnh tới thu ngân để đối chiếu khoản xét nghiệm."}</L10n></p>
      <p className="text-sm text-muted-foreground"><L10n>{"Thu ngân mở “Thu phí xét nghiệm &amp; thuốc”, tìm bệnh nhân rồi cập nhật hàng chờ. Khoản thu xuất hiện sau khi hệ thống ghi nhận yêu cầu và bảng giá hợp lệ; tạo yêu cầu thành công chưa có nghĩa đã thanh toán. Nếu chưa thấy khoản thu, cần đối soát yêu cầu này, không lập chỉ định trùng."}</L10n></p>
      <div className="flex flex-wrap items-center gap-4"><Link href={`/records/${request.recordId}`} className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"><L10n>{"Quay lại hồ sơ khám"}</L10n></Link><Link href={`/lab/${created.testId}`} className="text-sm font-medium text-primary hover:underline"><L10n>{"Xem yêu cầu xét nghiệm"}</L10n></Link></div>
    </div> : <Button type="button" variant="primary" loading={command.busy} disabled={command.uncertain || command.error?.status === 409} onClick={() => void command.run(() => transferLabSource(request, true), value => { setCreated(value); onCompleted?.(); })}><L10n>{"Tiếp tục chuyển sang Lab"}</L10n></Button>}</L10nValue>
    <L10nValue>{command.uncertain || command.error?.status === 409 ? <p className="text-sm text-warning"><L10n>{"Kiểm tra hàng đợi xét nghiệm bằng mã chỉ định trước khi thao tác lại. Không lập chỉ định mới để thay thế yêu cầu chưa rõ kết quả."}</L10n></p> : null}</L10nValue>
    <L10nValue>{command.uncertain || command.error?.status === 409 ? <Link href={`/lab?episodeId=${encodeURIComponent(request.careEpisodeId ?? "")}`} className="inline-flex min-h-11 items-center text-sm text-primary underline"><L10n>{"Đối chiếu xét nghiệm của lượt khám"}</L10n></Link> : null}</L10nValue>
  </section>;
}
