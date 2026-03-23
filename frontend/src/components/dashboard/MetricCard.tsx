import { cn } from "@/lib/utils";

interface MetricCardProps {
  label: string;
  value: string | number;
  sublabel?: string;
  className?: string;
}

export function MetricCard({ label, value, sublabel, className }: MetricCardProps) {
  return (
    <div className={cn("bg-card border border-border rounded-xl p-4 sm:p-6", className)}>
      <div className="text-sm text-muted-foreground mb-1">{label}</div>
      <div className="text-2xl sm:text-4xl font-bold">{value}</div>
      {sublabel && <div className="text-xs text-muted-foreground mt-2">{sublabel}</div>}
    </div>
  );
}
