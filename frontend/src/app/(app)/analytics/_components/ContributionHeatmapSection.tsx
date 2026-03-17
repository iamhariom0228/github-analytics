"use client";

import { ContributionHeatmap } from "@/components/charts/ContributionHeatmap";
import { Skeleton } from "@/components/shared/Skeleton";
import type React from "react";

type HeatmapData = React.ComponentProps<typeof ContributionHeatmap>["data"];

interface Props {
  data: HeatmapData | undefined;
  isLoading: boolean;
}

export function ContributionHeatmapSection({ data, isLoading }: Props) {
  return (
    <div className="bg-card border border-border rounded-xl p-6">
      <h2 className="font-semibold mb-4">Contribution Heatmap (Hour × Day of Week)</h2>
      {isLoading ? (
        <Skeleton className="h-40" />
      ) : (data ?? []).length === 0 ? (
        <div className="h-40 flex items-center justify-center text-muted-foreground text-sm">
          No commits yet — sync a repository to see your coding patterns
        </div>
      ) : (
        <ContributionHeatmap data={data ?? []} />
      )}
    </div>
  );
}
