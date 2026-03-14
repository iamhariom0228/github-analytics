"use client";

import { useState } from "react";
import { useRepos, useAddRepo, useDeleteRepo, useTriggerSync, useRepoSuggestions } from "@/hooks/useRepos";
import { cn } from "@/lib/utils";
import { RefreshCw, Trash2, Plus, Search } from "lucide-react";
import type { GitHubRepoSuggestion } from "@/types";

const statusColor: Record<string, string> = {
  PENDING: "text-yellow-600 bg-yellow-50",
  SYNCING: "text-blue-600 bg-blue-50",
  DONE: "text-green-600 bg-green-50",
  FAILED: "text-red-600 bg-red-50",
};

export default function ReposPage() {
  const { data: repos, isLoading, error } = useRepos();
  const addRepo = useAddRepo();
  const deleteRepo = useDeleteRepo();
  const triggerSync = useTriggerSync();

  const [showModal, setShowModal] = useState(false);
  const [search, setSearch] = useState("");
  const { data: suggestions } = useRepoSuggestions(showModal);

  const filtered = suggestions?.filter((r: GitHubRepoSuggestion) =>
    r.fullName.toLowerCase().includes(search.toLowerCase())
  );

  const handleAdd = (repo: GitHubRepoSuggestion) => {
    addRepo.mutate(
      { owner: repo.owner.login, name: repo.name },
      { onSuccess: () => setShowModal(false) }
    );
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Repositories</h1>
        <button
          onClick={() => setShowModal(true)}
          className="flex items-center gap-2 bg-primary text-primary-foreground px-4 py-2 rounded-md text-sm"
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
            <div key={i} className="h-16 bg-muted animate-pulse rounded-xl" />
          ))}
        </div>
      ) : repos?.length === 0 ? (
        <div className="text-center py-16 text-muted-foreground">
          No repositories tracked yet. Add one to get started.
        </div>
      ) : (
        <div className="space-y-3">
          {repos?.map((repo) => (
            <div key={repo.id} className="bg-card border border-border rounded-xl p-4 flex items-center justify-between">
              <div>
                <div className="font-medium">{repo.fullName}</div>
                <div className="flex items-center gap-2 mt-1">
                  <span className={cn("text-xs px-2 py-0.5 rounded font-medium", statusColor[repo.syncStatus])}>
                    {repo.syncStatus}
                  </span>
                  {repo.isPrivate && (
                    <span className="text-xs text-muted-foreground border border-border rounded px-2 py-0.5">
                      Private
                    </span>
                  )}
                  {repo.lastSyncedAt && (
                    <span className="text-xs text-muted-foreground">
                      Synced {new Date(repo.lastSyncedAt).toLocaleDateString()}
                    </span>
                  )}
                </div>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => triggerSync.mutate(repo.id)}
                  disabled={repo.syncStatus === "SYNCING"}
                  className="p-2 hover:bg-muted rounded-md disabled:opacity-50"
                  title="Sync now"
                >
                  <RefreshCw className={cn("w-4 h-4", repo.syncStatus === "SYNCING" && "animate-spin")} />
                </button>
                <button
                  onClick={() => deleteRepo.mutate(repo.id)}
                  className="p-2 hover:bg-red-50 text-red-500 rounded-md"
                  title="Remove"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Add Repo Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-card border border-border rounded-xl p-6 w-full max-w-md shadow-xl">
            <h2 className="font-semibold mb-4">Add Repository</h2>
            <div className="relative mb-3">
              <Search className="absolute left-3 top-2.5 w-4 h-4 text-muted-foreground" />
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search your repositories..."
                className="w-full pl-9 pr-3 py-2 border border-input rounded-md text-sm focus:outline-none focus:ring-1 focus:ring-ring"
              />
            </div>
            <div className="space-y-2 max-h-64 overflow-y-auto">
              {filtered?.map((repo: GitHubRepoSuggestion) => (
                <button
                  key={repo.id}
                  onClick={() => handleAdd(repo)}
                  disabled={addRepo.isPending}
                  className="w-full text-left px-3 py-2 hover:bg-muted rounded-md text-sm"
                >
                  <div className="font-medium">{repo.fullName}</div>
                  {repo.privateRepo && <div className="text-xs text-muted-foreground">Private</div>}
                </button>
              ))}
              {!filtered?.length && (
                <p className="text-muted-foreground text-sm text-center py-4">No repositories found</p>
              )}
            </div>
            <div className="flex justify-end mt-4">
              <button
                onClick={() => setShowModal(false)}
                className="text-sm text-muted-foreground hover:text-foreground"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
