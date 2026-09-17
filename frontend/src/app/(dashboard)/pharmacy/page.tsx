import type { Metadata } from "next";
import { redirect } from "next/navigation";

export const metadata: Metadata = {
  title: "Dược | MediFlow",
};

/**
 * Route đầu vào của phân hệ Dược.
 *
 * /pharmacy không có nội dung nghiệp vụ riêng trong phase hiện tại.
 * Khi truy cập, chuyển thẳng sang trang kho thuốc.
 *
 * Redirect thực hiện tại route server, không cần tạo Client Component
 * rồi chờ useEffect để điều hướng.
 */
export default function PharmacyPage() {
  redirect("/pharmacy/drugs");
}