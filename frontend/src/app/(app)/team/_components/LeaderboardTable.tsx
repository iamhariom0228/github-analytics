"use client";

import { useState } from "react";
import { Skeleton } from "@/components/shared/Skeleton";

interface Contributor {
  login: string;
  commits: number;
  linesAdded: number;
  linesRemoved: number;
}

interface Props {
  leaderboard: Contributor[] | undefined;
  isLoading: boolean;
}

const ROW_COLORS = ["#6366f1", "#8b5cf6", "#ec4899", "#f59e0b", "#10b981", "#3b82f6", "#f97316", "#14b8a6"];

export function LeaderboardTable({ leaderboard, isLoading }: Props) {
  const [search, setSearch] = useState("");

  const filtered = leaderboard?.filter((c) =>
    (c.login ?? "").toLowerCase().includes(search.toLowerCase())
  ) ?? [];

  const maxCommits = Math.max(...(leaderboard?.map((c) => c.commits) ?? [1]), 1);

  return (
    <div className="bg-card border border-border rounded-xl p-6">
      <div className="flex items-center justify-between mb-4 flex-wrap gap-2">
        <h2 className="font-semibold">Contributor Leaderboard</h2>
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          onKeyDown={(e) => e.stopPropagation()}
          placeholder="Filter by contributor..."
          className={`border border-input rounded-md px-3 py-1.5 text-sm bg-background w-48 ${!leaderboard?.length ? "hidden" : ""}`}
        />
      </div>

      {isLoading ? (
        <div className="space-y-3">
          {[...Array(5)].map((_, i) => <Skeleton key={i} className="h-12" />)}
        </div>
      ) : !leaderboard?.length ? (
        <p className="text-muted-foreground text-sm py-4 text-center">No commit data in this period — try a wider date range.</p>
      ) : filtered.length === 0 ? (
        <p className="text-muted-foreground text-sm py-4 text-center">No contributors match &quot;{search}&quot;</p>
      ) : (
        <div className="space-y-2">
          {filtered.map((c, i) => {
            const barPct = Math.round((c.commits / maxCommits) * 100);
            const color = ROW_COLORS[i % ROW_COLORS.length];
            return (
              <div key={c.login} className="flex items-center gap-3 rounded-lg px-3 py-2.5 hover:bg-muted/40 transition-colors group">
                {/* Rank */}
                <span className="text-xs text-muted-foreground w-5 text-right flex-shrink-0">{i + 1}</span>

                {/* Name + bar */}
                <div className="flex-1 min-w-0 space-y-1">
                  <span className="text-sm font-medium truncate block">{c.login ?? "(unknown)"}</span>
                  <div className="flex items-center gap-2">
                    <div className="flex-1 h-1.5 bg-muted rounded-full overflow-hidden">
                      <div
                        className="h-full rounded-full transition-all duration-500"
                        style={{ width: `${barPct}%`, backgroundColor: color }}
                      />
                    </div>
                    <span className="text-xs font-semibold flex-shrink-0" style={{ color }}>{c.commits} commits</span>
                  </div>
                </div>

                {/* Lines */}
                <div className="hidden sm:flex flex-col items-end gap-0.5 flex-shrink-0 text-xs">
                  <span className="text-green-500 font-medium">+{c.linesAdded.toLocaleString()}</span>
                  <span className="text-red-500 font-medium">-{c.linesRemoved.toLocaleString()}</span>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
