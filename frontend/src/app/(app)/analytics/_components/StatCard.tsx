"use client";

import { Skeleton } from "@/components/shared/Skeleton";

export function StatCard({ label, value, icon: Icon, isLoading }: {
  label: string;
  value: string | number;
  icon: React.ElementType;
  isLoading: boolean;
}) {
  return (
    <div className="bg-card border border-border rounded-xl p-3 sm:p-5 flex items-center gap-3 sm:gap-4">
      <div className="p-2 bg-primary/10 rounded-lg shrink-0">
        <Icon className="w-5 h-5 text-primary" />
      </div>
      <div className="min-w-0">
        <div className="text-xs text-muted-foreground truncate">{label}</div>
        {isLoading ? (
          <Skeleton className="h-7 w-16 mt-1" />
        ) : (
          <div className="text-xl sm:text-2xl font-bold">{value}</div>
        )}
      </div>
    </div>
  );
}
