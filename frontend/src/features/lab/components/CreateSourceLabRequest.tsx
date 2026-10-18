"use client";

import Link from "next/link";
import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { CommandFeedback } from "@/components/ui/CommandFeedback";
import { useCommand } from "@/lib/use-command";
import { labApi } from "../api";
import type { CreateLabRequest, LabTestDTO } from "../types";

export function CreateSourceLabRequest({ request }: { request: CreateLabRequest }) {
  const command = useCommand();
  const [created, setCreated] = useState<LabTestDTO | null>(null);
  return <section className="space-y-4 rounded-xl border border-border bg-surface p-6">
    <h2 className="text-lg font-semibold">2. Gửi yêu cầu xét nghiệm</h2>
    <p className="text-sm text-muted-foreground">{request.labType} · {request.requestedDate}. Thông tin chỉ định đã được khóa theo hồ sơ khám.</p>
    <p className="break-all font-mono text-xs">Mã chỉ định: {request.sourceOrderId}</p>
    <CommandFeedback error={command.error} uncertain={command.uncertain} />
    {created ? <div role="status" className="space-y-3">
      <p className="text-sm text-success">Đã tạo yêu cầu xét nghiệm. Thanh toán và điều kiện thực hiện được hệ thống cập nhật riêng.</p>
      <Link href={`/lab/${created.testId}`} className="inline-flex min-h-11 items-center rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground">Mở xét nghiệm</Link>
    </div> : <Button type="button" variant="primary" loading={command.busy} disabled={command.uncertain || command.error?.status === 409} onClick={() => void command.run(() => labApi.create(request), setCreated)}>Tạo yêu cầu từ chỉ định</Button>}
    {command.uncertain || command.error?.status === 409 ? <p className="text-sm text-warning">Kiểm tra hàng đợi xét nghiệm bằng mã chỉ định trước khi thao tác lại. Không lập chỉ định mới để thay thế yêu cầu chưa rõ kết quả.</p> : null}
    <Link href="/lab" className="inline-flex min-h-11 items-center text-sm text-primary underline">Mở hàng đợi xét nghiệm</Link>
  </section>;
}
