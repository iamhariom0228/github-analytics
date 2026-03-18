"use client";

import { useState } from "react";
import { useCollaboration } from "@/hooks/useAnalytics";
import { formatISO, subDays, subMonths } from "date-fns";
import { Users, GitPullRequest, ArrowRight } from "lucide-react";
import {
  BarChart, Bar, XAxis, YAxis, ResponsiveContainer, Tooltip as ReTooltip, Cell,
} from "recharts";

const RANGES = [
  { label: "Last 30 days", from: () => subDays(new Date(), 30) },
  { label: "Last 90 days", from: () => subDays(new Date(), 90) },
  { label: "Last 6 months", from: () => subMonths(new Date(), 6) },
  { label: "This year", from: () => new Date(new Date().getFullYear(), 0, 1) },
];

function Skeleton({ className = "" }: { className?: string }) {
  return <div className={`animate-pulse bg-muted rounded ${className}`} />;
}

const COLORS = [
  "hsl(var(--primary))",
  "hsl(var(--primary) / 0.85)",
  "hsl(var(--primary) / 0.7)",
  "hsl(var(--primary) / 0.55)",
  "hsl(var(--primary) / 0.4)",
  "hsl(var(--primary) / 0.3)",
  "hsl(var(--primary) / 0.22)",
  "hsl(var(--primary) / 0.16)",
  "hsl(var(--primary) / 0.12)",
  "hsl(var(--primary) / 0.08)",
];

function CollabChart({
  data,
  isLoading,
  emptyLabel,
}: {
  data: { login: string; count: number }[];
  isLoading: boolean;
  emptyLabel: string;
}) {
  if (isLoading) return <div className="space-y-2">{[...Array(5)].map((_, i) => <Skeleton key={i} className="h-8" />)}</div>;
  if (!data.length)
    return (
      <div className="h-48 flex items-center justify-center text-sm text-muted-foreground">
        {emptyLabel}
      </div>
    );

  return (
    <ResponsiveContainer width="100%" height={data.length * 44 + 16}>
      <BarChart data={data} layout="vertical" margin={{ top: 0, right: 24, left: 0, bottom: 0 }}>
        <XAxis type="number" tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} allowDecimals={false} />
        <YAxis type="category" dataKey="login" width={90} tick={{ fontSize: 12, fill: "hsl(var(--foreground))" }} tickLine={false} axisLine={false} />
        <ReTooltip
          contentStyle={{ background: "hsl(var(--card))", border: "1px solid hsl(var(--border))", borderRadius: "8px", fontSize: "12px" }}
          formatter={(v: number) => [v, "reviews"]}
        />
        <Bar dataKey="count" radius={[0, 6, 6, 0]} maxBarSize={28}>
          {data.map((_, i) => (
            <Cell key={i} fill={COLORS[Math.min(i, COLORS.length - 1)]} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

export default function CollaborationPage() {
  const [rangeIdx, setRangeIdx] = useState(1);
  const range = RANGES[rangeIdx];
  const from = formatISO(range.from());
  const to = formatISO(new Date());

  const { data, isLoading } = useCollaboration(from, to);

  const totalReceived = data?.topReviewersOfMe.reduce((s, e) => s + e.count, 0) ?? 0;
  const totalGiven    = data?.topPeopleIReview.reduce((s, e) => s + e.count, 0) ?? 0;

  return (
    <div className="space-y-8 max-w-4xl">
      <div>
        <h1 className="text-2xl font-bold">Collaboration Network</h1>
        <p className="text-muted-foreground text-sm mt-1">
          See who you work closely with through code reviews.
        </p>
      </div>

      {/* Range selector */}
      <div className="flex gap-2 flex-wrap">
        {RANGES.map((r, i) => (
          <button
            key={r.label}
            onClick={() => setRangeIdx(i)}
            className={`px-4 py-2 text-sm rounded-lg border transition-colors ${
              rangeIdx === i
                ? "bg-primary text-primary-foreground border-primary"
                : "border-border text-muted-foreground hover:text-foreground hover:bg-muted/40"
            }`}
          >
            {r.label}
          </button>
        ))}
      </div>

      {/* Summary stats */}
      <div className="grid grid-cols-2 gap-4">
        <div className="bg-card border border-border rounded-xl p-5 flex items-center gap-4">
          <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center flex-shrink-0">
            <Users className="w-5 h-5 text-primary" />
          </div>
          <div>
            <div className="text-xs text-muted-foreground uppercase tracking-wide font-medium">Reviews Received</div>
            {isLoading ? <Skeleton className="h-7 w-12 mt-1" /> : (
              <div className="text-2xl font-bold">{totalReceived}</div>
            )}
          </div>
        </div>
        <div className="bg-card border border-border rounded-xl p-5 flex items-center gap-4">
          <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center flex-shrink-0">
            <GitPullRequest className="w-5 h-5 text-primary" />
          </div>
          <div>
            <div className="text-xs text-muted-foreground uppercase tracking-wide font-medium">Reviews Given</div>
            {isLoading ? <Skeleton className="h-7 w-12 mt-1" /> : (
              <div className="text-2xl font-bold">{totalGiven}</div>
            )}
          </div>
        </div>
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className="bg-card border border-border rounded-xl p-6">
          <div className="flex items-center gap-2 mb-1">
            <h2 className="font-semibold">Who reviews my code</h2>
          </div>
          <p className="text-xs text-muted-foreground mb-5">Top reviewers of your pull requests</p>
          <CollabChart
            data={data?.topReviewersOfMe ?? []}
            isLoading={isLoading}
            emptyLabel="No review data yet for this period"
          />
        </div>

        <div className="bg-card border border-border rounded-xl p-6">
          <div className="flex items-center gap-2 mb-1">
            <h2 className="font-semibold">Who I review for</h2>
          </div>
          <p className="text-xs text-muted-foreground mb-5">Authors whose PRs you review most</p>
          <CollabChart
            data={data?.topPeopleIReview ?? []}
            isLoading={isLoading}
            emptyLabel="No reviews given yet for this period"
          />
        </div>
      </div>

      {/* Mutual collaborators */}
      {!isLoading && data && (() => {
        const myReviewers = new Set(data.topReviewersOfMe.map(e => e.login));
        const mutual = data.topPeopleIReview.filter(e => myReviewers.has(e.login));
        if (!mutual.length) return null;
        return (
          <div className="bg-muted/20 border border-border rounded-xl p-5">
            <h3 className="font-medium text-sm mb-3">Mutual collaborators</h3>
            <div className="flex flex-wrap gap-2">
              {mutual.map(e => (
                <span key={e.login} className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-primary/10 text-primary text-xs font-medium border border-primary/20">
                  <ArrowRight className="w-3 h-3" />
                  {e.login}
                </span>
              ))}
            </div>
            <p className="text-xs text-muted-foreground mt-2">These teammates both review your code and have their code reviewed by you.</p>
          </div>
        );
      })()}
    </div>
  );
}
