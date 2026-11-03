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

/** OutpatientReportDTO and its nested Period from the Report public reader. */
export interface OutpatientReportDTO {
  fromDate: string;
  toDate: string;
  departmentId: string | null;
  currency: string;
  zoneId: string;
  coverageId: string;
  sourceObservedAt: string;
  reconciledAt: string;
  covered: boolean;
  /** Absent on older Report deployments. */
  syntheticData?: boolean;
  periods: OutpatientReportPeriod[];
}

export interface OutpatientReportPeriod {
  fromDate: string;
  toDate: string;
  completedVisits: number;
  labTests: number;
  dispensedPrescriptions: number;
  dispensedUnits: number;
  cashReceived: number;
  refunds: number;
  netCash: number;
}

/** GET outpatient/available-periods; each entry is revalidated by Report. */
export interface OutpatientAvailablePeriodDTO {
  coverageId: string;
  fromDate: string;
  toDate: string;
  departmentId: string | null;
  currency: string;
  zoneId: string;
  sourceObservedAt: string;
  reconciledAt: string;
  syntheticData: boolean;
}
