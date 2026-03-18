"use client";

import { useQuery } from "@tanstack/react-query";
import apiClient from "@/lib/api/client";
import type { ApiResponse } from "@/types";
import {
  GitPullRequest, Clock, Code, ExternalLink, CheckCircle2, AlertCircle, RefreshCw,
} from "lucide-react";
import { formatDistanceToNow, parseISO } from "date-fns";

interface ReviewQueueItem {
  id: number;
  prNumber: number;
  title: string;
  repoFullName: string;
  authorLogin: string;
  createdAt: string;
  additions: number;
  deletions: number;
  changedFiles: number;
  ageHours: number;
  sizeLabel: "XS" | "S" | "M" | "L" | "XL";
}

function useReviewQueue() {
  return useQuery<ReviewQueueItem[]>({
    queryKey: ["review-queue"],
    queryFn: () =>
      apiClient
        .get<ApiResponse<ReviewQueueItem[]>>("/analytics/review-queue")
        .then((r) => r.data.data),
    staleTime: 5 * 60 * 1000,
  });
}

const SIZE_STYLES: Record<string, string> = {
  XS: "bg-emerald-500/15 text-emerald-600",
  S:  "bg-blue-500/15 text-blue-600",
  M:  "bg-yellow-500/15 text-yellow-700",
  L:  "bg-orange-500/15 text-orange-600",
  XL: "bg-red-500/15 text-red-600",
};

const SIZE_TOOLTIP: Record<string, string> = {
  XS: "< 10 lines",
  S:  "< 50 lines",
  M:  "< 200 lines",
  L:  "< 500 lines",
  XL: "500+ lines",
};

function urgencyColor(ageHours: number) {
  if (ageHours > 72) return "text-red-500";
  if (ageHours > 24) return "text-orange-500";
  return "text-muted-foreground";
}

function Skeleton({ className = "" }: { className?: string }) {
  return <div className={`animate-pulse bg-muted rounded ${className}`} />;
}

function PRCard({ pr }: { pr: ReviewQueueItem }) {
  const ghUrl = `https://github.com/${pr.repoFullName}/pull/${pr.prNumber}`;
  const age = formatDistanceToNow(parseISO(pr.createdAt), { addSuffix: true });
  const isUrgent = pr.ageHours > 72;

  return (
    <div className={`bg-card border rounded-xl p-5 hover:border-primary/40 transition-colors ${isUrgent ? "border-red-500/30" : "border-border"}`}>
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-3 min-w-0">
          <GitPullRequest className="w-4 h-4 text-blue-500 mt-0.5 shrink-0" />
          <div className="min-w-0">
            <a
              href={ghUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="text-sm font-semibold hover:text-primary transition-colors line-clamp-2 leading-snug"
            >
              {pr.title}
            </a>
            <div className="flex items-center gap-2 mt-1 text-xs text-muted-foreground flex-wrap">
              <span className="font-medium text-foreground/80">{pr.repoFullName}</span>
              <span>·</span>
              <span>#{pr.prNumber}</span>
              <span>·</span>
              <span>by <span className="font-medium">{pr.authorLogin}</span></span>
            </div>
          </div>
        </div>

        <a
          href={ghUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="shrink-0 flex items-center gap-1.5 text-xs border border-border px-3 py-1.5 rounded-md hover:bg-muted/60 transition-colors"
        >
          <ExternalLink className="w-3.5 h-3.5" />
          Review
        </a>
      </div>

      <div className="flex items-center gap-3 mt-4 flex-wrap">
        {/* Size badge */}
        <span
          className={`text-xs font-semibold px-2 py-0.5 rounded-full ${SIZE_STYLES[pr.sizeLabel]}`}
          title={SIZE_TOOLTIP[pr.sizeLabel]}
        >
          {pr.sizeLabel}
        </span>

        {/* Code delta */}
        <div className="flex items-center gap-1.5 text-xs">
          <Code className="w-3.5 h-3.5 text-muted-foreground" />
          <span className="text-green-500">+{pr.additions.toLocaleString()}</span>
          <span className="text-red-500">-{pr.deletions.toLocaleString()}</span>
          <span className="text-muted-foreground">({pr.changedFiles} file{pr.changedFiles !== 1 ? "s" : ""})</span>
        </div>

        {/* Age */}
        <div className={`flex items-center gap-1 text-xs ml-auto ${urgencyColor(pr.ageHours)}`}>
          <Clock className="w-3.5 h-3.5" />
          {isUrgent && <AlertCircle className="w-3.5 h-3.5" />}
          Opened {age}
        </div>
      </div>
    </div>
  );
}

export default function ReviewQueuePage() {
  const { data, isLoading, error, refetch, isFetching } = useReviewQueue();

  // Group by repo
  const byRepo = (data ?? []).reduce<Record<string, ReviewQueueItem[]>>((acc, pr) => {
    (acc[pr.repoFullName] ??= []).push(pr);
    return acc;
  }, {});

  const urgent = (data ?? []).filter((pr) => pr.ageHours > 72).length;
  const total = data?.length ?? 0;

  return (
    <div className="space-y-8 max-w-3xl">
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl font-bold">PR Review Queue</h1>
          <p className="text-muted-foreground text-sm mt-1">
            Open pull requests across your tracked repos waiting for your review.
          </p>
        </div>
        <button
          onClick={() => refetch()}
          disabled={isFetching}
          className="flex items-center gap-2 text-sm border border-border px-3 py-1.5 rounded-md hover:bg-muted/60 disabled:opacity-50 transition-colors"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${isFetching ? "animate-spin" : ""}`} />
          Refresh
        </button>
      </div>

      {/* Summary */}
      {!isLoading && data && (
        <div className="grid grid-cols-3 gap-4">
          <div className="bg-card border border-border rounded-xl p-4 text-center">
            <div className="text-2xl font-bold">{total}</div>
            <div className="text-xs text-muted-foreground mt-0.5">PRs to review</div>
          </div>
          <div className="bg-card border border-border rounded-xl p-4 text-center">
            <div className={`text-2xl font-bold ${urgent > 0 ? "text-red-500" : ""}`}>{urgent}</div>
            <div className="text-xs text-muted-foreground mt-0.5">Overdue (&gt;72h)</div>
          </div>
          <div className="bg-card border border-border rounded-xl p-4 text-center">
            <div className="text-2xl font-bold">{Object.keys(byRepo).length}</div>
            <div className="text-xs text-muted-foreground mt-0.5">Repositories</div>
          </div>
        </div>
      )}

      {/* Error */}
      {error && (
        <div className="bg-destructive/10 border border-destructive/30 text-destructive rounded-xl p-4 text-sm">
          Failed to load review queue. Please try refreshing.
        </div>
      )}

      {/* Loading */}
      {isLoading && (
        <div className="space-y-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-28 rounded-xl" />
          ))}
        </div>
      )}

      {/* Empty state */}
      {!isLoading && !error && total === 0 && (
        <div className="bg-card border border-border rounded-xl p-14 text-center">
          <CheckCircle2 className="w-12 h-12 text-green-500 mx-auto mb-4" />
          <p className="font-semibold">You're all caught up!</p>
          <p className="text-muted-foreground text-sm mt-1">
            No open PRs waiting for your review across any tracked repository.
          </p>
        </div>
      )}

      {/* PRs grouped by repo */}
      {!isLoading && !error && total > 0 && (
        <div className="space-y-8">
          {urgent > 0 && (
            <div className="flex items-center gap-2 bg-red-500/10 border border-red-500/30 text-red-600 px-4 py-2.5 rounded-xl text-sm">
              <AlertCircle className="w-4 h-4 shrink-0" />
              {urgent} PR{urgent !== 1 ? "s have" : " has"} been waiting over 72 hours — consider reviewing soon.
            </div>
          )}

          {Object.entries(byRepo).map(([repo, prs]) => (
            <div key={repo}>
              <div className="flex items-center gap-3 mb-3">
                <h2 className="text-sm font-semibold">{repo}</h2>
                <span className="text-xs text-muted-foreground bg-muted px-2 py-0.5 rounded-full">
                  {prs.length} PR{prs.length !== 1 ? "s" : ""}
                </span>
                <div className="flex-1 h-px bg-border" />
              </div>
              <div className="space-y-3">
                {prs.map((pr) => <PRCard key={pr.id} pr={pr} />)}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
