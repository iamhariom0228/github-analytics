"use client";

import { useState } from "react";
import { useRepos, useAddRepo, useDeleteRepo, useTriggerSync, useRepoSuggestions } from "@/hooks/useRepos";
import { Plus } from "lucide-react";
import type { GitHubRepoSuggestion } from "@/types";
import { RepoCard } from "./_components/RepoCard";
import { AddRepoModal } from "./_components/AddRepoModal";

export default function ReposPage() {
  const { data: repos, isLoading, error } = useRepos();
  const addRepo = useAddRepo();
  const deleteRepo = useDeleteRepo();
  const triggerSync = useTriggerSync();

  const [showModal, setShowModal] = useState(false);
  const [expandedHealthId, setExpandedHealthId] = useState<string | null>(null);
  const { data: suggestions } = useRepoSuggestions(showModal);

  const handleAdd = (repo: GitHubRepoSuggestion) => {
    addRepo.mutate(
      { owner: repo.owner.login, name: repo.name },
      { onSuccess: () => setShowModal(false) }
    );
  };

  const toggleHealth = (id: string) =>
    setExpandedHealthId((prev) => (prev === id ? null : id));

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <h1 className="text-2xl font-bold">Repositories</h1>
        <button
          onClick={() => setShowModal(true)}
          className="flex items-center gap-2 bg-primary text-primary-foreground px-4 py-2 rounded-md text-sm self-start sm:self-auto"
        >
          <Plus className="w-4 h-4" /> Add Repository
        </button>
      </div>

      {error ? (
        <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-6 text-center">
          <p className="text-sm text-destructive">Failed to load repositories. Check your connection and try again.</p>
        </div>
      ) : isLoading ? (
        <div className="space-y-3">
          {[...Array(3)].map((_, i) => (
            <div key={i} className="h-20 bg-muted animate-pulse rounded-xl" />
          ))}
        </div>
      ) : repos?.length === 0 ? (
        <div className="text-center py-16 border border-dashed border-border rounded-xl text-muted-foreground">
          <Plus className="w-8 h-8 mx-auto mb-3 opacity-40" />
          <p className="font-medium">No repositories tracked yet</p>
          <p className="text-sm mt-1">Add a repository to start seeing analytics</p>
        </div>
      ) : (
        <div className="space-y-3">
          {repos?.map((repo) => (
            <RepoCard
              key={repo.id}
              repo={repo}
              expandedHealthId={expandedHealthId}
              onToggleHealth={toggleHealth}
              onSync={() => triggerSync.mutate(repo.id)}
              onDelete={() => deleteRepo.mutate(repo.id)}
            />
          ))}
        </div>
      )}

      {showModal && (
        <AddRepoModal
          suggestions={suggestions}
          isPending={addRepo.isPending}
          onAdd={handleAdd}
          onClose={() => setShowModal(false)}
        />
      )}
    </div>
  );
}
