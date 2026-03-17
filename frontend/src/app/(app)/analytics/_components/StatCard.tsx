"use client";

import { Skeleton } from "@/components/shared/Skeleton";

export function StatCard({ label, value, icon: Icon, isLoading }: {
  label: string;
  value: string | number;
  icon: React.ElementType;
  isLoading: boolean;
}) {
  return (
    <div className="bg-card border border-border rounded-xl p-5 flex items-center gap-4">
      <div className="p-2 bg-primary/10 rounded-lg">
        <Icon className="w-5 h-5 text-primary" />
      </div>
      <div>
        <div className="text-xs text-muted-foreground">{label}</div>
        {isLoading ? (
          <Skeleton className="h-7 w-16 mt-1" />
        ) : (
          <div className="text-2xl font-bold">{value}</div>
        )}
      </div>
    </div>
  );
}
