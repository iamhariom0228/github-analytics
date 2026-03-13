"use client";

import { useState } from "react";
import { useRepos } from "@/hooks/useRepos";
import { useLeaderboard, useBusFactor, useStalePRs } from "@/hooks/useAnalytics";
import { formatDistanceToNow, subDays, formatISO } from "date-fns";

export default function TeamPage() {
  const { data: repos } = useRepos();
  const [selectedRepoId, setSelectedRepoId] = useState<string>("");

  const from = formatISO(subDays(new Date(), 30));
  const to = formatISO(new Date());

  const repoId = selectedRepoId || repos?.[0]?.id || "";
  const { data: leaderboard } = useLeaderboard(repoId, from, to);
  const { data: busFactor } = useBusFactor(repoId);
  const { data: stalePRs } = useStalePRs(repoId, 7);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Team Analytics</h1>
        <select
          value={repoId}
          onChange={(e) => setSelectedRepoId(e.target.value)}
          className="border border-input rounded-md px-3 py-1.5 text-sm bg-background"
        >
          {repos?.map((r) => (
            <option key={r.id} value={r.id}>{r.fullName}</option>
          ))}
        </select>
      </div>

      {/* Bus factor */}
      {busFactor && (
        <div className="bg-card border border-border rounded-xl p-6">
          <h2 className="font-semibold mb-2">Bus Factor</h2>
          <p className="text-muted-foreground text-sm mb-3">
            <strong>{busFactor.topContributor}</strong> owns{" "}
            <strong>{busFactor.topContributorPercentage.toFixed(0)}%</strong> of commits
            across {busFactor.totalContributors} contributors.
          </p>
          <div className="w-full bg-muted rounded-full h-3">
            <div
              className="h-3 rounded-full bg-orange-500"
              style={{ width: `${Math.min(busFactor.topContributorPercentage, 100)}%` }}
            />
          </div>
          {busFactor.topContributorPercentage > 50 && (
            <p className="text-orange-600 text-xs mt-2 font-medium">
              High bus factor risk — consider distributing ownership.
            </p>
          )}
        </div>
      )}

      {/* Leaderboard */}
      <div className="bg-card border border-border rounded-xl p-6">
        <h2 className="font-semibold mb-4">Contributor Leaderboard (Last 30 days)</h2>
        {!leaderboard?.length ? (
          <p className="text-muted-foreground text-sm">No data yet — sync a repository first.</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border text-muted-foreground text-left">
                <th className="py-2 pr-4">#</th>
                <th className="py-2 pr-4">Contributor</th>
                <th className="py-2 pr-4">Commits</th>
                <th className="py-2 pr-4">Lines Added</th>
                <th className="py-2">Lines Removed</th>
              </tr>
            </thead>
            <tbody>
              {leaderboard.map((c, i) => (
                <tr key={c.login} className="border-b border-border/50">
                  <td className="py-2 pr-4 text-muted-foreground">{i + 1}</td>
                  <td className="py-2 pr-4 font-medium">{c.login}</td>
                  <td className="py-2 pr-4">{c.commits}</td>
                  <td className="py-2 pr-4 text-green-600">+{c.linesAdded.toLocaleString()}</td>
                  <td className="py-2 text-red-500">-{c.linesRemoved.toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Stale PRs */}
      <div className="bg-card border border-border rounded-xl p-6">
        <h2 className="font-semibold mb-4">Stale PRs (open &gt;7 days)</h2>
        {!stalePRs?.length ? (
          <p className="text-muted-foreground text-sm">No stale PRs. Great job!</p>
        ) : (
          <div className="space-y-2">
            {stalePRs.map((pr) => (
              <div key={pr.id} className="flex justify-between items-center p-3 bg-orange-50 border border-orange-200 rounded-lg">
                <span className="text-sm font-medium">#{pr.prNumber} {pr.title}</span>
                <span className="text-xs text-orange-600">
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
