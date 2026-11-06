import { MediFlowLoader } from "@mediflow/loader";

export default function Loading() {
  return (
    <div className="flex min-h-[40vh] items-center justify-center" aria-busy="true">
      <MediFlowLoader
        className="flex flex-col items-center gap-2 text-sm"
        label="Đang tải MediFlow..."
        size={72}
      />
    </div>
  );
}
