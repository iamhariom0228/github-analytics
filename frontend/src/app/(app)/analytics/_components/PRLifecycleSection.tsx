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
  );
}
