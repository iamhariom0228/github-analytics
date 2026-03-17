"use client";

import { cn } from "@/lib/utils";
import { RefreshCw, Trash2, Star, GitFork } from "lucide-react";
import { HealthBadge } from "./HealthBadge";

const statusColor: Record<string, string> = {
  PENDING: "text-yellow-600 bg-yellow-50 dark:bg-yellow-950/30",
  SYNCING: "text-blue-600 bg-blue-50 dark:bg-blue-950/30",
  DONE: "text-green-600 bg-green-50 dark:bg-green-950/30",
  FAILED: "text-red-600 bg-red-50 dark:bg-red-950/30",
};

interface Repo {
  id: string;
  fullName: string;
  description?: string | null;
  syncStatus: string;
  isPrivate?: boolean;
  lastSyncedAt?: string | null;
  language?: string | null;
  stars?: number | null;
  forks?: number | null;
}

interface Props {
  repo: Repo;
  expandedHealthId: string | null;
  onToggleHealth: (id: string) => void;
  onSync: () => void;
  onDelete: () => void;
}

export function RepoCard({ repo, expandedHealthId, onToggleHealth, onSync, onDelete }: Props) {
  return (
    <div className="bg-card border border-border rounded-xl p-4 flex items-center justify-between gap-4">
      <div className="min-w-0 flex-1">
        <div className="font-medium truncate">{repo.fullName}</div>
        {repo.description && (
          <div className="text-xs text-muted-foreground truncate mt-0.5">{repo.description}</div>
        )}
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
            onToggle={() => onToggleHealth(repo.id)}
          />
        </div>
        <div className="flex items-center gap-3 mt-1.5 flex-wrap">
          {repo.language && (
            <span className="flex items-center gap-1 text-xs text-muted-foreground">
              <span className="w-2.5 h-2.5 rounded-full bg-blue-400 inline-block" />
              {repo.language}
            </span>
          )}
          {(repo.stars ?? 0) > 0 && (
            <span className="flex items-center gap-1 text-xs text-muted-foreground">
              <Star className="w-3 h-3" />
              {repo.stars!.toLocaleString()}
            </span>
          )}
          {(repo.forks ?? 0) > 0 && (
            <span className="flex items-center gap-1 text-xs text-muted-foreground">
              <GitFork className="w-3 h-3" />
              {repo.forks!.toLocaleString()}
            </span>
          )}
        </div>
      </div>
      <div className="flex items-center gap-2 shrink-0">
        <button
          onClick={onSync}
          disabled={repo.syncStatus === "SYNCING"}
          className="p-2 hover:bg-muted rounded-md disabled:opacity-50 transition-colors"
          title="Sync now"
        >
          <RefreshCw className={cn("w-4 h-4", repo.syncStatus === "SYNCING" && "animate-spin")} />
        </button>
        <button
          onClick={onDelete}
          className="p-2 hover:bg-red-50 dark:hover:bg-red-950/30 text-red-500 rounded-md transition-colors"
          title="Remove"
        >
          <Trash2 className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}
