"use client";

import { useState } from "react";
import { useRepos, useAddRepo, useDeleteRepo, useTriggerSync, useRepoSuggestions } from "@/hooks/useRepos";
import { useRepoHealth } from "@/hooks/useAnalytics";
import { cn } from "@/lib/utils";
import { RefreshCw, Trash2, Plus, Search, ShieldCheck, ShieldAlert, ShieldX, ChevronDown, ChevronUp } from "lucide-react";
import type { GitHubRepoSuggestion, RepoHealth } from "@/types";

const statusColor: Record<string, string> = {
  PENDING: "text-yellow-600 bg-yellow-50 dark:bg-yellow-950/30",
  SYNCING: "text-blue-600 bg-blue-50 dark:bg-blue-950/30",
  DONE: "text-green-600 bg-green-50 dark:bg-green-950/30",
  FAILED: "text-red-600 bg-red-50 dark:bg-red-950/30",
};

const healthConfig = {
  "Healthy": { icon: ShieldCheck, color: "text-green-500", bg: "bg-green-500/10" },
  "At Risk": { icon: ShieldAlert, color: "text-yellow-500", bg: "bg-yellow-500/10" },
  "Needs Attention": { icon: ShieldX, color: "text-red-500", bg: "bg-red-500/10" },
};

function HealthBadge({ repoId, syncStatus, expanded, onToggle }: {
  repoId: string; syncStatus: string; expanded: boolean; onToggle: () => void;
}) {
  const { data: health, isLoading } = useRepoHealth(repoId);

  if (syncStatus !== "DONE" || isLoading) return null;
  if (!health) return null;

  const cfg = healthConfig[health.label] ?? healthConfig["Needs Attention"];
  const Icon = cfg.icon;

  return (
    <div className="relative">
      <button
        onClick={onToggle}
        className={cn("flex items-center gap-1.5 text-xs px-2 py-0.5 rounded font-medium border", cfg.bg, cfg.color, "border-current/20")}
      >
        <Icon className="w-3 h-3" />
        {health.score}/100
        {expanded ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
      </button>

      {expanded && (
        <div className="absolute top-7 left-0 z-10 w-72 bg-card border border-border rounded-xl shadow-xl p-4 space-y-3">
          <div className="flex items-center justify-between">
            <div className="font-semibold text-sm">{health.label}</div>
            <div className={cn("text-lg font-bold", cfg.color)}>{health.score}/100</div>
          </div>
          <div className="space-y-2">
            {health.signals.map((s) => (
              <div key={s.name} className="flex items-start gap-2 text-xs">
                <span className={s.passed ? "text-green-500 mt-0.5" : "text-red-400 mt-0.5"}>
                  {s.passed ? "✓" : "✗"}
                </span>
                <div>
                  <div className={s.passed ? "text-foreground" : "text-muted-foreground"}>{s.name}</div>
                  <div className="text-muted-foreground">{s.detail}</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export default function ReposPage() {
  const { data: repos, isLoading, error } = useRepos();
  const addRepo = useAddRepo();
  const deleteRepo = useDeleteRepo();
  const triggerSync = useTriggerSync();

  const [showModal, setShowModal] = useState(false);
  const [expandedHealthId, setExpandedHealthId] = useState<string | null>(null);
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
            <div key={repo.id} className="bg-card border border-border rounded-xl p-4 flex items-center justify-between gap-4">
              <div className="min-w-0 flex-1">
                <div className="font-medium truncate">{repo.fullName}</div>
                <div className="flex items-center gap-2 mt-1.5 flex-wrap">
                  <span className={cn("text-xs px-2 py-0.5 rounded font-medium", statusColor[repo.syncStatus])}>
                    {repo.syncStatus}
                  </span>
                  {repo.isPrivate && (
                    <span className="text-xs text-muted-foreground border border-border rounded px-2 py-0.5">Private</span>
                  )}
                  {repo.lastSyncedAt && (
                    <span className="text-xs text-muted-foreground">
                      Synced {new Date(repo.lastSyncedAt).toLocaleDateString()}
                    </span>
                  )}
                  <HealthBadge
                    repoId={repo.id}
                    syncStatus={repo.syncStatus}
                    expanded={expandedHealthId === repo.id}
                    onToggle={() => setExpandedHealthId(expandedHealthId === repo.id ? null : repo.id)}
                  />
                </div>
              </div>
              <div className="flex items-center gap-2 shrink-0">
                <button
                  onClick={() => triggerSync.mutate(repo.id)}
                  disabled={repo.syncStatus === "SYNCING"}
                  className="p-2 hover:bg-muted rounded-md disabled:opacity-50 transition-colors"
                  title="Sync now"
                >
                  <RefreshCw className={cn("w-4 h-4", repo.syncStatus === "SYNCING" && "animate-spin")} />
                </button>
                <button
                  onClick={() => deleteRepo.mutate(repo.id)}
                  className="p-2 hover:bg-red-50 dark:hover:bg-red-950/30 text-red-500 rounded-md transition-colors"
                  title="Remove"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

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
                className="w-full pl-9 pr-3 py-2 border border-input rounded-md text-sm bg-background focus:outline-none focus:ring-1 focus:ring-ring"
              />
            </div>
            <div className="space-y-2 max-h-64 overflow-y-auto">
              {filtered?.map((repo: GitHubRepoSuggestion) => (
                <button
                  key={repo.id}
                  onClick={() => handleAdd(repo)}
                  disabled={addRepo.isPending}
                  className="w-full text-left px-3 py-2 hover:bg-muted rounded-md text-sm transition-colors"
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
              <button onClick={() => setShowModal(false)} className="text-sm text-muted-foreground hover:text-foreground">
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
