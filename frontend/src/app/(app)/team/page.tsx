"use client";

import { useEffect, useState } from "react";
import { useRepos } from "@/hooks/useRepos";
import { useLeaderboard, useBusFactor, useStalePRs, useChurnLeaderboard } from "@/hooks/useAnalytics";
import { DateRangePicker, usePresetDates, useDatePreset } from "@/components/shared/DateRangePicker";
import { AlertTriangle } from "lucide-react";
import { Skeleton } from "@/components/shared/Skeleton";
import { BusFactorCard } from "./_components/BusFactorCard";
import { LeaderboardTable } from "./_components/LeaderboardTable";
import { StalePRsSection } from "./_components/StalePRsSection";
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, Legend,
} from "recharts";


const STORAGE_KEY = "team:selectedRepoId";

export default function TeamPage() {
  const { data: repos, isLoading: reposLoading } = useRepos();
  const [selectedRepoId, setSelectedRepoId] = useState<string>(
    () => (typeof window !== "undefined" ? localStorage.getItem(STORAGE_KEY) ?? "" : "")
  );
  const [preset, setPreset] = useDatePreset();
  const { from, to } = usePresetDates(preset);

  // If stored repo ID is no longer in the user's repo list (e.g. after switching accounts), reset it
  useEffect(() => {
    if (repos && repos.length > 0 && selectedRepoId && !repos.some((r) => r.id === selectedRepoId)) {
      setSelectedRepoId(repos[0].id);
      localStorage.setItem(STORAGE_KEY, repos[0].id);
    }
  }, [repos, selectedRepoId]);

  const repoId = selectedRepoId || repos?.[0]?.id || "";

  const [leaderboardMode, setLeaderboardMode] = useState<"commits" | "churn">("commits");
  const { data: leaderboard, isLoading: lbLoading } = useLeaderboard(repoId, from, to);
  const { data: churnData, isLoading: churnLoading } = useChurnLeaderboard(repoId, from, to);
  const { data: busFactor, isLoading: bfLoading } = useBusFactor(repoId);
  const { data: stalePRs, isLoading: staleLoading } = useStalePRs(repoId, 7);

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
        <div className="flex items-center gap-3 flex-wrap">
          <DateRangePicker value={preset} onChange={(p) => setPreset(p)} />
          <select
            value={repoId}
            onChange={(e) => { setSelectedRepoId(e.target.value); localStorage.setItem(STORAGE_KEY, e.target.value); }}
            className="border border-input rounded-md px-3 py-1.5 text-sm bg-background min-w-0 max-w-[200px] sm:max-w-none truncate"
          >
            {repos?.map((r) => (
              <option key={r.id} value={r.id}>{r.fullName}</option>
            ))}
          </select>
        </div>
      </div>

      {!staleLoading && (stalePRs?.length ?? 0) > 0 && (
        <div className="flex items-center gap-3 bg-orange-500/10 border border-orange-500/30 text-orange-600 rounded-xl px-4 py-3 text-sm">
          <AlertTriangle className="w-4 h-4 shrink-0" />
          <span>
            <strong>{stalePRs!.length} stale PR{stalePRs!.length !== 1 ? "s" : ""}</strong> open for more than 7 days — review needed
          </span>
        </div>
      )}

      <BusFactorCard busFactor={busFactor} isLoading={bfLoading} />

      <div className="bg-card border border-border rounded-xl p-6 space-y-4">
        <div className="flex items-center justify-between flex-wrap gap-2">
          <h2 className="font-semibold">Contributor Leaderboard</h2>
          <div className="flex rounded-lg border border-border overflow-hidden text-xs font-medium">
            <button
              onClick={() => setLeaderboardMode("commits")}
              className={`px-3 py-1.5 transition-colors ${leaderboardMode === "commits" ? "bg-primary text-primary-foreground" : "bg-background hover:bg-muted text-muted-foreground"}`}
            >
              By Commits
            </button>
            <button
              onClick={() => setLeaderboardMode("churn")}
              className={`px-3 py-1.5 transition-colors ${leaderboardMode === "churn" ? "bg-primary text-primary-foreground" : "bg-background hover:bg-muted text-muted-foreground"}`}
            >
              By Code Churn
            </button>
          </div>
        </div>
        {leaderboardMode === "commits" ? (
          <LeaderboardTable leaderboard={leaderboard} isLoading={lbLoading} hideHeader />
        ) : (
          <LeaderboardTable leaderboard={churnData} isLoading={churnLoading} hideHeader churnMode />
        )}
      </div>

      {/* Lines changed per contributor */}
      {!lbLoading && (leaderboard?.length ?? 0) >= 2 && (() => {
        const count = Math.min(leaderboard!.length, 8);
        const barSize = Math.max(14, Math.round(110 / count));
        const chartHeight = Math.max(150, count * 60);
        return (
          <div className="bg-card border border-border rounded-xl p-6">
            <h2 className="font-semibold mb-4">Lines Changed per Contributor (top 8)</h2>
            <ResponsiveContainer width="100%" height={chartHeight}>
              <BarChart data={leaderboard!.slice(0, 8).map((c) => ({ login: c.login ?? "(unknown)", added: c.linesAdded, removed: c.linesRemoved }))} margin={{ top: 4, right: 4, left: -10, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
                <XAxis dataKey="login" tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} />
                <YAxis tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} allowDecimals={false} />
                <Tooltip
                  cursor={{ fill: "hsl(var(--muted))", opacity: 0.5 }}
                  contentStyle={{ backgroundColor: "hsl(var(--card))", border: "1px solid hsl(var(--border))", borderRadius: "8px", fontSize: "12px" }}
                  formatter={(v: number, name: string) => [v.toLocaleString(), name === "added" ? "Lines Added" : "Lines Removed"]}
                />
                <Legend formatter={(v) => v === "added" ? "Lines Added" : "Lines Removed"} wrapperStyle={{ fontSize: "12px" }} />
                <Bar dataKey="added" fill="#22c55e" radius={[4, 4, 0, 0]} maxBarSize={barSize} />
                <Bar dataKey="removed" fill="#ef4444" radius={[4, 4, 0, 0]} maxBarSize={barSize} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        );
      })()}

      <StalePRsSection stalePRs={stalePRs} isLoading={staleLoading} />
    </div>
  );
}
