import { isUuid } from "@/lib/validation";
import type { SendNotificationRequest } from "./types";

export type SendNotificationValues = Omit<SendNotificationRequest, "recipientAddress"> & { recipientAddress: string };
export type SendNotificationErrors = Partial<Record<keyof SendNotificationValues, string>>;

export function validateSendNotification(values: SendNotificationValues): SendNotificationErrors {
  const errors: SendNotificationErrors = {};
  if (!isUuid(values.patientId.trim())) errors.patientId = "Mã bệnh nhân phải là UUID hợp lệ.";
  if (!values.title.trim() || values.title.trim().length > 255) errors.title = "Nhập tiêu đề, tối đa 255 ký tự.";
  if (!values.content.trim()) errors.content = "Nhập nội dung thông báo.";
  const address = values.recipientAddress.trim();
  if (!["IN_APP", "EMAIL", "SMS"].includes(values.channel)) errors.channel = "Chọn kênh gửi hợp lệ.";
  // Match the public backend's address rules, including its domestic SMS format.
  if (values.channel === "EMAIL" && (!/^[\w.+-]+@[\w-]+\.[a-zA-Z]{2,}$/.test(address) || address.length > 150)) {
    errors.recipientAddress = "Nhập email hợp lệ, tối đa 150 ký tự.";
  }
  if (values.channel === "SMS" && !/^0\d{9,10}$/.test(address)) errors.recipientAddress = "Số điện thoại phải bắt đầu bằng 0 và có 10–11 chữ số.";
  return errors;
}

export function toSendNotificationRequest(values: SendNotificationValues): SendNotificationRequest {
  if (Object.keys(validateSendNotification(values)).length) throw new Error("Thông tin thông báo chưa hợp lệ.");
  return {
    patientId: values.patientId.trim(), title: values.title.trim(), content: values.content.trim(),
    channel: values.channel, recipientAddress: values.channel === "IN_APP" ? null : values.recipientAddress.trim(),
  };
}
