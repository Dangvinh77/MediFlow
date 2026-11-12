
import { L10n, L10nValue } from "@/components/i18n/LocaleProvider";
import { formatDecimal, formatInstant } from "@/lib/format";
import type { OutpatientQuoteDTO } from "../types";

export function OutpatientQuoteBreakdown({ quote }: { quote: OutpatientQuoteDTO }) {
  return <section aria-label="Báo giá ngoại trú" className="space-y-4 rounded-lg border border-info/30 bg-info/5 p-4">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <h3 className="font-semibold"><L10n>{"Báo giá ngoại trú"}</L10n></h3>
        <p className="mt-1 text-xs text-muted-foreground"><L10n>{"Hỗ trợ BHYT mô phỏng · giả định cấu hình hiện tại áp dụng cho tất cả mục hợp lệ."}</L10n></p>

      </div>

    </div>
    <ul className="divide-y divide-border" aria-label="Chi tiết giá và khoản hỗ trợ mô phỏng">
      <L10nValue>{quote.lines.map((line, index) => <li key={`${line.sourceId}:${line.priceCode}:${index}`} className="space-y-3 py-3 text-sm">
        <h4 className="font-medium"><L10nValue>{line.description}</L10nValue></h4>
        <dl className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <div><dt className="text-xs text-muted-foreground"><L10n>{"Số lượng × đơn giá"}</L10n></dt><dd><L10nValue>{formatDecimal(line.quantity)}</L10nValue> × <L10nValue>{formatDecimal(line.unitPrice)}</L10nValue> {quote.currency}</dd></div>
          <div><dt className="text-xs text-muted-foreground"><L10n>{"Trước hỗ trợ"}</L10n></dt><dd><L10nValue>{formatDecimal(line.grossAmount)}</L10nValue> {quote.currency}</dd></div>
          <div><dt className="text-xs text-muted-foreground"><L10n>{"Hỗ trợ BHYT mô phỏng ("}</L10n><L10nValue>{formatDecimal(Number(line.supportRate) * 100)}</L10nValue>%)</dt><dd><L10nValue>{formatDecimal(line.supportAmount)}</L10nValue> {quote.currency}</dd></div>
          <div><dt className="text-xs text-muted-foreground"><L10n>{"Người bệnh trả"}</L10n></dt><dd className="font-semibold"><L10nValue>{formatDecimal(line.patientPayable)}</L10nValue> {quote.currency}</dd></div>
        </dl>
      </li>)}</L10nValue>
    </ul>
    <dl className="grid gap-3 text-sm sm:grid-cols-3">
      <div><dt className="text-muted-foreground"><L10n>{"Tổng trước hỗ trợ"}</L10n></dt><dd className="font-semibold tabular-nums"><L10nValue>{formatDecimal(quote.grossAmount)}</L10nValue> {quote.currency}</dd></div>
      <div><dt className="text-muted-foreground"><L10n>{"Hỗ trợ BHYT mô phỏng"}</L10n></dt><dd className="font-semibold tabular-nums"><L10nValue>{formatDecimal(quote.supportAmount)}</L10nValue> {quote.currency}</dd></div>
      <div><dt className="text-muted-foreground"><L10n>{"Người bệnh dự kiến trả"}</L10n></dt><dd className="font-semibold tabular-nums"><L10nValue>{formatDecimal(quote.patientPayable)}</L10nValue> {quote.currency}</dd></div>
    </dl>
    <details className="text-xs text-muted-foreground"><summary className="min-h-8 cursor-pointer"><L10n>{"Thông tin đối soát"}</L10n></summary><dl className="space-y-2 break-all pt-2"><div><dt><L10n>{"Mã báo giá"}</L10n></dt><dd className="font-mono">{quote.quoteId}</dd></div><div><dt><L10n>{"Mã yêu cầu"}</L10n></dt><dd className="font-mono">{quote.paymentRequestId}</dd></div><L10nValue>{quote.lines.map((line, index) => <div key={index}><dt><L10nValue>{line.description}</L10nValue></dt><dd>{line.priceCode} · {line.catalogueVersion} · {line.policyVersion} · {line.sourceId}</dd></div>)}</L10nValue></dl></details>
    <p className="text-xs text-muted-foreground"><L10n>{"Báo giá hết hạn: "}</L10n><L10nValue>{formatInstant(quote.expiresAt)}</L10nValue><L10n>{" · Cấp lúc: "}</L10n><L10nValue>{formatInstant(quote.issuedAt)}</L10nValue></p>
  </section>;
}
