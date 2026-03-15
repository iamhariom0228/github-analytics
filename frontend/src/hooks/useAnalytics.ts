import { useQuery } from "@tanstack/react-query";
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
} from "@/lib/api/client";

export function useDashboard() {
  return useQuery({ queryKey: ["dashboard"], queryFn: getDashboard });
}

export function useHeatmap(repoId?: string, timezone?: string) {
  return useQuery({
    queryKey: ["heatmap", repoId, timezone],
    queryFn: () => getHeatmap(repoId, timezone),
  });
}

export function usePRLifecycle(from: string, to: string) {
  return useQuery({
    queryKey: ["pr-lifecycle", from, to],
    queryFn: () => getPRLifecycle(from, to),
  });
}

export function useReviewsSummary(from: string, to: string) {
  return useQuery({
    queryKey: ["reviews-summary", from, to],
    queryFn: () => getReviewsSummary(from, to),
  });
}

export function usePRSizeDistribution(from: string, to: string) {
  return useQuery({
    queryKey: ["pr-size-distribution", from, to],
    queryFn: () => getPRSizeDistribution(from, to),
  });
}

export function useStreak(timezone?: string) {
  return useQuery({
    queryKey: ["streak", timezone],
    queryFn: () => getStreak(timezone),
  });
}

export function useLeaderboard(repoId: string, from: string, to: string) {
  return useQuery({
    queryKey: ["leaderboard", repoId, from, to],
    queryFn: () => getLeaderboard(repoId, from, to),
    enabled: !!repoId,
  });
}

export function useBusFactor(repoId: string) {
  return useQuery({
    queryKey: ["bus-factor", repoId],
    queryFn: () => getBusFactor(repoId),
    enabled: !!repoId,
  });
}

export function useStalePRs(repoId: string, days = 7) {
  return useQuery({
    queryKey: ["stale-prs", repoId, days],
    queryFn: () => getStalePRs(repoId, days),
    enabled: !!repoId,
  });
}

export function useInsights(timezone?: string) {
  return useQuery({
    queryKey: ["insights", timezone],
    queryFn: () => getInsights(timezone),
  });
}

export function useCommitTrend(from: string, to: string, granularity = "daily") {
  return useQuery({
    queryKey: ["commit-trend", from, to, granularity],
    queryFn: () => getCommitTrend(from, to, granularity),
  });
}

export function useOverview(from: string, to: string) {
  return useQuery({
    queryKey: ["overview", from, to],
    queryFn: () => getOverview(from, to),
  });
}

export function useRepoHealth(repoId: string) {
  return useQuery({
    queryKey: ["repo-health", repoId],
    queryFn: () => getRepoHealth(repoId),
    enabled: !!repoId,
  });
}

export function useAiSummary(timezone?: string) {
  return useQuery({
    queryKey: ["ai-summary", timezone],
    queryFn: () => getAiSummary(timezone),
    staleTime: 6 * 60 * 60 * 1000, // 6 hours — matches Redis cache TTL
  });
}
