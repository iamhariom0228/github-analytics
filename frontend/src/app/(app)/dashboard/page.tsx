"use client";

import { useDashboard, useInsights, useAiSummary } from "@/hooks/useAnalytics";
import { MetricCard } from "@/components/dashboard/MetricCard";
import { RecentPRsTable } from "@/components/dashboard/RecentPRsTable";
import { InsightsPanel } from "@/components/dashboard/InsightsPanel";
import { formatHours } from "@/lib/utils";

function Skeleton({ className = "" }: { className?: string }) {
  return <div className={`animate-pulse bg-muted rounded ${className}`} />;
}

export default function DashboardPage() {
  const { data, isLoading, error } = useDashboard();
  const { data: insights, isLoading: insightsLoading } = useInsights();
  const { data: aiSummary, isLoading: aiLoading } = useAiSummary();

  return (
    <div className="space-y-8">
      <h1 className="text-2xl font-bold">Dashboard</h1>

      {error ? (
        <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-6 text-center">
          <p className="text-sm text-destructive">Failed to load dashboard. Try refreshing the page.</p>
        </div>
      ) : (
        <>
          {/* Metric cards */}
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            {isLoading ? (
              [...Array(4)].map((_, i) => (
                <div key={i} className="bg-card border border-border rounded-xl p-6">
                  <Skeleton className="h-4 w-28 mb-3" />
                  <Skeleton className="h-8 w-20" />
                </div>
              ))
            ) : (
              <>
                <MetricCard label="Contribution Streak" value={`${data?.currentStreak ?? 0} days`} />
                <MetricCard label="Commits This Week" value={data?.weeklyCommits ?? 0} />
                <MetricCard label="PRs Merged (30d)" value={data?.monthlyPRsMerged ?? 0} />
                <MetricCard label="Avg Merge Time" value={formatHours(data?.avgMergeTimeHours ?? 0)} />
              </>
            )}
          </div>

          {/* AI Summary card */}
          {aiLoading ? (
            <div className="rounded-xl border border-violet-500/20 p-5">
              <Skeleton className="h-4 w-32 mb-3" />
              <Skeleton className="h-4 w-full mb-2" />
              <Skeleton className="h-4 w-3/4" />
            </div>
          ) : aiSummary?.aiPowered ? (
            <div className="rounded-xl border border-violet-500/30 bg-gradient-to-br from-violet-500/10 to-purple-500/5 p-5">
              <div className="flex items-center gap-2 mb-3">
                <span className="text-violet-500 text-base">✦</span>
                <span className="text-xs font-semibold text-violet-500 uppercase tracking-wider">AI Coaching Summary</span>
                <span className="text-xs text-muted-foreground ml-auto">Powered by Groq · Llama 3</span>
              </div>
              <p className="text-sm leading-relaxed text-foreground">{aiSummary.summary}</p>
            </div>
          ) : null}

          {/* Insights */}
          {insightsLoading ? (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {[...Array(4)].map((_, i) => <Skeleton key={i} className="h-28 rounded-xl" />)}
            </div>
          ) : insights && insights.length > 0 ? (
            <InsightsPanel insights={insights} />
          ) : null}

          {/* Recent PRs */}
          <div className="bg-card border border-border rounded-xl p-6">
            <h2 className="font-semibold mb-4">Recent Pull Requests</h2>
            {isLoading ? (
              <div className="space-y-3">
                {[...Array(4)].map((_, i) => <Skeleton key={i} className="h-10" />)}
              </div>
            ) : (data?.recentPRs ?? []).length === 0 ? (
              <p className="text-muted-foreground text-sm py-4 text-center">
                No pull requests yet — they will appear here once synced.
              </p>
            ) : (
              <RecentPRsTable prs={data?.recentPRs ?? []} />
            )}
          </div>
        </>
      )}
    </div>
  );
}
