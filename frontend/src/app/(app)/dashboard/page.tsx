"use client";

import { useState } from "react";
import { useDashboard, useInsights, useAiSummary, useOverview } from "@/hooks/useAnalytics";
import { MetricCard } from "@/components/dashboard/MetricCard";
import { RecentPRsTable } from "@/components/dashboard/RecentPRsTable";
import { InsightsPanel } from "@/components/dashboard/InsightsPanel";
import { DateRangePicker, usePresetDates } from "@/components/shared/DateRangePicker";
import type { DatePreset } from "@/components/shared/DateRangePicker";
import { formatHours } from "@/lib/utils";
import { TrendingUp, GitPullRequest, Star, Code2 } from "lucide-react";

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

export default function DashboardPage() {
  const [preset, setPreset] = useState<DatePreset>("30d");
  const { from, to } = usePresetDates(preset);

  const { data, isLoading, error } = useDashboard();
  const { data: overview, isLoading: overviewLoading } = useOverview(from, to);
  const { data: insights, isLoading: insightsLoading } = useInsights(undefined, from, to);
  const { data: aiSummary, isLoading: aiLoading } = useAiSummary(undefined, from, to);

  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Dashboard</h1>
        <DateRangePicker value={preset} onChange={(p) => setPreset(p)} />
      </div>

      {error ? (
        <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-6 text-center">
          <p className="text-sm text-destructive">Failed to load dashboard. Try refreshing the page.</p>
        </div>
      ) : (
        <>
          {/* Period overview */}
          <div className="grid grid-cols-2 lg:grid-cols-5 gap-4">
            <StatCard label="Commits" value={overview?.commits ?? 0} icon={TrendingUp} isLoading={overviewLoading} />
            <StatCard label="PRs Authored" value={overview?.prsAuthored ?? 0} icon={GitPullRequest} isLoading={overviewLoading} />
            <StatCard label="Reviews Given" value={overview?.reviewsGiven ?? 0} icon={Star} isLoading={overviewLoading} />
            <StatCard label="Lines Added" value={(overview?.linesAdded ?? 0).toLocaleString()} icon={Code2} isLoading={overviewLoading} />
            <StatCard label="Lines Removed" value={(overview?.linesRemoved ?? 0).toLocaleString()} icon={Code2} isLoading={overviewLoading} />
          </div>

          {/* All-time metric cards */}
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
