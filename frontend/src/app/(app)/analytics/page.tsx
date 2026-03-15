"use client";

import { useState } from "react";
import { useHeatmap, usePRLifecycle, usePRSizeDistribution, useReviewsSummary } from "@/hooks/useAnalytics";
import { ContributionHeatmap } from "@/components/charts/ContributionHeatmap";
import { DateRangePicker, usePresetDates } from "@/components/shared/DateRangePicker";
import type { DatePreset } from "@/components/shared/DateRangePicker";
import { formatHours } from "@/lib/utils";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  Cell,
} from "recharts";
import { PR_SIZE_COLORS } from "@/lib/utils";

const tabs = ["Commits", "Pull Requests", "Reviews"] as const;
type Tab = (typeof tabs)[number];

export default function AnalyticsPage() {
  const [tab, setTab] = useState<Tab>("Commits");
  const [preset, setPreset] = useState<DatePreset>("30d");
  const { from, to } = usePresetDates(preset);

  const { data: heatmap, isLoading: heatmapLoading } = useHeatmap();
  const { data: lifecycle } = usePRLifecycle(from, to);
  const { data: sizeData } = usePRSizeDistribution(from, to);
  const { data: reviews } = useReviewsSummary(from, to);

  const sizeChartData = sizeData
    ? Object.entries(sizeData.buckets).map(([key, count]) => ({ name: key, count }))
    : [];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Analytics</h1>
        {tab !== "Commits" && (
          <DateRangePicker
            value={preset}
            onChange={(p) => setPreset(p)}
          />
        )}
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

      {/* Commits Tab */}
      {tab === "Commits" && (
        <div className="bg-card border border-border rounded-xl p-6">
          <h2 className="font-semibold mb-4">Contribution Heatmap (Hour x Day)</h2>
          {heatmapLoading ? (
            <div className="h-40 animate-pulse bg-muted rounded" />
          ) : (
            <ContributionHeatmap data={heatmap ?? []} />
          )}
        </div>
      )}

      {/* PR Tab */}
      {tab === "Pull Requests" && (
        <div className="space-y-4">
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="bg-card border border-border rounded-xl p-6">
              <div className="text-sm text-muted-foreground">Avg. Time to First Review</div>
              <div className="text-3xl font-bold mt-1">
                {formatHours(lifecycle?.avgHoursToFirstReview ?? 0)}
              </div>
            </div>
            <div className="bg-card border border-border rounded-xl p-6">
              <div className="text-sm text-muted-foreground">Avg. Time to Merge</div>
              <div className="text-3xl font-bold mt-1">
                {formatHours(lifecycle?.avgHoursToMerge ?? 0)}
              </div>
            </div>
            <div className="bg-card border border-border rounded-xl p-6">
              <div className="text-sm text-muted-foreground">PRs Merged</div>
              <div className="text-3xl font-bold mt-1">{lifecycle?.mergedCount ?? 0}</div>
            </div>
            <div className="bg-card border border-border rounded-xl p-6">
              <div className="text-sm text-muted-foreground">Merge Rate</div>
              <div className="text-3xl font-bold mt-1">
                {lifecycle?.totalCount
                  ? Math.round((lifecycle.mergedCount / lifecycle.totalCount) * 100)
                  : 0}
                %
              </div>
            </div>
          </div>

          <div className="bg-card border border-border rounded-xl p-6">
            <h2 className="font-semibold mb-4">PR Size Distribution (by changed files)</h2>
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={sizeChartData}>
                <XAxis dataKey="name" />
                <YAxis />
                <Tooltip />
                <Bar dataKey="count" radius={[4, 4, 0, 0]}>
                  {sizeChartData.map((entry) => (
                    <Cell key={entry.name} fill={PR_SIZE_COLORS[entry.name] ?? "#6366f1"} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
            <div className="flex gap-4 mt-2 text-xs text-muted-foreground">
              <span>XS: &lt;10 files</span>
              <span>S: 10-49</span>
              <span>M: 50-249</span>
              <span>L: 250-999</span>
              <span>XL: 1000+</span>
            </div>
          </div>
        </div>
      )}

      {tab === "Reviews" && (
        <div className="space-y-4">
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
                      <div className={`h-full ${color} rounded-full`} style={{ width: `${pct}%` }} />
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
