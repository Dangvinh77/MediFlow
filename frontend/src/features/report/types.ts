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

/** OutpatientStatisticsDTO: provisional (not reconciled) outpatient operations from Report projections. */
export interface OutpatientStatisticsFigures {
  completedVisits: number;
  examRevenue: number;
  labRevenue: number;
  prescriptionRevenue: number;
  unclassifiedRevenue: number;
  refunds: number;
}

export interface OutpatientStatisticsDTO {
  fromDate: string;
  toDate: string;
  departmentId: string | null;
  currency: string;
  zoneId: string;
  reconciled: boolean;
  totals: OutpatientStatisticsFigures;
  days: (OutpatientStatisticsFigures & { date: string })[];
  departments: (OutpatientStatisticsFigures & { departmentId: string })[];
}
