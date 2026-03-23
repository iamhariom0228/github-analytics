"use client";

import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, Cell,
} from "recharts";
import { PR_SIZE_COLORS, formatHours } from "@/lib/utils";
import { Skeleton } from "@/components/shared/Skeleton";


interface Lifecycle {
  avgHoursToFirstReview: number;
  avgHoursToMerge: number;
  mergedCount: number;
  totalCount: number;
}

interface SizePoint { name: string; count: number }

interface Props {
  lifecycle: Lifecycle | undefined;
  lifecycleLoading: boolean;
  sizeChartData: SizePoint[];
  sizeLoading: boolean;
}

export function PRLifecycleSection({ lifecycle, lifecycleLoading, sizeChartData, sizeLoading }: Props) {
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {lifecycleLoading ? (
          [...Array(4)].map((_, i) => <Skeleton key={i} className="h-24 rounded-xl" />)
        ) : (
          <>
            <div className="bg-card border border-border rounded-xl p-4 sm:p-6">
              <div className="text-sm text-muted-foreground">Avg. Time to First Review</div>
              <div className="text-2xl sm:text-3xl font-bold mt-1">{formatHours(lifecycle?.avgHoursToFirstReview ?? 0)}</div>
            </div>
            <div className="bg-card border border-border rounded-xl p-4 sm:p-6">
              <div className="text-sm text-muted-foreground">Avg. Time to Merge</div>
              <div className="text-2xl sm:text-3xl font-bold mt-1">{formatHours(lifecycle?.avgHoursToMerge ?? 0)}</div>
            </div>
            <div className="bg-card border border-border rounded-xl p-4 sm:p-6">
              <div className="text-sm text-muted-foreground">PRs Merged</div>
              <div className="text-2xl sm:text-3xl font-bold mt-1">{lifecycle?.mergedCount ?? 0}</div>
            </div>
            <div className="bg-card border border-border rounded-xl p-4 sm:p-6">
              <div className="text-sm text-muted-foreground">Merge Rate</div>
              <div className="text-2xl sm:text-3xl font-bold mt-1">
                {lifecycle?.totalCount ? Math.round((lifecycle.mergedCount / lifecycle.totalCount) * 100) : 0}%
              </div>
            </div>
          </>
        )}
      </div>

      <div className="bg-card border border-border rounded-xl p-6">
        <h2 className="font-semibold mb-6">PR Lifecycle Pipeline</h2>
        <div className="flex items-center gap-0 overflow-x-auto pb-2">
          {/* Stage 1 */}
          <div className="flex-shrink-0 text-center px-3 sm:px-5 py-4 bg-blue-500/10 border border-blue-500/30 rounded-xl min-w-[100px] sm:min-w-[120px]">
            <div className="text-2xl sm:text-3xl font-bold text-blue-500">{lifecycle?.totalCount ?? 0}</div>
            <div className="text-xs text-muted-foreground mt-1 font-medium uppercase tracking-wide">PRs Opened</div>
            <div className="text-xs text-blue-500/70 mt-1">100%</div>
          </div>
          {/* Arrow + time */}
          <div className="flex-1 flex flex-col items-center min-w-[90px] px-3">
            <div className="text-xs font-semibold text-foreground mb-1">{formatHours(lifecycle?.avgHoursToFirstReview ?? 0)}</div>
            <div className="flex items-center w-full">
              <div className="h-0.5 bg-border flex-1" />
              <svg width="12" height="12" viewBox="0 0 12 12" className="text-muted-foreground flex-shrink-0"><path d="M1 6h9M7 2l4 4-4 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" fill="none"/></svg>
            </div>
            <div className="text-xs text-muted-foreground mt-1">wait for review</div>
          </div>
          {/* Stage 2 */}
          <div className="flex-shrink-0 text-center px-3 sm:px-5 py-4 bg-violet-500/10 border border-violet-500/30 rounded-xl min-w-[100px] sm:min-w-[120px]">
            <div className="text-2xl sm:text-3xl font-bold text-violet-500">{lifecycle?.totalCount ?? 0}</div>
            <div className="text-xs text-muted-foreground mt-1 font-medium uppercase tracking-wide">In Review</div>
            <div className="text-xs text-violet-500/70 mt-1">avg {formatHours(lifecycle?.avgHoursToFirstReview ?? 0)} wait</div>
          </div>
          {/* Arrow + time */}
          <div className="flex-1 flex flex-col items-center min-w-[90px] px-3">
            <div className="text-xs font-semibold text-foreground mb-1">{formatHours(Math.max((lifecycle?.avgHoursToMerge ?? 0) - (lifecycle?.avgHoursToFirstReview ?? 0), 0))}</div>
            <div className="flex items-center w-full">
              <div className="h-0.5 bg-border flex-1" />
              <svg width="12" height="12" viewBox="0 0 12 12" className="text-muted-foreground flex-shrink-0"><path d="M1 6h9M7 2l4 4-4 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" fill="none"/></svg>
            </div>
            <div className="text-xs text-muted-foreground mt-1">review → merge</div>
          </div>
          {/* Stage 3 */}
          <div className="flex-shrink-0 text-center px-3 sm:px-5 py-4 bg-green-500/10 border border-green-500/30 rounded-xl min-w-[100px] sm:min-w-[120px]">
            <div className="text-2xl sm:text-3xl font-bold text-green-500">{lifecycle?.mergedCount ?? 0}</div>
            <div className="text-xs text-muted-foreground mt-1 font-medium uppercase tracking-wide">Merged</div>
            {lifecycle && lifecycle.totalCount > 0 && (
              <div className="text-xs text-green-500/70 mt-1">{Math.round((lifecycle.mergedCount / lifecycle.totalCount) * 100)}% of opened</div>
            )}
          </div>
        </div>

        {/* Merge rate + bottleneck insight */}
        {lifecycle && lifecycle.totalCount > 0 && (() => {
          const rate = Math.round((lifecycle.mergedCount / lifecycle.totalCount) * 100);
          const barColor = rate >= 75 ? "bg-green-500" : rate >= 40 ? "bg-yellow-500" : "bg-red-500";
          const textColor = rate >= 75 ? "text-green-600" : rate >= 40 ? "text-yellow-600" : "text-red-500";
          const reviewWait = lifecycle.avgHoursToFirstReview;
          const reviewToMerge = Math.max(lifecycle.avgHoursToMerge - lifecycle.avgHoursToFirstReview, 0);
          const bottleneck = reviewWait > reviewToMerge
            ? `Review wait (${formatHours(reviewWait)}) is the bottleneck — ${Math.round(reviewWait / Math.max(reviewToMerge, 0.1))}× longer than merge stage`
            : `Merge stage (${formatHours(reviewToMerge)}) takes longer than review wait (${formatHours(reviewWait)})`;
          const notMerged = lifecycle.totalCount - lifecycle.mergedCount;
          return (
            <div className="mt-5 pt-4 border-t border-border space-y-3">
              <div className="flex justify-between text-sm">
                <span className="text-muted-foreground">Merge Rate</span>
                <span className={`font-semibold ${textColor}`}>{rate}% merged</span>
              </div>
              <div className="h-2 bg-muted rounded-full overflow-hidden">
                <div className={`h-full ${barColor} rounded-full transition-all duration-500`} style={{ width: `${rate}%` }} />
              </div>
              <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
                <span>{lifecycle.mergedCount} merged · {notMerged} not merged · avg {formatHours(lifecycle.avgHoursToMerge)} total</span>
              </div>
              {reviewWait > 0 && reviewToMerge > 0 && (
                <div className="text-xs text-muted-foreground bg-muted/40 rounded-lg px-3 py-2">
                  <span className="text-foreground font-medium">Bottleneck: </span>{bottleneck}
                </div>
              )}
            </div>
          );
        })()}
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
                <Tooltip
                  cursor={false}
                  position={{ y: 80 }}
                  contentStyle={{ backgroundColor: "hsl(var(--card))", border: "1px solid hsl(var(--border))", borderRadius: "8px", fontSize: "12px" }}
                  labelStyle={{ color: "hsl(var(--foreground))", fontWeight: 500 }}
                  itemStyle={{ color: "hsl(var(--muted-foreground))" }}
                  formatter={(v: number) => [v, "PRs"]}
                />
                <Bar dataKey="count" radius={[4, 4, 0, 0]}>
                  {sizeChartData.map((entry) => (
                    <Cell key={entry.name} fill={PR_SIZE_COLORS[entry.name] ?? "#6366f1"} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
            <div className="flex gap-4 mt-2 text-xs text-muted-foreground justify-center">
              <span>XS: &lt;5 files</span><span>S: 5-29</span><span>M: 30-100</span><span>L: 101-250</span><span>XL: 250+</span>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
