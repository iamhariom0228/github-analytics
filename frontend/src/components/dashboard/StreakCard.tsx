"use client";

import { useStreak } from "@/hooks/useAnalytics";

export function StreakCard() {
  const { data, isLoading } = useStreak();

  return (
    <div className="bg-card border border-border rounded-xl p-6">
      <div className="text-sm text-muted-foreground mb-1">Contribution Streak</div>
      {isLoading ? (
        <div className="h-8 w-16 bg-muted animate-pulse rounded" />
      ) : (
        <div className="flex items-end gap-2">
          <span className="text-4xl font-bold">{data?.currentStreak ?? 0}</span>
          <span className="text-muted-foreground mb-1">days</span>
        </div>
      )}
      {data && (
        <div className="text-xs text-muted-foreground mt-2">
          Longest: {data.longestStreak} days
        </div>
      )}
    </div>
  );
}
