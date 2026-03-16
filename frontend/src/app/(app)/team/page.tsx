"use client";

import { useState } from "react";
import { useRepos } from "@/hooks/useRepos";
import { useLeaderboard, useBusFactor, useStalePRs } from "@/hooks/useAnalytics";
import { DateRangePicker, usePresetDates } from "@/components/shared/DateRangePicker";
import type { DatePreset } from "@/components/shared/DateRangePicker";
import { formatDistanceToNow } from "date-fns";
import { AlertTriangle } from "lucide-react";

function Skeleton({ className = "" }: { className?: string }) {
  return <div className={`animate-pulse bg-muted rounded ${className}`} />;
}

function LeaderboardSearch({ onSearch, hidden }: { onSearch: (v: string) => void; hidden: boolean }) {
  const [value, setValue] = useState("");
  return (
    <input
      value={value}
      onChange={(e) => { setValue(e.target.value); onSearch(e.target.value); }}
      onKeyDown={(e) => e.stopPropagation()}
      placeholder="Filter by contributor..."
      className={`border border-input rounded-md px-3 py-1.5 text-sm bg-background w-48 ${hidden ? "hidden" : ""}`}
    />
  );
}


const STORAGE_KEY = "team:selectedRepoId";

export default function TeamPage() {
  const { data: repos, isLoading: reposLoading } = useRepos();
  const [selectedRepoId, setSelectedRepoId] = useState<string>(
    () => (typeof window !== "undefined" ? localStorage.getItem(STORAGE_KEY) ?? "" : "")
  );
  const [preset, setPreset] = useState<DatePreset>("30d");
  const { from, to } = usePresetDates(preset);

  const repoId = selectedRepoId || repos?.[0]?.id || "";

  const [lbSearch, setLbSearch] = useState("");
  const { data: leaderboard, isLoading: lbLoading } = useLeaderboard(repoId, from, to);

  const { data: busFactor, isLoading: bfLoading } = useBusFactor(repoId);
  const { data: stalePRs, isLoading: staleLoading } = useStalePRs(repoId, 7);

  const filteredLeaderboard = leaderboard?.filter((c) =>
    c.login.toLowerCase().includes(lbSearch.toLowerCase())
  ) ?? [];

  if (reposLoading) {
    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-bold">Team Analytics</h1>
        <Skeleton className="h-32 rounded-xl" />
        <Skeleton className="h-64 rounded-xl" />
      </div>
    );
  }

  if (!repos?.length) {
    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-bold">Team Analytics</h1>
        <div className="text-center py-16 border border-dashed border-border rounded-xl text-muted-foreground">
          <p className="font-medium">No repositories tracked yet</p>
          <p className="text-sm mt-1">Add a repository in the Repositories page to see team analytics</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <h1 className="text-2xl font-bold">Team Analytics</h1>
        <div className="flex items-center gap-3">
          <DateRangePicker value={preset} onChange={(p) => setPreset(p)} />
          <select
            value={repoId}
            onChange={(e) => {
            setSelectedRepoId(e.target.value);
            localStorage.setItem(STORAGE_KEY, e.target.value);
          }}
            className="border border-input rounded-md px-3 py-1.5 text-sm bg-background"
          >
            {repos?.map((r) => (
              <option key={r.id} value={r.id}>{r.fullName}</option>
            ))}
          </select>
        </div>
      </div>

      {/* Stale PR alert banner */}
      {!staleLoading && (stalePRs?.length ?? 0) > 0 && (
        <div className="flex items-center gap-3 bg-orange-500/10 border border-orange-500/30 text-orange-600 rounded-xl px-4 py-3 text-sm">
          <AlertTriangle className="w-4 h-4 shrink-0" />
          <span>
            <strong>{stalePRs!.length} stale PR{stalePRs!.length !== 1 ? "s" : ""}</strong> open for more than 7 days — review needed
          </span>
        </div>
      )}

      {/* Bus factor */}
      {bfLoading ? (
        <Skeleton className="h-28 rounded-xl" />
      ) : busFactor ? (
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
      ) : null}

      {/* Leaderboard */}
      <div className="bg-card border border-border rounded-xl p-6">
        <div className="flex items-center justify-between mb-4 flex-wrap gap-2">
          <h2 className="font-semibold">Contributor Leaderboard</h2>
          <div className="flex items-center gap-2">
            <LeaderboardSearch onSearch={setLbSearch} hidden={!leaderboard?.length} />
          </div>
        </div>
        {lbLoading ? (
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
              {filteredLeaderboard.length === 0 ? (
                <tr>
                  <td colSpan={5} className="py-4 text-center text-muted-foreground text-sm">
                    No contributors match &quot;{lbSearch}&quot;
                  </td>
                </tr>
              ) : (
                filteredLeaderboard.map((c, i) => (
                  <tr key={c.login} className="border-b border-border/50 hover:bg-muted/30 transition-colors">
                    <td className="py-2.5 pr-4 text-muted-foreground">{i + 1}</td>
                    <td className="py-2.5 pr-4 font-medium">{c.login}</td>
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

      {/* Stale PRs detail */}
      <div className="bg-card border border-border rounded-xl p-6">
        <h2 className="font-semibold mb-4">Stale PRs (open &gt;7 days)</h2>
        {staleLoading ? (
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
    </div>
  );
}
