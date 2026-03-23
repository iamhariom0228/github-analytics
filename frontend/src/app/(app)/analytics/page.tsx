"use client";

import { useState } from "react";
import {
  useHeatmap, usePRLifecycle, usePRSizeDistribution,
  useReviewsSummary, useCommitTrend, useOverview,
  usePRMergeRateTrend, useReviewerCoverage, useCollaboration,
  useIssueAnalytics,
} from "@/hooks/useAnalytics";
import { useRepos } from "@/hooks/useRepos";
import { useAuth } from "@/hooks/useAuth";
import { DateRangePicker, usePresetDates, useDatePreset } from "@/components/shared/DateRangePicker";
import { TrendingUp, GitPullRequest, Star, Code2, ShieldCheck, Share2, Check } from "lucide-react";
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
  LineChart, Line, BarChart, Bar,
} from "recharts";
import { StatCard } from "./_components/StatCard";
import { CommitTrendChart } from "./_components/CommitTrendChart";
import { ContributionHeatmapSection } from "./_components/ContributionHeatmapSection";
import { PRLifecycleSection } from "./_components/PRLifecycleSection";
import { ReviewsSection } from "./_components/ReviewsSection";
import { LanguageDistribution } from "./_components/LanguageDistribution";
import { IssuesSection } from "./_components/IssuesSection";
import { ContributorNetworkGraph } from "@/components/charts/ContributorNetworkGraph";
import { useCreateShare } from "@/hooks/useAnalytics";

const tabs = ["Commits", "Pull Requests", "Reviews", "Languages", "Issues"] as const;
type Tab = (typeof tabs)[number];

export default function AnalyticsPage() {
  const [tab, setTab] = useState<Tab>("Commits");
  const [preset, setPreset] = useDatePreset();
  const [granularity, setGranularity] = useState<"daily" | "weekly">("daily");
  const [issueRepoId, setIssueRepoId] = useState<string | undefined>(undefined);
  const { from, to } = usePresetDates(preset);

  const { data: repos } = useRepos();
  const selectedIssueRepo = issueRepoId ?? repos?.[0]?.id;
  const { data: issueData, isLoading: issueLoading } = useIssueAnalytics(selectedIssueRepo);
  const { data: authUser } = useAuth();
  const { data: heatmap, isLoading: heatmapLoading } = useHeatmap(undefined, undefined, from, to);
  const { data: trend, isLoading: trendLoading } = useCommitTrend(from, to, granularity);
  const { data: overview, isLoading: overviewLoading } = useOverview(from, to);
  const { data: lifecycle, isLoading: lifecycleLoading } = usePRLifecycle(from, to);
  const { data: sizeData, isLoading: sizeLoading } = usePRSizeDistribution(from, to);
  const { data: reviews, isLoading: reviewsLoading } = useReviewsSummary(from, to);
  const { data: mergeRateTrend, isLoading: mergeRateLoading } = usePRMergeRateTrend(from, to);
  const { data: reviewerCoverage } = useReviewerCoverage(from, to);
  const { data: collaboration } = useCollaboration(from, to);

  const { mutate: share, data: shareResult, isPending: sharing } = useCreateShare();

  const trendChartData = (trend ?? []).map((p) => {
    const d = new Date(p.date);
    return {
      date: d.toLocaleDateString("en-US", { month: "short", day: "numeric" }),
      fullDate: d.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" }),
      commits: p.count,
    };
  });

  const sizeChartData = sizeData
    ? Object.entries(sizeData.buckets).map(([key, count]) => ({ name: key, count }))
    : [];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <h1 className="text-2xl font-bold">Analytics</h1>
        <div className="flex items-center gap-2">
          <button
            onClick={() => share({ from, to })}
            disabled={sharing}
            title="Create shareable link"
            className="flex items-center gap-1.5 px-3 py-2 text-sm border border-border rounded-lg hover:bg-muted/40 transition-colors disabled:opacity-50"
          >
            {shareResult ? <Check className="w-4 h-4 text-green-500" /> : <Share2 className="w-4 h-4" />}
            {sharing ? "Sharing…" : shareResult ? "Copied!" : "Share"}
          </button>
          <DateRangePicker value={preset} onChange={(p) => setPreset(p)} />
        </div>
      </div>
      {shareResult && (
        <div className="bg-green-500/10 border border-green-500/30 rounded-xl px-4 py-3 text-sm flex items-center justify-between">
          <span className="text-green-600">Snapshot created! Share this link:</span>
          <a href={shareResult.url} target="_blank" rel="noopener noreferrer" className="text-primary hover:underline font-mono text-xs truncate max-w-[200px] sm:max-w-xs">{typeof window !== "undefined" ? window.location.origin : ""}{shareResult.url}</a>
        </div>
      )}

      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-4">
        <StatCard label="Commits" value={overview?.commits ?? 0} icon={TrendingUp} isLoading={overviewLoading} />
        <StatCard label="PRs Authored" value={overview?.prsAuthored ?? 0} icon={GitPullRequest} isLoading={overviewLoading} />
        <StatCard label="Reviews Given" value={overview?.reviewsGiven ?? 0} icon={Star} isLoading={overviewLoading} />
        <StatCard label="Lines Added" value={(overview?.linesAdded ?? 0).toLocaleString()} icon={Code2} isLoading={overviewLoading} />
        <StatCard label="Lines Removed" value={(overview?.linesRemoved ?? 0).toLocaleString()} icon={Code2} isLoading={overviewLoading} />
      </div>

      <div className="flex border-b border-border overflow-x-auto scrollbar-none">
        {tabs.map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors whitespace-nowrap ${
              tab === t ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground"
            }`}
          >
            {t}
          </button>
        ))}
      </div>

      {tab === "Commits" && (
        <div className="space-y-4">
          <CommitTrendChart
            data={trendChartData}
            isLoading={trendLoading}
            granularity={granularity}
            onGranularityChange={setGranularity}
          />
          <ContributionHeatmapSection data={heatmap} isLoading={heatmapLoading} />
        </div>
      )}

      {tab === "Pull Requests" && (
        <div className="space-y-4">
          <PRLifecycleSection
            lifecycle={lifecycle}
            lifecycleLoading={lifecycleLoading}
            sizeChartData={sizeChartData}
            sizeLoading={sizeLoading}
          />

          {/* Reviewer Coverage card */}
          {reviewerCoverage && (
            <div className="bg-card border border-border rounded-xl p-6 flex items-center gap-5">
              <div className="w-12 h-12 rounded-xl bg-primary/10 flex items-center justify-center flex-shrink-0">
                <ShieldCheck className="w-6 h-6 text-primary" />
              </div>
              <div>
                <p className="text-sm text-muted-foreground">Reviewer Coverage</p>
                <p className="text-2xl font-bold">{reviewerCoverage.coveragePct.toFixed(0)}%</p>
                <p className="text-xs text-muted-foreground mt-0.5">
                  {reviewerCoverage.reviewedPRs} of {reviewerCoverage.totalPRs} PRs received at least one review
                </p>
              </div>
            </div>
          )}

          {/* PR Merge Rate Trend chart */}
          {!mergeRateLoading && (mergeRateTrend?.length ?? 0) > 0 && (
            <div className="bg-card border border-border rounded-xl p-6">
              <h2 className="font-semibold mb-1">PR Merge Rate Over Time</h2>
              <p className="text-xs text-muted-foreground mb-5">Weekly merge rate (%) for your pull requests.</p>
              <ResponsiveContainer width="100%" height={240}>
                <AreaChart data={mergeRateTrend!.map((p) => ({
                  week: p.week,
                  "Merge Rate %": Math.round(p.mergeRate),
                  Total: p.total,
                  Merged: p.merged,
                }))} margin={{ top: 4, right: 4, left: -16, bottom: 0 }}>
                  <defs>
                    <linearGradient id="mr-grad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="hsl(var(--primary))" stopOpacity={0.3} />
                      <stop offset="95%" stopColor="hsl(var(--primary))" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
                  <XAxis dataKey="week" tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} />
                  <YAxis domain={[0, 100]} tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} unit="%" />
                  <Tooltip
                    contentStyle={{ backgroundColor: "hsl(var(--card))", border: "1px solid hsl(var(--border))", borderRadius: "8px", fontSize: "12px" }}
                    formatter={(v: number, name: string) => [name === "Merge Rate %" ? `${v}%` : v, name]}
                  />
                  <Legend wrapperStyle={{ fontSize: "12px" }} />
                  <Area type="monotone" dataKey="Merge Rate %" stroke="hsl(var(--primary))" fill="url(#mr-grad)" strokeWidth={2} dot={false} />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          )}
        </div>
      )}

      {tab === "Reviews" && (
        <ReviewsSection reviews={reviews} isLoading={reviewsLoading} />
      )}

      {tab === "Languages" && (
        <div className="space-y-4">
          <div className="bg-card border border-border rounded-xl p-6">
            <h2 className="font-semibold mb-1">Language Distribution</h2>
            <p className="text-xs text-muted-foreground mb-5">
              Primary language of each tracked repository.
            </p>
            <LanguageDistribution repos={repos ?? []} />
          </div>
        </div>
      )}

      {tab === "Issues" && (
        <div className="space-y-4">
          {repos && repos.length > 1 && (
            <div className="flex items-center gap-2 flex-wrap">
              <label className="text-sm text-muted-foreground shrink-0">Repository:</label>
              <select
                value={selectedIssueRepo ?? ""}
                onChange={(e) => setIssueRepoId(e.target.value)}
                className="text-sm border border-border rounded-lg px-3 py-1.5 bg-background min-w-0 max-w-[200px] sm:max-w-none truncate"
              >
                {repos.map((r) => (
                  <option key={r.id} value={r.id}>{r.fullName}</option>
                ))}
              </select>
            </div>
          )}
          <IssuesSection repoId={selectedIssueRepo} data={issueData} isLoading={issueLoading} />
        </div>
      )}

      {/* Collaboration Network — shown in Commits tab below heatmap */}
      {tab === "Commits" && collaboration && authUser && (
        <div className="bg-card border border-border rounded-xl p-6">
          <h2 className="font-semibold mb-1">Collaboration Network</h2>
          <p className="text-xs text-muted-foreground mb-4">
            Connections between you and collaborators based on PR reviews.
          </p>
          <ContributorNetworkGraph data={collaboration} selfLogin={authUser.username} />
        </div>
      )}
    </div>
  );
}
