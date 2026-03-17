"use client";

import { useState } from "react";
import { useDashboard, useInsights, useAiSummary, useOverview, useCommitTrend } from "@/hooks/useAnalytics";
import { MetricCard } from "@/components/dashboard/MetricCard";
import { RecentPRsTable } from "@/components/dashboard/RecentPRsTable";
import { InsightsPanel } from "@/components/dashboard/InsightsPanel";
import { DateRangePicker, usePresetDates } from "@/components/shared/DateRangePicker";
import type { DatePreset } from "@/components/shared/DateRangePicker";
import { formatHours } from "@/lib/utils";
import { TrendingUp, GitPullRequest, Star, Code2 } from "lucide-react";
import { AreaChart, Area, ResponsiveContainer, Tooltip, CartesianGrid, XAxis, YAxis } from "recharts";

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
  const { data: trendData, isLoading: trendLoading } = useCommitTrend(from, to, "daily");

  const sparkData = (trendData ?? []).map((p: { date: string; count: number }) => ({
    date: new Date(p.date).toLocaleDateString("en-US", { month: "short", day: "numeric" }),
    commits: p.count,
  }));

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

          {/* Commit Activity Chart */}
          <div className="bg-card border border-border rounded-xl p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="font-semibold">Commit Activity</h2>
              {!trendLoading && sparkData.length > 0 && (
                <span className="text-xs text-muted-foreground">{sparkData.length} data points</span>
              )}
            </div>
            {trendLoading ? <Skeleton className="h-48" /> : sparkData.length === 0 ? (
              <div className="h-48 flex items-center justify-center text-muted-foreground text-sm">No commit data for this period</div>
            ) : (
              <ResponsiveContainer width="100%" height={192}>
                <AreaChart data={sparkData} margin={{ top: 5, right: 4, left: -20, bottom: 0 }}>
                  <defs>
                    <linearGradient id="sparkGradient" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="hsl(var(--primary))" stopOpacity={0.3} />
                      <stop offset="95%" stopColor="hsl(var(--primary))" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
                  <XAxis dataKey="date" tick={{ fontSize: 10, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} interval="preserveStartEnd" />
                  <YAxis tick={{ fontSize: 10, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} allowDecimals={false} />
                  <Tooltip contentStyle={{ background: "hsl(var(--card))", border: "1px solid hsl(var(--border))", borderRadius: "8px", fontSize: "12px" }} formatter={(v: number) => [v, "commits"]} labelFormatter={(l: string) => l} />
                  <Area type="monotone" dataKey="commits" stroke="hsl(var(--primary))" strokeWidth={2} fill="url(#sparkGradient)" dot={false} />
                </AreaChart>
              </ResponsiveContainer>
            )}
          </div>

          {/* Code Impact */}
          {!overviewLoading && ((overview?.linesAdded ?? 0) + (overview?.linesRemoved ?? 0)) > 0 && (
            <div className="bg-card border border-border rounded-xl p-6">
              <h2 className="font-semibold mb-4">Code Impact</h2>
              <div className="space-y-4">
                {[
                  { label: "Lines Added", value: overview!.linesAdded, color: "bg-green-500", textColor: "text-green-600", sign: "+" },
                  { label: "Lines Removed", value: overview!.linesRemoved, color: "bg-red-500", textColor: "text-red-500", sign: "-" },
                ].map(({ label, value, color, textColor, sign }) => {
                  const total = (overview!.linesAdded + overview!.linesRemoved) || 1;
                  const pct = Math.round((value / total) * 100);
                  return (
                    <div key={label} className="space-y-1.5">
                      <div className="flex justify-between text-sm">
                        <span className="text-muted-foreground">{label}</span>
                        <span className={`font-semibold ${textColor}`}>{sign}{value.toLocaleString()} <span className="font-normal text-muted-foreground">({pct}%)</span></span>
                      </div>
                      <div className="h-2 bg-muted rounded-full overflow-hidden">
                        <div className={`h-full ${color} rounded-full transition-all duration-500`} style={{ width: `${pct}%` }} />
                      </div>
                    </div>
                  );
                })}
                <div className="pt-1 text-xs text-muted-foreground">
                  Net change: <span className={`font-medium ${overview!.linesAdded >= overview!.linesRemoved ? "text-green-600" : "text-red-500"}`}>
                    {overview!.linesAdded >= overview!.linesRemoved ? "+" : ""}{(overview!.linesAdded - overview!.linesRemoved).toLocaleString()} lines
                  </span>
                </div>
              </div>
            </div>
          )}

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
                <div className="bg-card border border-border rounded-xl p-6 flex items-center gap-4">
                  {/* SVG streak ring */}
                  {(() => {
                    const streak = data?.currentStreak ?? 0;
                    const target = 30; // goal: 30-day streak
                    const pct = Math.min(streak / target, 1);
                    const r = 24, circ = 2 * Math.PI * r;
                    return (
                      <div className="relative flex-shrink-0 w-16 h-16">
                        <svg width="64" height="64" viewBox="0 0 64 64" className="-rotate-90">
                          <circle cx="32" cy="32" r={r} fill="none" stroke="hsl(var(--muted))" strokeWidth="5" />
                          <circle cx="32" cy="32" r={r} fill="none" stroke="#f97316" strokeWidth="5"
                            strokeDasharray={circ} strokeDashoffset={circ * (1 - pct)}
                            strokeLinecap="round" style={{ transition: "stroke-dashoffset 0.5s ease" }}
                          />
                        </svg>
                        <div className="absolute inset-0 flex items-center justify-center">
                          <span className="text-lg">🔥</span>
                        </div>
                      </div>
                    );
                  })()}
                  <div>
                    <div className="text-xs text-muted-foreground uppercase tracking-wide font-medium">Contribution Streak</div>
                    <div className="text-3xl font-bold">{data?.currentStreak ?? 0} <span className="text-base font-normal text-muted-foreground">days</span></div>
                  </div>
                </div>
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
