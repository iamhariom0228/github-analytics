import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { getRepos, addRepo, deleteRepo, triggerSync, getSyncStatus, getRepoSuggestions } from "@/lib/api/client";

export function useRepos() {
  return useQuery({ queryKey: ["repos"], queryFn: getRepos });
}

export function useRepoSuggestions(enabled: boolean) {
  return useQuery({
    queryKey: ["repo-suggestions"],
    queryFn: getRepoSuggestions,
    enabled,
  });
}

export function useAddRepo() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ owner, name }: { owner: string; name: string }) =>
      addRepo(owner, name),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["repos"] }),
  });
}

export function useDeleteRepo() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteRepo(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["repos"] }),
  });
}

export function useTriggerSync() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => triggerSync(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["repos"] }),
  });
}

export function useSyncStatus(repoId: string, enabled: boolean) {
  return useQuery({
    queryKey: ["sync-status", repoId],
    queryFn: () => getSyncStatus(repoId),
    enabled,
    refetchInterval: 3000,
  });
}
