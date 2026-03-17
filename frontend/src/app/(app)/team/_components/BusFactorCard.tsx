"use client";

import { Skeleton } from "@/components/shared/Skeleton";

interface BusFactor {
  topContributor: string;
  topContributorPercentage: number;
  totalContributors: number;
}

interface Props {
  busFactor: BusFactor | undefined;
  isLoading: boolean;
}

export function BusFactorCard({ busFactor, isLoading }: Props) {
  if (isLoading) return <Skeleton className="h-28 rounded-xl" />;
  if (!busFactor) return null;

  const pct = busFactor.topContributorPercentage;
  const gaugeColor = pct > 70 ? "#ef4444" : pct > 40 ? "#eab308" : "#22c55e";
  const labelColor = pct > 70 ? "text-red-500" : pct > 40 ? "text-yellow-500" : "text-green-500";

  const r = 36;
  const circ = 2 * Math.PI * r;
  const filled = circ * (Math.min(pct, 100) / 100);

  return (
    <div className="bg-card border border-border rounded-xl p-6">
      <div className="flex items-center gap-6 flex-wrap">
        {/* SVG arc gauge — fixed size, no Recharts stretching */}
        <div className="relative flex-shrink-0 w-24 h-24">
          <svg width="96" height="96" viewBox="0 0 96 96" className="-rotate-90">
            <circle cx="48" cy="48" r={r} fill="none" stroke="hsl(var(--muted))" strokeWidth="8" />
            <circle
              cx="48" cy="48" r={r} fill="none"
              stroke={gaugeColor} strokeWidth="8"
              strokeDasharray={`${filled} ${circ}`}
              strokeLinecap="round"
              style={{ transition: "stroke-dasharray 0.5s ease" }}
            />
          </svg>
          <div className="absolute inset-0 flex flex-col items-center justify-center">
            <span className="text-lg font-bold leading-none">{pct.toFixed(0)}%</span>
            <span className="text-[10px] text-muted-foreground mt-0.5">ownership</span>
          </div>
        </div>

        <div className="flex-1 min-w-0">
          <h2 className="font-semibold mb-1">Bus Factor</h2>
          <p className="text-sm text-muted-foreground mb-3">
            <span className="font-medium text-foreground">{busFactor.topContributor}</span> owns{" "}
            {pct.toFixed(0)}% of commits across{" "}
            <span className="font-medium text-foreground">{busFactor.totalContributors}</span>{" "}
            contributor{busFactor.totalContributors !== 1 ? "s" : ""}.
          </p>
          {pct > 70 ? (
            <div className={`text-xs font-medium ${labelColor}`}>⚠ High risk — single point of failure</div>
          ) : pct > 40 ? (
            <div className={`text-xs font-medium ${labelColor}`}>Consider distributing ownership</div>
          ) : (
            <div className={`text-xs font-medium ${labelColor}`}>✓ Healthy ownership distribution</div>
          )}
        </div>
      </div>
    </div>
  );
}
