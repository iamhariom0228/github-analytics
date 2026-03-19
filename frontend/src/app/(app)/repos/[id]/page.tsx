"use client";

import { use } from "react";
import { useStarsForksTrend, useReleaseTrend, useIssueAnalytics, useLanguageBytes } from "@/hooks/useAnalytics";
import { useRepos, useTriggerSync } from "@/hooks/useRepos";
import { ArrowLeft, RefreshCw, Star, GitFork, Eye } from "lucide-react";
import Link from "next/link";
import {
  LineChart, Line, BarChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
} from "recharts";
import { IssuesSection } from "../../analytics/_components/IssuesSection";

const tooltipStyle = {
  backgroundColor: "hsl(var(--card))",
  border: "1px solid hsl(var(--border))",
  borderRadius: "8px",
  fontSize: "12px",
};

export default function RepoDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);

  const { data: repos } = useRepos();
  const repo = repos?.find((r) => r.id === id);
  const triggerSync = useTriggerSync();

  const { data: starsForksTrend } = useStarsForksTrend(id);
  const { data: releaseTrend } = useReleaseTrend(id);
  const { data: issueAnalytics, isLoading: issueLoading } = useIssueAnalytics(id);
  const { data: languageBytes } = useLanguageBytes(id);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-3 min-w-0">
          <Link href="/repos" className="p-1.5 hover:bg-muted rounded-md transition-colors shrink-0">
            <ArrowLeft className="w-4 h-4" />
          </Link>
          <div className="min-w-0">
            <h1 className="text-xl font-bold truncate">{repo?.fullName ?? "Repository"}</h1>
            {repo?.description && (
              <p className="text-sm text-muted-foreground truncate mt-0.5">{repo.description}</p>
            )}
          </div>
        </div>
        <button
          onClick={() => triggerSync.mutate(id)}
          disabled={repo?.syncStatus === "SYNCING" || triggerSync.isPending}
          className="flex items-center gap-2 px-3 py-1.5 text-sm border border-border rounded-md hover:bg-muted disabled:opacity-50 transition-colors shrink-0"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${repo?.syncStatus === "SYNCING" ? "animate-spin" : ""}`} />
          Sync
        </button>
      </div>

      {/* Quick stats */}
      {repo && (
        <div className="flex items-center gap-4 flex-wrap text-sm text-muted-foreground">
          {(repo.stars ?? 0) > 0 && (
            <span className="flex items-center gap-1"><Star className="w-3.5 h-3.5" />{repo.stars!.toLocaleString()} stars</span>
          )}
          {(repo.forks ?? 0) > 0 && (
            <span className="flex items-center gap-1"><GitFork className="w-3.5 h-3.5" />{repo.forks!.toLocaleString()} forks</span>
          )}
          {repo.language && (
            <span className="flex items-center gap-1.5">
              <span className="w-2.5 h-2.5 rounded-full bg-blue-400 inline-block" />{repo.language}
            </span>
          )}
          {repo.lastSyncedAt && (
            <span>Last synced {new Date(repo.lastSyncedAt).toLocaleDateString()}</span>
          )}
        </div>
      )}

      {/* Stars & Forks Growth */}
      <div className="bg-card border border-border rounded-xl p-6">
        <h2 className="font-semibold mb-1">Stars &amp; Forks Growth</h2>
        <p className="text-xs text-muted-foreground mb-5">Daily snapshots over the last 90 days.</p>
        {(starsForksTrend?.length ?? 0) === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-10">
            No snapshot data yet. Sync the repository to start tracking.
          </p>
        ) : (
          <ResponsiveContainer width="100%" height={220}>
            <LineChart
              data={starsForksTrend!.map((p) => ({ date: p.date.substring(0, 10), Stars: p.stars, Forks: p.forks }))}
              margin={{ top: 4, right: 4, left: -12, bottom: 0 }}
            >
              <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
              <XAxis dataKey="date" tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} interval="preserveStartEnd" />
              <YAxis tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} />
              <Tooltip contentStyle={tooltipStyle} />
              <Legend wrapperStyle={{ fontSize: "12px" }} />
              <Line type="monotone" dataKey="Stars" stroke="#f59e0b" strokeWidth={2} dot={false} />
              <Line type="monotone" dataKey="Forks" stroke="hsl(var(--primary))" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>

      {/* Release Cadence */}
      <div className="bg-card border border-border rounded-xl p-6">
        <h2 className="font-semibold mb-1">Release Cadence</h2>
        <p className="text-xs text-muted-foreground mb-5">Number of releases per month (last 12 months).</p>
        {(releaseTrend?.length ?? 0) === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-10">No releases found.</p>
        ) : (
          <ResponsiveContainer width="100%" height={200}>
            <BarChart
              data={releaseTrend!.map((p) => ({ month: p.month, Releases: p.count }))}
              margin={{ top: 4, right: 4, left: -16, bottom: 0 }}
            >
              <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
              <XAxis dataKey="month" tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} />
              <YAxis tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} allowDecimals={false} />
              <Tooltip contentStyle={tooltipStyle} />
              <Bar dataKey="Releases" fill="hsl(var(--primary))" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </div>

      {/* Language Bytes */}
      {(languageBytes?.length ?? 0) > 0 && (
        <div className="bg-card border border-border rounded-xl p-6">
          <h2 className="font-semibold mb-1">Languages</h2>
          <p className="text-xs text-muted-foreground mb-5">Bytes of code by language.</p>
          <div className="space-y-2">
            {languageBytes!.map((l) => {
              const total = languageBytes!.reduce((s, x) => s + x.bytes, 0);
              const pct = total > 0 ? ((l.bytes / total) * 100).toFixed(1) : "0";
              return (
                <div key={l.language} className="flex items-center gap-3 text-sm">
                  <span className="w-24 truncate text-muted-foreground">{l.language}</span>
                  <div className="flex-1 h-2 bg-muted rounded-full overflow-hidden">
                    <div className="h-full bg-primary rounded-full" style={{ width: `${pct}%` }} />
                  </div>
                  <span className="text-xs text-muted-foreground w-10 text-right">{pct}%</span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Issues */}
      <IssuesSection repoId={id} data={issueAnalytics} isLoading={issueLoading} />
    </div>
  );
}
