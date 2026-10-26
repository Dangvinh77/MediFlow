import { formatInstant } from "@/lib/format";
import type { OutpatientQuoteDTO } from "../types";

export function OutpatientQuoteBreakdown({ quote }: { quote: OutpatientQuoteDTO }) {
  return <section aria-label="Báo giá ngoại trú" className="space-y-4 rounded-lg border border-info/30 bg-info/5 p-4">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <h3 className="font-semibold">Báo giá ngoại trú</h3>
        <p className="mt-1 text-xs text-muted-foreground">Hỗ trợ BHYT mô phỏng · giả định cấu hình hiện tại áp dụng cho tất cả mục hợp lệ.</p>
        <p className="mt-1 break-all text-xs text-muted-foreground">Yêu cầu {quote.paymentRequestId} · bệnh nhân {quote.patientId} · {quote.careEpisodeType} {quote.careEpisodeId}</p>
      </div>
      <span className="font-mono text-xs text-muted-foreground">{quote.quoteId}</span>
    </div>
    <div className="overflow-x-auto">
      <table className="w-full min-w-[46rem] text-left text-sm">
        <caption className="sr-only">Chi tiết giá và khoản hỗ trợ mô phỏng</caption>
        <thead className="border-b border-border text-xs text-muted-foreground">
          <tr><th className="py-2 pr-3">Mục phí</th><th className="py-2 pr-3">Số lượng × đơn giá</th><th className="py-2 pr-3">Tạm tính</th><th className="py-2 pr-3">Hỗ trợ mô phỏng</th><th className="py-2">Người bệnh</th></tr>
        </thead>
        <tbody className="divide-y divide-border/70">
          {quote.lines.map((line, index) => <tr key={`${line.sourceId}:${line.priceCode}:${index}`}>
            <td className="py-2 pr-3"><span className="font-medium">{line.description}</span><span className="mt-1 block text-xs text-muted-foreground">{line.sourceType} · {line.priceCode}</span><span className="mt-1 block break-all font-mono text-[11px] text-muted-foreground">Nguồn {line.sourceId} · catalogue {line.catalogueVersion} · policy {line.policyVersion}</span></td>
            <td className="py-2 pr-3 tabular-nums">{line.quantity} × {line.unitPrice} {quote.currency}</td>
            <td className="py-2 pr-3 tabular-nums">{line.grossAmount} {quote.currency}</td>
            <td className="py-2 pr-3 tabular-nums">{line.supportAmount} {quote.currency} <span className="text-xs text-muted-foreground">({line.supportRate})</span></td>
            <td className="py-2 tabular-nums">{line.patientPayable} {quote.currency}</td>
          </tr>)}
        </tbody>
      </table>
    </div>
    <dl className="grid gap-3 text-sm sm:grid-cols-3">
      <div><dt className="text-muted-foreground">Tổng trước hỗ trợ</dt><dd className="font-semibold tabular-nums">{quote.grossAmount} {quote.currency}</dd></div>
      <div><dt className="text-muted-foreground">Hỗ trợ BHYT mô phỏng</dt><dd className="font-semibold tabular-nums">{quote.supportAmount} {quote.currency}</dd></div>
      <div><dt className="text-muted-foreground">Người bệnh dự kiến trả</dt><dd className="font-semibold tabular-nums">{quote.patientPayable} {quote.currency}</dd></div>
    </dl>
    <p className="text-xs text-muted-foreground">Báo giá hết hạn: {formatInstant(quote.expiresAt)} · Cấp lúc: {formatInstant(quote.issuedAt)}</p>
  </section>;
}
