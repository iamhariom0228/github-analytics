import { useQuery, useMutation } from "@tanstack/react-query";
import {
  getDashboard,
  getHeatmap,
  getPRLifecycle,
  getPRSizeDistribution,
  getReviewsSummary,
  getStreak,
  getLeaderboard,
  getBusFactor,
  getStalePRs,
  getInsights,
  getCommitTrend,
  getOverview,
  getRepoHealth,
  getAiSummary,
  getActivityFeed,
  getCollaboration,
  getPublicRepoStats,
  getRepoCommitTrend,
  getPRMergeRateTrend,
  getReviewerCoverage,
  getChurnLeaderboard,
  getStarsForksTrend,
  getReleaseTrend,
  getIssueAnalytics,
  getLanguageBytes,
  compareRepos,
  compareContributors,
  createShare,
  getPublicShare,
} from "@/lib/api/client";

// Analytics data changes infrequently — 5 min staleTime prevents refetch on every window focus
const ANALYTICS_STALE_MS = 5 * 60 * 1000;

export function useDashboard() {
  return useQuery({
    queryKey: ["dashboard"],
    queryFn: getDashboard,
    staleTime: ANALYTICS_STALE_MS,
  });
}

export function useHeatmap(repoId?: string, timezone?: string, from?: string, to?: string) {
  return useQuery({
    queryKey: ["heatmap", repoId, timezone, from, to],
    queryFn: () => getHeatmap(repoId, timezone, from, to),
    staleTime: ANALYTICS_STALE_MS,
  });
}

export function usePRLifecycle(from: string, to: string, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ["pr-lifecycle", from, to],
    queryFn: () => getPRLifecycle(from, to),
    staleTime: ANALYTICS_STALE_MS,
    ...options,
  });
}

export function useReviewsSummary(from: string, to: string) {
  return useQuery({
    queryKey: ["reviews-summary", from, to],
    queryFn: () => getReviewsSummary(from, to),
    staleTime: ANALYTICS_STALE_MS,
  });
}

export function usePRSizeDistribution(from: string, to: string) {
  return useQuery({
    queryKey: ["pr-size-distribution", from, to],
    queryFn: () => getPRSizeDistribution(from, to),
    staleTime: ANALYTICS_STALE_MS,
  });
}

export function useStreak(timezone?: string) {
  return useQuery({
    queryKey: ["streak", timezone],
    queryFn: () => getStreak(timezone),
    staleTime: ANALYTICS_STALE_MS,
  });
}

export function useLeaderboard(repoId: string, from: string, to: string) {
  return useQuery({
    queryKey: ["leaderboard", repoId, from, to],
    queryFn: () => getLeaderboard(repoId, from, to),
    enabled: !!repoId,
    staleTime: ANALYTICS_STALE_MS,
  });
}

export function useBusFactor(repoId: string) {
  return useQuery({
    queryKey: ["bus-factor", repoId],
    queryFn: () => getBusFactor(repoId),
    enabled: !!repoId,
    staleTime: ANALYTICS_STALE_MS,
  });
}

export function useStalePRs(repoId: string, days = 7) {
  return useQuery({
    queryKey: ["stale-prs", repoId, days],
    queryFn: () => getStalePRs(repoId, days),
    enabled: !!repoId,
    staleTime: ANALYTICS_STALE_MS,
  });
}

export function useInsights(timezone?: string, from?: string, to?: string) {
  return useQuery({
    queryKey: ["insights", timezone, from, to],
    queryFn: () => getInsights(timezone, from, to),
    staleTime: ANALYTICS_STALE_MS,
  });
}

export function useCommitTrend(from: string, to: string, granularity = "daily", options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ["commit-trend", from, to, granularity],
    queryFn: () => getCommitTrend(from, to, granularity),
    staleTime: ANALYTICS_STALE_MS,
    ...options,
  });
}

export function useCollaboration(from: string, to: string) {
  return useQuery({
    queryKey: ["collaboration", from, to],
    queryFn: () => getCollaboration(from, to),
    staleTime: ANALYTICS_STALE_MS,
  });
}

export function usePublicRepoStats(repoId: string) {
  return useQuery({
    queryKey: ["public-repo-stats", repoId],
    queryFn: () => getPublicRepoStats(repoId),
    enabled: !!repoId,
    staleTime: ANALYTICS_STALE_MS,
  });
}

export function useRepoCommitTrend(repoId: string, from: string, to: string) {
  return useQuery({
    queryKey: ["repo-commit-trend", repoId, from, to],
    queryFn: () => getRepoCommitTrend(repoId, from, to),
    enabled: !!repoId,
    staleTime: ANALYTICS_STALE_MS,
  });
}

export function useOverview(from: string, to: string, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ["overview", from, to],
    queryFn: () => getOverview(from, to),
    staleTime: ANALYTICS_STALE_MS,
    ...options,
  });
}

export function useRepoHealth(repoId: string) {
  return useQuery({
    queryKey: ["repo-health", repoId],
    queryFn: () => getRepoHealth(repoId),
    enabled: !!repoId,
    staleTime: ANALYTICS_STALE_MS,
  });
}

export function useAiSummary(timezone?: string, from?: string, to?: string) {
  return useQuery({
    queryKey: ["ai-summary", timezone, from, to],
    queryFn: () => getAiSummary(timezone, from, to),
    staleTime: 6 * 60 * 60 * 1000, // 6 hours — AI summaries are expensive, cache aggressively
  });
}

export function useActivityFeed(limit = 40) {
  return useQuery({
    queryKey: ["activity-feed", limit],
    queryFn: () => getActivityFeed(limit),
    staleTime: ANALYTICS_STALE_MS,
  });
}

export function usePRMergeRateTrend(from: string, to: string) {
  return useQuery({
    queryKey: ["pr-merge-rate-trend", from, to],
    queryFn: () => getPRMergeRateTrend(from, to),
    staleTime: ANALYTICS_STALE_MS,
  });
}

export function useReviewerCoverage(from: string, to: string) {
  return useQuery({
    queryKey: ["reviewer-coverage", from, to],
    queryFn: () => getReviewerCoverage(from, to),
    staleTime: ANALYTICS_STALE_MS,
  });
}

export function useChurnLeaderboard(repoId: string, from: string, to: string) {
  return useQuery({
    queryKey: ["churn-leaderboard", repoId, from, to],
    queryFn: () => getChurnLeaderboard(repoId, from, to),
    enabled: !!repoId,
    staleTime: ANALYTICS_STALE_MS,
  });
}

export function useStarsForksTrend(repoId?: string) {
  return useQuery({
    queryKey: ["stars-forks-trend", repoId],
    queryFn: () => getStarsForksTrend(repoId!),
    enabled: !!repoId,
    staleTime: ANALYTICS_STALE_MS,
  });
}

export function useReleaseTrend(repoId?: string) {
  return useQuery({
    queryKey: ["release-trend", repoId],
    queryFn: () => getReleaseTrend(repoId!),
    enabled: !!repoId,
    staleTime: ANALYTICS_STALE_MS,
  });
}

export function useIssueAnalytics(repoId?: string) {
  return useQuery({
    queryKey: ["issue-analytics", repoId],
    queryFn: () => getIssueAnalytics(repoId!),
    enabled: !!repoId,
    staleTime: ANALYTICS_STALE_MS,
  });
}

export function useLanguageBytes(repoId?: string) {
  return useQuery({
    queryKey: ["language-bytes", repoId],
    queryFn: () => getLanguageBytes(repoId!),
    enabled: !!repoId,
    staleTime: ANALYTICS_STALE_MS,
  });
}

export function useCompareRepos(repoIds: string[]) {
  return useQuery({
    queryKey: ["compare-repos", ...repoIds],
    queryFn: () => compareRepos(repoIds),
    enabled: repoIds.length >= 2,
    staleTime: ANALYTICS_STALE_MS,
  });
}

export function useCompareContributors(logins: string[]) {
  return useQuery({
    queryKey: ["compare-contributors", ...logins],
    queryFn: () => compareContributors(logins),
    enabled: logins.filter(Boolean).length >= 2,
    staleTime: ANALYTICS_STALE_MS,
  });
}

export function useCreateShare() {
  return useMutation({
    mutationFn: ({ timezone, from, to }: { timezone?: string; from?: string; to?: string }) =>
      createShare(timezone, from, to),
  });
}

export function usePublicShare(token: string) {
  return useQuery({
    queryKey: ["public-share", token],
    queryFn: () => getPublicShare(token),
    enabled: !!token,
    staleTime: 10 * 60 * 1000,
  });
}
