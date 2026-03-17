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

  return (
    <div className="bg-card border border-border rounded-xl p-6">
      <h2 className="font-semibold mb-2">Bus Factor</h2>
      <p className="text-muted-foreground text-sm mb-3">
        <strong>{busFactor.topContributor}</strong> owns{" "}
        <strong>{busFactor.topContributorPercentage.toFixed(0)}%</strong> of commits
        across {busFactor.totalContributors} contributor{busFactor.totalContributors !== 1 ? "s" : ""}.
      </p>
      <div className="w-full bg-muted rounded-full h-3">
        <div
          className="h-3 rounded-full bg-orange-500 transition-all"
          style={{ width: `${Math.min(busFactor.topContributorPercentage, 100)}%` }}
        />
      </div>
      {busFactor.topContributorPercentage > 50 && (
        <p className="text-orange-600 text-xs mt-2 font-medium">
          High bus factor risk — consider distributing ownership.
        </p>
      )}
    </div>
  );
}
