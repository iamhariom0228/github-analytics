"use client";

import { useDashboard, useInsights } from "@/hooks/useAnalytics";
import { MetricCard } from "@/components/dashboard/MetricCard";
import { RecentPRsTable } from "@/components/dashboard/RecentPRsTable";
import { InsightsPanel } from "@/components/dashboard/InsightsPanel";
import { formatHours } from "@/lib/utils";

export default function DashboardPage() {
  const { data, isLoading, error } = useDashboard();
  const { data: insights } = useInsights();

  if (isLoading) {
    return (
      <div className="space-y-6">
        <h1 className="text-2xl font-bold">Dashboard</h1>
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="bg-card border border-border rounded-xl p-6 h-28 animate-pulse" />
          ))}
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-red-500">Failed to load dashboard. Please try again.</div>
    );
  }

  return (
    <div className="space-y-8">
      <h1 className="text-2xl font-bold">Dashboard</h1>

      {/* Metric cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <MetricCard
          label="Contribution Streak"
          value={`${data?.currentStreak ?? 0} days`}
        />
        <MetricCard
          label="Commits This Week"
          value={data?.weeklyCommits ?? 0}
        />
        <MetricCard
          label="PRs Merged (30d)"
          value={data?.monthlyPRsMerged ?? 0}
        />
        <MetricCard
          label="Avg Merge Time"
          value={formatHours(data?.avgMergeTimeHours ?? 0)}
        />
      </div>

      {/* Insights */}
      {insights && insights.length > 0 && <InsightsPanel insights={insights} />}

      {/* Recent PRs */}
      <div className="bg-card border border-border rounded-xl p-6">
        <h2 className="font-semibold mb-4">Recent Pull Requests</h2>
        <RecentPRsTable prs={data?.recentPRs ?? []} />
      </div>
    </div>
  );
}
