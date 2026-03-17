"use client";

import { useState } from "react";
import { Search } from "lucide-react";
import type { GitHubRepoSuggestion } from "@/types";

interface Props {
  suggestions: GitHubRepoSuggestion[] | undefined;
  isPending: boolean;
  onAdd: (repo: GitHubRepoSuggestion) => void;
  onClose: () => void;
}

export function AddRepoModal({ suggestions, isPending, onAdd, onClose }: Props) {
  const [search, setSearch] = useState("");

  const filtered = suggestions?.filter((r) =>
    r.fullName.toLowerCase().includes(search.toLowerCase())
  );

  return (
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
          {filtered?.map((repo) => (
            <button
              key={repo.id}
              onClick={() => onAdd(repo)}
              disabled={isPending}
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
          <button onClick={onClose} className="text-sm text-muted-foreground hover:text-foreground">
            Cancel
          </button>
        </div>
      </div>
    </div>
  );
}
