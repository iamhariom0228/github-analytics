"use client";

import { Skeleton } from "@/components/shared/Skeleton";
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from "recharts";

interface ReviewsSummary {
  totalReviewsGiven: number;
  approved: number;
  changesRequested: number;
  commented: number;
  avgReviewsPerPR: number;
}

interface Props {
  reviews: ReviewsSummary | undefined;
  isLoading: boolean;
}

export function ReviewsSection({ reviews, isLoading }: Props) {
  if (isLoading) {
    return (
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {[...Array(4)].map((_, i) => <Skeleton key={i} className="h-20 sm:h-24 rounded-xl" />)}
      </div>
    );
  }

  if ((reviews?.totalReviewsGiven ?? 0) === 0) {
    return (
      <div className="bg-card border border-border rounded-xl p-12 text-center text-muted-foreground">
        No reviews given in this period. Try selecting a wider date range.
      </div>
    );
  }

  const pieData = [
    { name: "Approved", value: reviews?.approved ?? 0, color: "#22c55e" },
    { name: "Changes Requested", value: reviews?.changesRequested ?? 0, color: "#eab308" },
    { name: "Commented", value: reviews?.commented ?? 0, color: "#3b82f6" },
  ];

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-card border border-border rounded-xl p-4 sm:p-6">
          <div className="text-sm text-muted-foreground">Reviews Given</div>
          <div className="text-2xl sm:text-3xl font-bold mt-1">{reviews?.totalReviewsGiven ?? 0}</div>
        </div>
        <div className="bg-card border border-border rounded-xl p-4 sm:p-6">
          <div className="text-sm text-muted-foreground">Approved</div>
          <div className="text-2xl sm:text-3xl font-bold mt-1 text-green-500">{reviews?.approved ?? 0}</div>
        </div>
        <div className="bg-card border border-border rounded-xl p-4 sm:p-6">
          <div className="text-sm text-muted-foreground">Changes Requested</div>
          <div className="text-2xl sm:text-3xl font-bold mt-1 text-yellow-500">{reviews?.changesRequested ?? 0}</div>
        </div>
        <div className="bg-card border border-border rounded-xl p-4 sm:p-6">
          <div className="text-sm text-muted-foreground">Avg Reviews / PR</div>
          <div className="text-2xl sm:text-3xl font-bold mt-1">{(reviews?.avgReviewsPerPR ?? 0).toFixed(1)}</div>
        </div>
      </div>

      <div className="bg-card border border-border rounded-xl p-6">
        <h2 className="font-semibold mb-4">Review Breakdown</h2>
        <div className="flex items-center gap-4 sm:gap-8 flex-wrap">
          <div className="flex-shrink-0 w-full sm:w-[220px]">
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie data={pieData} cx="50%" cy="50%" innerRadius={60} outerRadius={95} paddingAngle={3} dataKey="value" strokeWidth={0}>
                  {pieData.map((entry) => (
                    <Cell key={entry.name} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip contentStyle={{ background: "hsl(var(--card))", border: "1px solid hsl(var(--border))", borderRadius: "8px", fontSize: "12px" }} formatter={(v: number) => [v, ""]} />
              </PieChart>
            </ResponsiveContainer>
          </div>
          <div className="flex-1 min-w-0 space-y-3">
            {pieData.map(({ name, value, color }) => {
              const total = reviews?.totalReviewsGiven || 1;
              const pct = Math.round((value / total) * 100);
              return (
                <div key={name} className="space-y-1">
                  <div className="flex items-center gap-2 text-sm">
                    <div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ backgroundColor: color }} />
                    <span className="flex-1">{name}</span>
                    <span className="font-semibold">{value}</span>
                    <span className="text-muted-foreground w-9 text-right">{pct}%</span>
                  </div>
                  <div className="h-1.5 bg-muted rounded-full overflow-hidden ml-4">
                    <div className="h-full rounded-full transition-all duration-500" style={{ width: `${pct}%`, backgroundColor: color }} />
                  </div>
                </div>
              );
            })}
            <div className="pt-2 border-t border-border text-sm text-muted-foreground">
              {reviews?.totalReviewsGiven ?? 0} total reviews · {(reviews?.avgReviewsPerPR ?? 0).toFixed(1)} per PR
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
