"use client";

import { Skeleton } from "@/components/shared/Skeleton";

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
        {[...Array(4)].map((_, i) => <Skeleton key={i} className="h-24 rounded-xl" />)}
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

  const bars = [
    { label: "Approved", value: reviews?.approved ?? 0, color: "bg-green-500" },
    { label: "Changes Requested", value: reviews?.changesRequested ?? 0, color: "bg-yellow-500" },
    { label: "Commented", value: reviews?.commented ?? 0, color: "bg-blue-500" },
  ];

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-card border border-border rounded-xl p-6">
          <div className="text-sm text-muted-foreground">Reviews Given</div>
          <div className="text-3xl font-bold mt-1">{reviews?.totalReviewsGiven ?? 0}</div>
        </div>
        <div className="bg-card border border-border rounded-xl p-6">
          <div className="text-sm text-muted-foreground">Approved</div>
          <div className="text-3xl font-bold mt-1 text-green-500">{reviews?.approved ?? 0}</div>
        </div>
        <div className="bg-card border border-border rounded-xl p-6">
          <div className="text-sm text-muted-foreground">Changes Requested</div>
          <div className="text-3xl font-bold mt-1 text-yellow-500">{reviews?.changesRequested ?? 0}</div>
        </div>
        <div className="bg-card border border-border rounded-xl p-6">
          <div className="text-sm text-muted-foreground">Avg Reviews / PR</div>
          <div className="text-3xl font-bold mt-1">{(reviews?.avgReviewsPerPR ?? 0).toFixed(1)}</div>
        </div>
      </div>

      <div className="bg-card border border-border rounded-xl p-6">
        <h2 className="font-semibold mb-4">Review Breakdown</h2>
        <div className="space-y-3">
          {bars.map(({ label, value, color }) => {
            const total = reviews?.totalReviewsGiven || 1;
            const pct = Math.round((value / total) * 100);
            return (
              <div key={label}>
                <div className="flex justify-between text-sm mb-1">
                  <span>{label}</span>
                  <span className="text-muted-foreground">{value} ({pct}%)</span>
                </div>
                <div className="h-2 bg-muted rounded-full overflow-hidden">
                  <div className={`h-full ${color} rounded-full transition-all`} style={{ width: `${pct}%` }} />
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
