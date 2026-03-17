"use client";

import { formatDistanceToNow } from "date-fns";
import { Skeleton } from "@/components/shared/Skeleton";

interface StalePR {
  id: string | number;
  prNumber: number;
  title: string;
  createdAt: string;
}

interface Props {
  stalePRs: StalePR[] | undefined;
  isLoading: boolean;
}

export function StalePRsSection({ stalePRs, isLoading }: Props) {
  return (
    <div className="bg-card border border-border rounded-xl p-6">
      <h2 className="font-semibold mb-4">Stale PRs (open &gt;7 days)</h2>
      {isLoading ? (
        <div className="space-y-2">
          {[...Array(3)].map((_, i) => <Skeleton key={i} className="h-12" />)}
        </div>
      ) : !stalePRs?.length ? (
        <div className="py-6 text-center text-muted-foreground text-sm">
          <p className="text-green-500 font-medium">All clear!</p>
          <p>No PRs have been open for more than 7 days.</p>
        </div>
      ) : (
        <div className="space-y-2">
          {stalePRs.map((pr) => (
            <div key={pr.id} className="flex justify-between items-center p-3 bg-orange-50 border border-orange-200 rounded-lg dark:bg-orange-950/20 dark:border-orange-900/40">
              <span className="text-sm font-medium">#{pr.prNumber} {pr.title}</span>
              <span className="text-xs text-orange-600 shrink-0 ml-4">
                {formatDistanceToNow(new Date(pr.createdAt), { addSuffix: true })}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
