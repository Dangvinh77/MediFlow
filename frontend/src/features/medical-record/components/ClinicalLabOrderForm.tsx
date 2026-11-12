"use client";
import { L10n, L10nValue, useLocale } from "@/components/i18n/LocaleProvider";


import { useState, type FormEvent } from "react";
import { Button } from "@/components/ui/Button";
import { Field, controlClassName } from "@/components/ui/Field";
import { CommandFeedback } from "@/components/ui/CommandFeedback";
import { useCommand } from "@/lib/use-command";
import { useUnsavedChanges } from "@/lib/use-unsaved-changes";
import { createPendingLabOrder, hasUnresolvedLabOrder } from "../pending-lab-order";
import type { ClinicalLabOrderDTO } from "../types";

interface CatalogChoice {
  serviceCode: string;
  name: string;
  displayGroup: string;
  specimenType: string;
  preparation: string | null;
  turnaroundHours: number | null;
  priceCode: string;
  panel: boolean;
}

interface ClinicalLabOrderFormProps {
  recordId: string;
  onCreated: (order: ClinicalLabOrderDTO) => void;
  catalog?: CatalogChoice[] | null;
  catalogError?: string | null;
}

const groupLabels: Record<string, string> = {
  HEMATOLOGY: "Huyết học",
  BIOCHEMISTRY: "Sinh hóa",
  IMMUNOLOGY: "Miễn dịch",
  URINALYSIS: "Xét nghiệm nước tiểu",
  MICROBIOLOGY: "Vi sinh",
};

function localToday() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

export function ClinicalLabOrderForm({ recordId, onCreated, catalog, catalogError }: ClinicalLabOrderFormProps) {
  const { localize } = useLocale();
  const command = useCommand();
  const { markDirty, markSaved } = useUnsavedChanges();
  const [labType, setLabType] = useState("");
  const [requestedDate, setRequestedDate] = useState(localToday);
  const [priceCode, setPriceCode] = useState("");
  const [serviceCode, setServiceCode] = useState("");
  const [created, setCreated] = useState(false);
  const [validation, setValidation] = useState<string | null>(null);
  const [restoredUncertain] = useState(() => hasUnresolvedLabOrder(recordId));
  const selectedService = catalog?.find((item) => item.serviceCode === serviceCode) ?? null;

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (created || command.busy || command.uncertain || restoredUncertain) return;
    if ((catalog && !selectedService) || (!catalog && (!labType.trim() || !priceCode.trim())) || !requestedDate || requestedDate > localToday()) {
      setValidation(catalog && !selectedService
        ? "Chọn một dịch vụ xét nghiệm trong danh mục."
        : "Nhập loại xét nghiệm, mã giá và ngày yêu cầu không ở tương lai."); return;
    }
    setValidation(null);
    markDirty();
    const body = {
      labType: selectedService?.name ?? labType.trim(),
      requestedDate,
      priceCode: selectedService?.priceCode ?? priceCode.trim(),
      ...(selectedService ? { serviceCode: selectedService.serviceCode } : {}),
    };
    void command.run(() => createPendingLabOrder(recordId, body),
      (order) => { markSaved(); setCreated(true); onCreated(order); });
  }
  return <form onSubmit={submit} onChange={markDirty} className="space-y-4 rounded-xl border border-border bg-surface p-6">
    <h2 className="text-lg font-semibold"><L10n>{"Chỉ định xét nghiệm"}</L10n></h2>
    <p className="text-sm text-muted-foreground"><L10nValue>{catalog ? "Chọn một dịch vụ trong danh mục để lập chỉ định gắn với hồ sơ đang khám." : "Chỉ định gắn với hồ sơ đang khám. Nhập mã giá xét nghiệm được cơ sở phê duyệt."}</L10nValue></p>
    <CommandFeedback error={command.error} uncertain={command.uncertain} />
    <L10nValue>{catalogError ? <p role="alert" className="text-sm text-danger"><L10n>{"Không tải được danh mục xét nghiệm: "}</L10n><L10nValue>{catalogError}</L10nValue><L10n>{". Bạn vẫn có thể nhập chỉ định thủ công."}</L10n></p> : null}</L10nValue>
    <L10nValue>{validation ? <p role="alert" className="text-sm text-danger"><L10nValue>{validation}</L10nValue></p> : null}</L10nValue>
    <fieldset disabled={command.busy || command.uncertain || restoredUncertain || created} className="grid gap-4 sm:grid-cols-2">
      <L10nValue>{catalog ? <>
        <Field label={localize("Dịch vụ xét nghiệm")} htmlFor="order-service" required>
          <select id="order-service" required className={controlClassName} value={serviceCode} onChange={(event) => { setServiceCode(event.target.value); setValidation(null); }}>
            <option value=""><L10n>{"Chọn dịch vụ xét nghiệm"}</L10n></option>
            <L10nValue>{catalog.length === 0 ? <option value="" disabled><L10n>{"Danh mục hiện chưa có dịch vụ"}</L10n></option> : null}</L10nValue>
            <L10nValue>{Object.entries(catalog.reduce<Record<string, CatalogChoice[]>>((groups, item) => {
              (groups[item.displayGroup] ??= []).push(item);
              return groups;
            }, {})).map(([group, items]) => (
              <optgroup key={group} label={localize(groupLabels[group] ?? group)}>
                <L10nValue>{items.map((item) => <option key={item.serviceCode} value={item.serviceCode}>{item.name}<L10nValue>{item.panel ? " · Bộ xét nghiệm" : ""}</L10nValue></option>)}</L10nValue>
              </optgroup>
            ))}</L10nValue>
          </select>
        </Field>
        <L10nValue>{selectedService ? <div className="space-y-2 rounded-lg border border-border p-4 text-sm sm:col-span-2">
          <p><span className="font-medium"><L10n>{"Bệnh phẩm:"}</L10n></span> {selectedService.specimenType}</p>
          <p><span className="font-medium"><L10n>{"Chuẩn bị:"}</L10n></span> <L10nValue>{selectedService.preparation || "Chưa có thông tin"}</L10nValue></p>
          <p><span className="font-medium"><L10n>{"Thời gian trả kết quả:"}</L10n></span> <L10nValue>{selectedService.turnaroundHours === null ? "Chưa có thông tin" : `${selectedService.turnaroundHours} giờ`}</L10nValue></p>
          <L10nValue>{selectedService.panel ? <p className="text-muted-foreground"><L10n>{"Dịch vụ bộ xét nghiệm được lập thành một chỉ định."}</L10n></p> : null}</L10nValue>
        </div> : null}</L10nValue>
      </> : <>
        <Field label={localize("Loại xét nghiệm")} htmlFor="order-lab-type" required><input id="order-lab-type" required maxLength={50} className={controlClassName} value={labType} onChange={(event) => setLabType(event.target.value)} /></Field>
        <Field label={localize("Mã giá")} htmlFor="order-price" required><input id="order-price" required maxLength={64} className={controlClassName} value={priceCode} onChange={(event) => setPriceCode(event.target.value)} /></Field>
      </>}</L10nValue>
      <Field label={localize("Ngày yêu cầu")} htmlFor="order-date" required><input id="order-date" required type="date" max={localToday()} className={controlClassName} value={requestedDate} onChange={(event) => setRequestedDate(event.target.value)} /></Field>
      <div className="sm:col-span-2"><Button type="submit" variant="primary" loading={command.busy}><L10n>{"Gửi chỉ định xét nghiệm"}</L10n></Button></div>
    </fieldset>
    <L10nValue>{command.uncertain || restoredUncertain ? <p className="text-sm text-warning"><L10n>{"Chưa xác định kết quả lập chỉ định trong hồ sơ. Không lập lại; cần đối soát hồ sơ với bộ phận hỗ trợ trước khi tiếp tục. Lab có thể chưa nhận được yêu cầu."}</L10n></p> : null}</L10nValue>
  </form>;
}
