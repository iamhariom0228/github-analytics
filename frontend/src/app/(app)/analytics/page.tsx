"use client";

import { useState } from "react";
import {
  useHeatmap,
  usePRLifecycle,
  usePRSizeDistribution,
  useReviewsSummary,
  useCommitTrend,
  useOverview,
} from "@/hooks/useAnalytics";
import { ContributionHeatmap } from "@/components/charts/ContributionHeatmap";
import { DateRangePicker, usePresetDates } from "@/components/shared/DateRangePicker";
import type { DatePreset } from "@/components/shared/DateRangePicker";
import { formatHours } from "@/lib/utils";
import {
  AreaChart,
  Area,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  Cell,
  CartesianGrid,
} from "recharts";
import { PR_SIZE_COLORS } from "@/lib/utils";
import { TrendingUp, GitPullRequest, Star, Code2 } from "lucide-react";

const tabs = ["Commits", "Pull Requests", "Reviews"] as const;
type Tab = (typeof tabs)[number];

function Skeleton({ className = "" }: { className?: string }) {
  return <div className={`animate-pulse bg-muted rounded ${className}`} />;
}

function StatCard({ label, value, icon: Icon, isLoading }: {
  label: string; value: string | number; icon: React.ElementType; isLoading: boolean;
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

export default function AnalyticsPage() {
  const [tab, setTab] = useState<Tab>("Commits");
  const [preset, setPreset] = useState<DatePreset>("30d");
  const [granularity, setGranularity] = useState<"daily" | "weekly">("daily");
  const { from, to } = usePresetDates(preset);

  const { data: heatmap, isLoading: heatmapLoading } = useHeatmap(undefined, undefined, from, to);
  const { data: trend, isLoading: trendLoading } = useCommitTrend(from, to, granularity);
  const { data: overview, isLoading: overviewLoading } = useOverview(from, to);
  const { data: lifecycle, isLoading: lifecycleLoading } = usePRLifecycle(from, to);
  const { data: sizeData, isLoading: sizeLoading } = usePRSizeDistribution(from, to);
  const { data: reviews, isLoading: reviewsLoading } = useReviewsSummary(from, to);

  const sizeChartData = sizeData
    ? Object.entries(sizeData.buckets).map(([key, count]) => ({ name: key, count }))
    : [];

  const trendChartData = (trend ?? []).map((p) => ({
    date: new Date(p.date).toLocaleDateString("en-US", { month: "short", day: "numeric" }),
    commits: p.count,
  }));

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Analytics</h1>
        <DateRangePicker value={preset} onChange={(p) => setPreset(p)} />
      </div>

      {/* Overview stats row */}
      <div className="grid grid-cols-2 lg:grid-cols-5 gap-4">
        <StatCard label="Commits" value={overview?.commits ?? 0} icon={TrendingUp} isLoading={overviewLoading} />
        <StatCard label="PRs Authored" value={overview?.prsAuthored ?? 0} icon={GitPullRequest} isLoading={overviewLoading} />
        <StatCard label="Reviews Given" value={overview?.reviewsGiven ?? 0} icon={Star} isLoading={overviewLoading} />
        <StatCard label="Lines Added" value={(overview?.linesAdded ?? 0).toLocaleString()} icon={Code2} isLoading={overviewLoading} />
        <StatCard label="Lines Removed" value={(overview?.linesRemoved ?? 0).toLocaleString()} icon={Code2} isLoading={overviewLoading} />
      </div>

      {/* Tabs */}
      <div className="flex border-b border-border">
        {tabs.map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors ${
              tab === t
                ? "border-primary text-primary"
                : "border-transparent text-muted-foreground hover:text-foreground"
            }`}
          >
            {t}
          </button>
        ))}
      </div>

      {tab === "Commits" && (
        <div className="space-y-4">
          <div className="bg-card border border-border rounded-xl p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-semibold">Commit Activity</h2>
              <div className="flex gap-0 text-xs border border-border rounded-md overflow-hidden">
                {(["daily", "weekly"] as const).map((g) => (
                  <button
                    key={g}
                    onClick={() => setGranularity(g)}
                    className={`px-3 py-1 capitalize transition-colors ${
                      granularity === g ? "bg-primary text-primary-foreground" : "hover:bg-muted"
                    }`}
                  >
                    {g}
                  </button>
                ))}
              </div>
            </div>
            {trendLoading ? (
              <Skeleton className="h-52" />
            ) : trendChartData.length === 0 ? (
              <div className="h-52 flex items-center justify-center text-muted-foreground text-sm">
                No commit data in this period — try a wider date range
              </div>
            ) : (
              <ResponsiveContainer width="100%" height={210}>
                <AreaChart data={trendChartData}>
                  <defs>
                    <linearGradient id="commitGradient" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="hsl(var(--primary))" stopOpacity={0.3} />
                      <stop offset="95%" stopColor="hsl(var(--primary))" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
                  <XAxis dataKey="date" tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} interval="preserveStartEnd" />
                  <YAxis tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} allowDecimals={false} />
                  <Tooltip contentStyle={{ background: "hsl(var(--card))", border: "1px solid hsl(var(--border))", borderRadius: "8px", fontSize: "12px" }} />
                  <Area type="monotone" dataKey="commits" stroke="hsl(var(--primary))" strokeWidth={2} fill="url(#commitGradient)" />
                </AreaChart>
              </ResponsiveContainer>
            )}
          </div>

          <div className="bg-card border border-border rounded-xl p-6">
            <h2 className="font-semibold mb-4">Contribution Heatmap (Hour × Day of Week)</h2>
            {heatmapLoading ? (
              <Skeleton className="h-40" />
            ) : (heatmap ?? []).length === 0 ? (
              <div className="h-40 flex items-center justify-center text-muted-foreground text-sm">
                No commits yet — sync a repository to see your coding patterns
              </div>
            ) : (
              <ContributionHeatmap data={heatmap ?? []} />
            )}
          </div>
        </div>
      )}

      {tab === "Pull Requests" && (
        <div className="space-y-4">
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            {lifecycleLoading ? (
              [...Array(4)].map((_, i) => <Skeleton key={i} className="h-24 rounded-xl" />)
            ) : (
              <>
                <div className="bg-card border border-border rounded-xl p-6">
                  <div className="text-sm text-muted-foreground">Avg. Time to First Review</div>
                  <div className="text-3xl font-bold mt-1">{formatHours(lifecycle?.avgHoursToFirstReview ?? 0)}</div>
                </div>
                <div className="bg-card border border-border rounded-xl p-6">
                  <div className="text-sm text-muted-foreground">Avg. Time to Merge</div>
                  <div className="text-3xl font-bold mt-1">{formatHours(lifecycle?.avgHoursToMerge ?? 0)}</div>
                </div>
                <div className="bg-card border border-border rounded-xl p-6">
                  <div className="text-sm text-muted-foreground">PRs Merged</div>
                  <div className="text-3xl font-bold mt-1">{lifecycle?.mergedCount ?? 0}</div>
                </div>
                <div className="bg-card border border-border rounded-xl p-6">
                  <div className="text-sm text-muted-foreground">Merge Rate</div>
                  <div className="text-3xl font-bold mt-1">
                    {lifecycle?.totalCount ? Math.round((lifecycle.mergedCount / lifecycle.totalCount) * 100) : 0}%
                  </div>
                </div>
              </>
            )}
          </div>
          <div className="bg-card border border-border rounded-xl p-6">
            <h2 className="font-semibold mb-4">PR Size Distribution (by changed files)</h2>
            {sizeLoading ? (
              <Skeleton className="h-52" />
            ) : sizeChartData.every((d) => d.count === 0) ? (
              <div className="h-52 flex items-center justify-center text-muted-foreground text-sm">
                No PRs found in this period
              </div>
            ) : (
              <>
                <ResponsiveContainer width="100%" height={210}>
                  <BarChart data={sizeChartData}>
                    <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
                    <XAxis dataKey="name" tick={{ fontSize: 12 }} />
                    <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
                    <Tooltip contentStyle={{ background: "hsl(var(--card))", border: "1px solid hsl(var(--border))", borderRadius: "8px", fontSize: "12px" }} />
                    <Bar dataKey="count" radius={[4, 4, 0, 0]}>
                      {sizeChartData.map((entry) => (
                        <Cell key={entry.name} fill={PR_SIZE_COLORS[entry.name] ?? "#6366f1"} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
                <div className="flex gap-4 mt-2 text-xs text-muted-foreground">
                  <span>XS: &lt;10 files</span><span>S: 10-49</span><span>M: 50-249</span><span>L: 250-999</span><span>XL: 1000+</span>
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {tab === "Reviews" && (
        <div className="space-y-4">
          {reviewsLoading ? (
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
              {[...Array(4)].map((_, i) => <Skeleton key={i} className="h-24 rounded-xl" />)}
            </div>
          ) : (reviews?.totalReviewsGiven ?? 0) === 0 ? (
            <div className="bg-card border border-border rounded-xl p-12 text-center text-muted-foreground">
              No reviews given in this period. Try selecting a wider date range.
            </div>
          ) : (
            <>
              <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                <div className="bg-card border border-border rounded-xl p-6">
                  <div className="text-sm text-muted-foreground">Reviews Given</div>
                  <div className="text-3xl font-bold mt-1">{reviews?.totalReviewsGiven ?? 0}</div>
                </div>
                <div className="bg-card border border-border rounded-xl p-6">
                  <div className="text-sm text-muted-foreground">Approved</div>
                  <div className="text-3xl font-bold mt-1 text-green-500">{reviews?.approved ?? 0}</div>
                </div>
                <div className="bg-card border border-border rounded-xl p-6">
                  <div className="text-sm text-muted-foreground">Changes Requested</div>
                  <div className="text-3xl font-bold mt-1 text-yellow-500">{reviews?.changesRequested ?? 0}</div>
                </div>
                <div className="bg-card border border-border rounded-xl p-6">
                  <div className="text-sm text-muted-foreground">Avg Reviews / PR</div>
                  <div className="text-3xl font-bold mt-1">{(reviews?.avgReviewsPerPR ?? 0).toFixed(1)}</div>
                </div>
              </div>
              <div className="bg-card border border-border rounded-xl p-6">
                <h2 className="font-semibold mb-4">Review Breakdown</h2>
                <div className="space-y-3">
                  {[
                    { label: "Approved", value: reviews?.approved ?? 0, color: "bg-green-500" },
                    { label: "Changes Requested", value: reviews?.changesRequested ?? 0, color: "bg-yellow-500" },
                    { label: "Commented", value: reviews?.commented ?? 0, color: "bg-blue-500" },
                  ].map(({ label, value, color }) => {
                    const total = reviews?.totalReviewsGiven || 1;
                    const pct = Math.round((value / total) * 100);
                    return (
                      <div key={label}>
                        <div className="flex justify-between text-sm mb-1">
                          <span>{label}</span>
                          <span className="text-muted-foreground">{value} ({pct}%)</span>
                        </div>
                        <div className="h-2 bg-muted rounded-full overflow-hidden">
                          <div className={`h-full ${color} rounded-full transition-all`} style={{ width: `${pct}%` }} />
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}
