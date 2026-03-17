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

export function LeaderboardTable({ leaderboard, isLoading }: Props) {
  const [search, setSearch] = useState("");

  const filtered = leaderboard?.filter((c) =>
    (c.login ?? "").toLowerCase().includes(search.toLowerCase())
  ) ?? [];

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
        <div className="space-y-2">
          {[...Array(5)].map((_, i) => <Skeleton key={i} className="h-10" />)}
        </div>
      ) : !leaderboard?.length ? (
        <p className="text-muted-foreground text-sm py-4 text-center">No commit data in this period — try a wider date range.</p>
      ) : (
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border text-muted-foreground text-left">
              <th className="py-2 pr-4 w-8">#</th>
              <th className="py-2 pr-4">Contributor</th>
              <th className="py-2 pr-4 text-right">Commits</th>
              <th className="py-2 pr-4 text-right">Lines Added</th>
              <th className="py-2 text-right">Lines Removed</th>
            </tr>
          </thead>
          <tbody>
            {filtered.length === 0 ? (
              <tr>
                <td colSpan={5} className="py-4 text-center text-muted-foreground text-sm">
                  No contributors match &quot;{search}&quot;
                </td>
              </tr>
            ) : (
              filtered.map((c, i) => (
                <tr key={c.login} className="border-b border-border/50 hover:bg-muted/30 transition-colors">
                  <td className="py-2.5 pr-4 text-muted-foreground">{i + 1}</td>
                  <td className="py-2.5 pr-4 font-medium">{c.login ?? "(unknown)"}</td>
                  <td className="py-2.5 pr-4 text-right">{c.commits}</td>
                  <td className="py-2.5 pr-4 text-right text-green-600">+{c.linesAdded.toLocaleString()}</td>
                  <td className="py-2.5 text-right text-red-500">-{c.linesRemoved.toLocaleString()}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      )}
    </div>
  );
}
