"use client";

import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from "recharts";
import type { IssueAnalytics } from "@/types";

interface Props {
  repoId?: string;
  data: IssueAnalytics | undefined;
  isLoading: boolean;
}

function Skeleton({ className = "" }: { className?: string }) {
  return <div className={`animate-pulse bg-muted rounded ${className}`} />;
}

export function IssuesSection({ repoId, data, isLoading }: Props) {
  if (!repoId) {
    return (
      <div className="bg-card border border-border rounded-xl p-12 text-center text-muted-foreground">
        No repositories tracked yet. Add a repository to see issue analytics.
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="space-y-4">
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
          {[...Array(3)].map((_, i) => <Skeleton key={i} className="h-24 rounded-xl" />)}
        </div>
        <Skeleton className="h-48 rounded-xl" />
      </div>
    );
  }

  if (!data) {
    return (
      <div className="bg-card border border-border rounded-xl p-12 text-center text-muted-foreground">
        No issue data available. Sync your repository to see issue analytics.
      </div>
    );
  }

  const total = data.openCount + data.closedCount;
  const closedPct = total > 0 ? Math.round((data.closedCount / total) * 100) : 0;

  return (
    <div className="space-y-4">
      {/* Summary cards */}
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
        <div className="bg-card border border-border rounded-xl p-4 sm:p-5">
          <div className="text-xs text-muted-foreground uppercase tracking-wide mb-2">Open Issues</div>
          <div className="text-2xl sm:text-3xl font-bold text-orange-500">{data.openCount.toLocaleString()}</div>
        </div>
        <div className="bg-card border border-border rounded-xl p-4 sm:p-5">
          <div className="text-xs text-muted-foreground uppercase tracking-wide mb-2">Closed Issues</div>
          <div className="text-2xl sm:text-3xl font-bold text-green-500">{data.closedCount.toLocaleString()}</div>
          <div className="text-xs text-muted-foreground mt-1">{closedPct}% closed</div>
        </div>
        <div className="bg-card border border-border rounded-xl p-4 sm:p-5">
          <div className="text-xs text-muted-foreground uppercase tracking-wide mb-2">Avg Close Time</div>
          <div className="text-2xl sm:text-3xl font-bold">
            {data.avgCloseTimeDays != null ? `${data.avgCloseTimeDays.toFixed(1)}d` : "—"}
          </div>
        </div>
      </div>

      {/* Age distribution */}
      {data.ageDistribution.length > 0 && (
        <div className="bg-card border border-border rounded-xl p-6">
          <h2 className="font-semibold mb-1">Open Issues by Age</h2>
          <p className="text-xs text-muted-foreground mb-5">Distribution of currently open issues by how long they&apos;ve been open.</p>
          <ResponsiveContainer width="100%" height={200}>
            <BarChart
              data={data.ageDistribution.map((b) => ({ bucket: b.label, Count: b.count }))}
              margin={{ top: 4, right: 4, left: -16, bottom: 0 }}
            >
              <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
              <XAxis dataKey="bucket" tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} />
              <YAxis tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} allowDecimals={false} />
              <Tooltip
                contentStyle={{ backgroundColor: "hsl(var(--card))", border: "1px solid hsl(var(--border))", borderRadius: "8px", fontSize: "12px" }}
              />
              <Bar dataKey="Count" fill="hsl(var(--primary))" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  );
}
