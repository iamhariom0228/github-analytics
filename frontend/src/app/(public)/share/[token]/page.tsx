"use client";

import { use } from "react";
import { usePublicShare } from "@/hooks/useAnalytics";
import { TrendingUp, GitPullRequest, Star, Code2, Flame } from "lucide-react";
import Link from "next/link";

interface Props {
  params: Promise<{ token: string }>;
}

function StatCard({ label, value, icon: Icon }: { label: string; value: string | number; icon: React.ElementType }) {
  return (
    <div className="bg-card border border-border rounded-xl p-5">
      <div className="flex items-center gap-2 mb-3">
        <Icon className="w-4 h-4 text-muted-foreground" />
        <span className="text-xs text-muted-foreground uppercase tracking-wide">{label}</span>
      </div>
      <div className="text-2xl font-bold">{value}</div>
    </div>
  );
}

export default function SharePage({ params }: Props) {
  const { token } = use(params);
  const { data, isLoading, error } = usePublicShare(token);

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="text-muted-foreground animate-pulse">Loading snapshot…</div>
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="text-center space-y-4">
          <h1 className="text-2xl font-bold">Snapshot Not Found</h1>
          <p className="text-muted-foreground">This snapshot may have expired or doesn&apos;t exist.</p>
          <Link href="/" className="text-primary hover:underline text-sm">Go to homepage</Link>
        </div>
      </div>
    );
  }

  const from = data.periodFrom ? new Date(data.periodFrom).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" }) : "";
  const to = data.periodTo ? new Date(data.periodTo).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" }) : "";

  return (
    <div className="min-h-screen bg-background">
      <div className="max-w-2xl mx-auto px-4 py-12 space-y-8">
        {/* Header */}
        <div className="text-center space-y-2">
          <div className="text-xs text-muted-foreground uppercase tracking-widest mb-2">Developer Snapshot</div>
          <h1 className="text-3xl font-bold">{data.login}</h1>
          {from && to && (
            <p className="text-muted-foreground text-sm">{from} — {to}</p>
          )}
        </div>

        {/* Stats grid */}
        <div className="grid grid-cols-2 gap-4">
          <StatCard label="Commits" value={data.commits ?? 0} icon={TrendingUp} />
          <StatCard label="PRs Authored" value={data.prsAuthored ?? 0} icon={GitPullRequest} />
          <StatCard label="Reviews Given" value={data.reviewsGiven ?? 0} icon={Star} />
          <StatCard label="Lines Added" value={(data.linesAdded ?? 0).toLocaleString()} icon={Code2} />
        </div>

        {/* Streak */}
        <div className="bg-card border border-border rounded-xl p-6 flex items-center gap-5">
          <div className="w-12 h-12 rounded-xl bg-orange-500/10 flex items-center justify-center">
            <Flame className="w-6 h-6 text-orange-500" />
          </div>
          <div>
            <p className="text-sm text-muted-foreground">Current Streak</p>
            <p className="text-2xl font-bold">{data.currentStreak ?? 0} days</p>
            <p className="text-xs text-muted-foreground mt-0.5">Longest: {data.longestStreak ?? 0} days</p>
          </div>
        </div>

        {/* CTA */}
        <div className="bg-primary/10 border border-primary/30 rounded-xl p-5 text-center space-y-3">
          <p className="text-sm font-medium">Track your own GitHub analytics</p>
          <Link
            href="/"
            className="inline-block bg-primary text-primary-foreground hover:opacity-90 px-6 py-2.5 rounded-lg text-sm font-medium transition"
          >
            Get Started Free
          </Link>
        </div>
      </div>
    </div>
  );
}
