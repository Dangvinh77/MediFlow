export interface DailyReportDTO {
  reportDate: string;
  departmentId: string | null;
  visitCount: number;
  labCount: number;
  prescriptionCount: number;
  revenue: number;
}

export interface MonthlyReportDTO {
  month: number;
  year: number;
  departmentId: string | null;
  totalRevenue: number;
  invoiceCount: number;
  dailyDetails: DailyReportDTO[];
}

export interface TopMedicineDTO {
  drugId: string;
  drugName: string;
  totalQuantity: number;
}
