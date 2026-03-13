import { useQuery } from "@tanstack/react-query";
import {
  getDashboard,
  getHeatmap,
  getPRLifecycle,
  getPRSizeDistribution,
  getStreak,
  getLeaderboard,
  getBusFactor,
  getStalePRs,
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
