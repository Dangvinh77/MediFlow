import { MediFlowLoader } from "@mediflow/loader";

export default function Loading() {
  return (
    <div className="flex min-h-[40vh] items-center justify-center" aria-busy="true">
      <MediFlowLoader
        className="flex flex-col items-center gap-2 text-sm"
        holdDuration={0.6}
        label="Loading MediFlow..."
        size={72}
        slideDuration={1}
        speed={1.5}
        stagger={0.1}
      />
    </div>
  );
}
