import { formatDecimal, formatInstant } from "@/lib/format";
import type { OutpatientQuoteDTO } from "../types";

export function OutpatientQuoteBreakdown({ quote }: { quote: OutpatientQuoteDTO }) {
  return <section aria-label="Báo giá ngoại trú" className="space-y-4 rounded-lg border border-info/30 bg-info/5 p-4">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <h3 className="font-semibold">Báo giá ngoại trú</h3>
        <p className="mt-1 text-xs text-muted-foreground">Hỗ trợ BHYT mô phỏng · giả định cấu hình hiện tại áp dụng cho tất cả mục hợp lệ.</p>

      </div>

    </div>
    <ul className="divide-y divide-border" aria-label="Chi tiết giá và khoản hỗ trợ mô phỏng">
      {quote.lines.map((line, index) => <li key={`${line.sourceId}:${line.priceCode}:${index}`} className="space-y-3 py-3 text-sm">
        <h4 className="font-medium">{line.description}</h4>
        <dl className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <div><dt className="text-xs text-muted-foreground">Số lượng × đơn giá</dt><dd>{formatDecimal(line.quantity)} × {formatDecimal(line.unitPrice)} {quote.currency}</dd></div>
          <div><dt className="text-xs text-muted-foreground">Trước hỗ trợ</dt><dd>{formatDecimal(line.grossAmount)} {quote.currency}</dd></div>
          <div><dt className="text-xs text-muted-foreground">Hỗ trợ BHYT mô phỏng ({formatDecimal(Number(line.supportRate) * 100)}%)</dt><dd>{formatDecimal(line.supportAmount)} {quote.currency}</dd></div>
          <div><dt className="text-xs text-muted-foreground">Người bệnh trả</dt><dd className="font-semibold">{formatDecimal(line.patientPayable)} {quote.currency}</dd></div>
        </dl>
      </li>)}
    </ul>
    <dl className="grid gap-3 text-sm sm:grid-cols-3">
      <div><dt className="text-muted-foreground">Tổng trước hỗ trợ</dt><dd className="font-semibold tabular-nums">{formatDecimal(quote.grossAmount)} {quote.currency}</dd></div>
      <div><dt className="text-muted-foreground">Hỗ trợ BHYT mô phỏng</dt><dd className="font-semibold tabular-nums">{formatDecimal(quote.supportAmount)} {quote.currency}</dd></div>
      <div><dt className="text-muted-foreground">Người bệnh dự kiến trả</dt><dd className="font-semibold tabular-nums">{formatDecimal(quote.patientPayable)} {quote.currency}</dd></div>
    </dl>
    <details className="text-xs text-muted-foreground"><summary className="min-h-8 cursor-pointer">Thông tin đối soát</summary><dl className="space-y-2 break-all pt-2"><div><dt>Mã báo giá</dt><dd className="font-mono">{quote.quoteId}</dd></div><div><dt>Mã yêu cầu</dt><dd className="font-mono">{quote.paymentRequestId}</dd></div>{quote.lines.map((line, index) => <div key={index}><dt>{line.description}</dt><dd>{line.priceCode} · {line.catalogueVersion} · {line.policyVersion} · {line.sourceId}</dd></div>)}</dl></details>
    <p className="text-xs text-muted-foreground">Báo giá hết hạn: {formatInstant(quote.expiresAt)} · Cấp lúc: {formatInstant(quote.issuedAt)}</p>
  </section>;
}
