"use client";

import { useState } from "react";
import {
  useHeatmap, usePRLifecycle, usePRSizeDistribution,
  useReviewsSummary, useCommitTrend, useOverview,
  usePRMergeRateTrend, useReviewerCoverage,
} from "@/hooks/useAnalytics";
import { useRepos } from "@/hooks/useRepos";
import { DateRangePicker, usePresetDates, useDatePreset } from "@/components/shared/DateRangePicker";
import { TrendingUp, GitPullRequest, Star, Code2, ShieldCheck } from "lucide-react";
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
} from "recharts";
import { StatCard } from "./_components/StatCard";
import { CommitTrendChart } from "./_components/CommitTrendChart";
import { ContributionHeatmapSection } from "./_components/ContributionHeatmapSection";
import { PRLifecycleSection } from "./_components/PRLifecycleSection";
import { ReviewsSection } from "./_components/ReviewsSection";
import { LanguageDistribution } from "./_components/LanguageDistribution";

const tabs = ["Commits", "Pull Requests", "Reviews", "Languages"] as const;
type Tab = (typeof tabs)[number];

export default function AnalyticsPage() {
  const [tab, setTab] = useState<Tab>("Commits");
  const [preset, setPreset] = useDatePreset();
  const [granularity, setGranularity] = useState<"daily" | "weekly">("daily");
  const { from, to } = usePresetDates(preset);

  const { data: repos } = useRepos();
  const { data: heatmap, isLoading: heatmapLoading } = useHeatmap(undefined, undefined, from, to);
  const { data: trend, isLoading: trendLoading } = useCommitTrend(from, to, granularity);
  const { data: overview, isLoading: overviewLoading } = useOverview(from, to);
  const { data: lifecycle, isLoading: lifecycleLoading } = usePRLifecycle(from, to);
  const { data: sizeData, isLoading: sizeLoading } = usePRSizeDistribution(from, to);
  const { data: reviews, isLoading: reviewsLoading } = useReviewsSummary(from, to);
  const { data: mergeRateTrend, isLoading: mergeRateLoading } = usePRMergeRateTrend(from, to);
  const { data: reviewerCoverage } = useReviewerCoverage(from, to);

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
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Analytics</h1>
        <DateRangePicker value={preset} onChange={(p) => setPreset(p)} />
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-5 gap-4">
        <StatCard label="Commits" value={overview?.commits ?? 0} icon={TrendingUp} isLoading={overviewLoading} />
        <StatCard label="PRs Authored" value={overview?.prsAuthored ?? 0} icon={GitPullRequest} isLoading={overviewLoading} />
        <StatCard label="Reviews Given" value={overview?.reviewsGiven ?? 0} icon={Star} isLoading={overviewLoading} />
        <StatCard label="Lines Added" value={(overview?.linesAdded ?? 0).toLocaleString()} icon={Code2} isLoading={overviewLoading} />
        <StatCard label="Lines Removed" value={(overview?.linesRemoved ?? 0).toLocaleString()} icon={Code2} isLoading={overviewLoading} />
      </div>

      <div className="flex border-b border-border">
        {tabs.map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors ${
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
        <div className="bg-card border border-border rounded-xl p-6">
          <h2 className="font-semibold mb-1">Language Distribution</h2>
          <p className="text-xs text-muted-foreground mb-5">
            Primary language of each tracked repository.
          </p>
          <LanguageDistribution repos={repos ?? []} />
        </div>
      )}
    </div>
  );
}
