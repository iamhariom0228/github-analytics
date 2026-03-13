"use client";

import { useState } from "react";
import { useHeatmap, usePRLifecycle, usePRSizeDistribution } from "@/hooks/useAnalytics";
import { ContributionHeatmap } from "@/components/charts/ContributionHeatmap";
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
import { subDays, formatISO } from "date-fns";

const tabs = ["Commits", "Pull Requests", "Reviews"] as const;
type Tab = (typeof tabs)[number];

export default function AnalyticsPage() {
  const [tab, setTab] = useState<Tab>("Commits");
  const from = formatISO(subDays(new Date(), 30));
  const to = formatISO(new Date());

  const { data: heatmap, isLoading: heatmapLoading } = useHeatmap();
  const { data: lifecycle } = usePRLifecycle(from, to);
  const { data: sizeData } = usePRSizeDistribution(from, to);

  const sizeChartData = sizeData
    ? Object.entries(sizeData.buckets).map(([key, count]) => ({ name: key, count }))
    : [];

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">Analytics</h1>

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
          {/* Lifecycle metrics */}
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
              <div className="text-sm text-muted-foreground">PRs Merged (30d)</div>
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

          {/* Size distribution */}
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
        <div className="bg-card border border-border rounded-xl p-6 text-muted-foreground">
          Review analytics coming soon — add a repo and sync to populate data.
        </div>
      )}
    </div>
  );
}
