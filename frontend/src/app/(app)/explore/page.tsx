"use client";

import { useState, useEffect, useRef } from "react";
import {
  Search, Star, GitFork, AlertCircle, Users, GitCommitHorizontal,
  ExternalLink, GitFork as ForkIcon, TrendingUp, Loader2,
} from "lucide-react";
import { useAuth } from "@/hooks/useAuth";
import { forkRepo } from "@/lib/api/client";
import Link from "next/link";

interface GitHubRepo {
  full_name: string;
  description: string | null;
  stargazers_count: number;
  forks_count: number;
  language: string | null;
  open_issues_count: number;
  html_url: string;
}

interface GitHubContributor {
  author: { login: string; avatar_url: string } | null;
  total: number;
  weeks: Array<{ a: number; d: number; c: number }>;
}

interface GitHubSearchResult {
  full_name: string;
  description: string | null;
  stargazers_count: number;
  language: string | null;
}

// Repos pushed in the last 7 days, sorted by stars — real trending data from GitHub
function getTrendingUrl() {
  const since = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().split("T")[0];
  return `https://api.github.com/search/repositories?q=pushed:>${since}&sort=stars&order=desc&per_page=6`;
}

const LANGUAGE_COLORS: Record<string, string> = {
  TypeScript: "bg-blue-500",
  JavaScript: "bg-yellow-400",
  Python: "bg-green-500",
  Java: "bg-orange-500",
  Go: "bg-cyan-500",
  Rust: "bg-orange-700",
  "C++": "bg-pink-500",
  C: "bg-gray-500",
  Ruby: "bg-red-500",
  PHP: "bg-purple-400",
  Swift: "bg-orange-400",
  Kotlin: "bg-violet-500",
};

function LanguageDot({ language }: { language: string }) {
  const colorClass = LANGUAGE_COLORS[language] ?? "bg-slate-400";
  return <span className={`w-2.5 h-2.5 rounded-full inline-block ${colorClass}`} />;
}

function parseRepoInput(input: string): { owner: string; repo: string } | null {
  const trimmed = input.trim();
  const urlMatch = trimmed.match(/github\.com\/([^/]+)\/([^/]+)/);
  if (urlMatch) return { owner: urlMatch[1], repo: urlMatch[2].replace(/\.git$/, "") };
  const shortMatch = trimmed.match(/^([^/]+)\/([^/]+)$/);
  if (shortMatch) return { owner: shortMatch[1], repo: shortMatch[2] };
  return null;
}

function TrendingCard({
  repo,
  onSelect,
}: {
  repo: GitHubRepo;
  onSelect: (name: string) => void;
}) {
  return (
    <button
      onClick={() => onSelect(repo.full_name)}
      className="w-full text-left bg-card border border-border rounded-xl p-4 hover:border-primary/40 hover:bg-muted/30 transition-all group"
    >
      <div className="flex items-start justify-between gap-2 mb-2">
        <span className="text-sm font-semibold text-primary group-hover:underline truncate">
          {repo.full_name}
        </span>
        {repo.language && (
          <span className="flex items-center gap-1.5 text-xs text-muted-foreground shrink-0">
            <LanguageDot language={repo.language} />
            {repo.language}
          </span>
        )}
      </div>
      {repo.description && (
        <p className="text-xs text-muted-foreground line-clamp-2 mb-3">{repo.description}</p>
      )}
      <div className="flex items-center gap-3 text-xs text-muted-foreground">
        <span className="flex items-center gap-1">
          <Star className="w-3 h-3 text-yellow-400" />
          {repo.stargazers_count.toLocaleString()}
        </span>
        <span className="flex items-center gap-1">
          <GitFork className="w-3 h-3" />
          {repo.forks_count.toLocaleString()}
        </span>
      </div>
    </button>
  );
}

export default function ExplorePage() {
  const { data: user } = useAuth();
  const isLoggedIn = !!user;

  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [repoData, setRepoData] = useState<GitHubRepo | null>(null);
  const [contributors, setContributors] = useState<GitHubContributor[]>([]);
  const [totalCommits, setTotalCommits] = useState(0);
  const [suggestions, setSuggestions] = useState<GitHubSearchResult[]>([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [forkLoading, setForkLoading] = useState(false);
  const [forkedUrl, setForkedUrl] = useState<string | null>(null);
  const [forkError, setForkError] = useState<string | null>(null);
  const [trendingRepos, setTrendingRepos] = useState<GitHubRepo[]>([]);
  const [trendingLoading, setTrendingLoading] = useState(true);

  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Load real trending repos on mount — most starred repos active in the last 7 days
  useEffect(() => {
    const fetchTrending = async () => {
      try {
        const res = await fetch(getTrendingUrl());
        if (!res.ok) return;
        const data = await res.json();
        setTrendingRepos(data.items ?? []);
      } catch {
        // silently ignore
      } finally {
        setTrendingLoading(false);
      }
    };
    fetchTrending();
  }, []);

  // Autocomplete
  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    const trimmed = query.trim();
    if (!trimmed || trimmed.includes("/")) {
      setSuggestions([]);
      setShowSuggestions(false);
      return;
    }
    debounceRef.current = setTimeout(async () => {
      try {
        const res = await fetch(
          `https://api.github.com/search/repositories?q=${encodeURIComponent(trimmed)}&sort=stars&per_page=6`
        );
        if (!res.ok) return;
        const data = await res.json();
        setSuggestions(data.items ?? []);
        setShowSuggestions(true);
      } catch {
        // silently ignore
      }
    }, 400);
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [query]);

  const triggerSearch = async (input: string) => {
    const parsed = parseRepoInput(input);
    if (!parsed) {
      setError("Enter a valid GitHub repo, e.g. facebook/react");
      return;
    }
    setLoading(true);
    setError(null);
    setRepoData(null);
    setContributors([]);
    setTotalCommits(0);
    setForkedUrl(null);
    setForkError(null);
    try {
      const repoRes = await fetch(`https://api.github.com/repos/${parsed.owner}/${parsed.repo}`);
      if (repoRes.status === 404) { setError("Repository not found."); return; }
      if (repoRes.status === 403) { setError("GitHub API rate limit reached. Try again shortly."); return; }
      if (!repoRes.ok) { setError("Failed to fetch repository data."); return; }
      const repo: GitHubRepo = await repoRes.json();
      setRepoData(repo);
      const contribRes = await fetch(
        `https://api.github.com/repos/${parsed.owner}/${parsed.repo}/stats/contributors`
      );
      if (contribRes.ok && contribRes.status === 200) {
        const data: GitHubContributor[] = await contribRes.json();
        const sorted = [...data].sort((a, b) => b.total - a.total);
        setContributors(sorted.slice(0, 5));
        setTotalCommits(data.reduce((sum, c) => sum + c.total, 0));
      }
    } catch {
      setError("Network error. Please check your connection.");
    } finally {
      setLoading(false);
    }
  };

  const handleSelectSuggestion = (fullName: string) => {
    setQuery(fullName);
    setShowSuggestions(false);
    setSuggestions([]);
    triggerSearch(fullName);
  };

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setShowSuggestions(false);
    triggerSearch(query);
  };

  const handleFork = async () => {
    if (!repoData) return;
    const [owner, repo] = repoData.full_name.split("/");
    setForkLoading(true);
    setForkError(null);
    try {
      const result = await forkRepo(owner, repo);
      setForkedUrl(result.htmlUrl);
    } catch {
      setForkError("Fork failed. You may already have a fork of this repo.");
    } finally {
      setForkLoading(false);
    }
  };

  return (
    <div className="space-y-8 max-w-3xl">
      <div>
        <h1 className="text-2xl font-bold">Explore</h1>
        <p className="text-muted-foreground text-sm mt-1">
          Search any public GitHub repository and get an instant analytics snapshot.
        </p>
      </div>

      {/* Search */}
      <form onSubmit={handleSearch} className="flex gap-2">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground z-10" />
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onBlur={() => setTimeout(() => setShowSuggestions(false), 150)}
            onFocus={() => suggestions.length > 0 && setShowSuggestions(true)}
            onKeyDown={(e) => { if (e.key === "Escape") setShowSuggestions(false); }}
            placeholder="owner/repo or paste a GitHub URL…"
            className="w-full pl-10 pr-4 py-2.5 rounded-lg bg-muted/50 border border-border text-sm placeholder:text-muted-foreground focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary"
          />
          {showSuggestions && suggestions.length > 0 && (
            <div className="absolute top-full left-0 right-0 mt-1 bg-card border border-border rounded-lg shadow-lg z-20 overflow-hidden">
              {suggestions.map((s) => (
                <button
                  key={s.full_name}
                  type="button"
                  onMouseDown={() => handleSelectSuggestion(s.full_name)}
                  className="w-full text-left px-4 py-2.5 hover:bg-muted/60 transition-colors flex items-center justify-between gap-3"
                >
                  <div className="min-w-0">
                    <div className="text-sm font-medium truncate">{s.full_name}</div>
                    {s.description && (
                      <div className="text-xs text-muted-foreground truncate">{s.description}</div>
                    )}
                  </div>
                  <div className="flex items-center gap-2 shrink-0 text-xs text-muted-foreground">
                    {s.language && <LanguageDot language={s.language} />}
                    <Star className="w-3 h-3" />
                    {s.stargazers_count.toLocaleString()}
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>
        <button
          type="submit"
          disabled={loading || !query.trim()}
          className="px-5 py-2.5 bg-primary text-primary-foreground hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed rounded-lg text-sm font-medium transition flex items-center gap-2"
        >
          {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Search className="w-4 h-4" />}
          {loading ? "Loading…" : "Search"}
        </button>
      </form>

      {/* Error */}
      {error && (
        <div className="flex items-center gap-3 bg-destructive/10 border border-destructive/30 text-destructive rounded-xl px-4 py-3 text-sm">
          <AlertCircle className="w-4 h-4 shrink-0" />
          {error}
        </div>
      )}

      {/* Loading skeleton */}
      {loading && (
        <div className="space-y-4 animate-pulse">
          <div className="h-36 bg-muted rounded-xl" />
          <div className="h-48 bg-muted rounded-xl" />
        </div>
      )}

      {/* Repo result */}
      {!loading && repoData && (
        <div className="space-y-4">
          <div className="bg-card border border-border rounded-xl p-6">
            <div className="flex items-start justify-between gap-4">
              <div className="min-w-0">
                <a
                  href={repoData.html_url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-lg font-semibold text-primary hover:opacity-80 transition break-all flex items-center gap-1.5"
                >
                  {repoData.full_name}
                  <ExternalLink className="w-4 h-4 shrink-0" />
                </a>
                {repoData.description && (
                  <p className="text-muted-foreground text-sm mt-1">{repoData.description}</p>
                )}
              </div>
              <div className="shrink-0">
                {forkedUrl ? (
                  <a
                    href={forkedUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-1.5 text-xs font-medium text-green-600 border border-green-500/40 bg-green-500/10 px-3 py-1.5 rounded-md hover:opacity-80 transition"
                  >
                    <ExternalLink className="w-3.5 h-3.5" />
                    Forked — Open
                  </a>
                ) : isLoggedIn ? (
                  <button
                    onClick={handleFork}
                    disabled={forkLoading}
                    className="flex items-center gap-1.5 text-xs font-medium border border-border px-3 py-1.5 rounded-md hover:bg-muted/60 disabled:opacity-50 transition"
                  >
                    <ForkIcon className="w-3.5 h-3.5" />
                    {forkLoading ? "Forking…" : "Fork"}
                  </button>
                ) : null}
              </div>
            </div>
            {forkError && <p className="text-xs text-destructive mt-2">{forkError}</p>}

            <div className="flex items-center flex-wrap gap-4 mt-5 text-sm text-foreground">
              {repoData.language && (
                <span className="flex items-center gap-1.5">
                  <LanguageDot language={repoData.language} />
                  {repoData.language}
                </span>
              )}
              <span className="flex items-center gap-1.5">
                <Star className="w-4 h-4 text-yellow-400" />
                {repoData.stargazers_count.toLocaleString()} stars
              </span>
              <span className="flex items-center gap-1.5">
                <GitFork className="w-4 h-4 text-muted-foreground" />
                {repoData.forks_count.toLocaleString()} forks
              </span>
              <span className="flex items-center gap-1.5">
                <AlertCircle className="w-4 h-4 text-orange-400" />
                {repoData.open_issues_count.toLocaleString()} open issues
              </span>
              {totalCommits > 0 && (
                <span className="flex items-center gap-1.5">
                  <GitCommitHorizontal className="w-4 h-4 text-green-500" />
                  {totalCommits.toLocaleString()} total commits
                </span>
              )}
            </div>
          </div>

          {contributors.length > 0 && (
            <div className="bg-card border border-border rounded-xl p-6">
              <div className="flex items-center gap-2 mb-4">
                <Users className="w-4 h-4 text-muted-foreground" />
                <h2 className="font-semibold text-sm">Top Contributors</h2>
              </div>
              <div className="space-y-3">
                {contributors.map((c, i) => {
                  if (!c.author) return null;
                  const pct = totalCommits > 0 ? Math.round((c.total / totalCommits) * 100) : 0;
                  const totalAdded = c.weeks.reduce((sum, w) => sum + w.a, 0);
                  const totalDeleted = c.weeks.reduce((sum, w) => sum + w.d, 0);
                  return (
                    <div key={c.author.login} className="flex items-center gap-3">
                      <span className="text-muted-foreground text-xs w-4 text-right">{i + 1}</span>
                      <img src={c.author.avatar_url} alt={c.author.login} className="w-7 h-7 rounded-full" />
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center justify-between mb-1">
                          <a
                            href={`https://github.com/${c.author.login}`}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="text-sm font-medium hover:text-primary transition truncate"
                          >
                            {c.author.login}
                          </a>
                          <span className="text-xs text-muted-foreground shrink-0 ml-2">
                            {c.total.toLocaleString()} commits ({pct}%)
                          </span>
                        </div>
                        <div className="h-1.5 bg-muted rounded-full overflow-hidden">
                          <div className="h-full bg-primary rounded-full" style={{ width: `${pct}%` }} />
                        </div>
                        <div className="flex gap-3 mt-1 text-xs text-muted-foreground">
                          <span className="text-green-500">+{totalAdded.toLocaleString()}</span>
                          <span className="text-red-500">-{totalDeleted.toLocaleString()}</span>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          <div className="bg-primary/10 border border-primary/30 rounded-xl p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <div className="font-semibold text-sm">Track this repo with full analytics</div>
              <div className="text-muted-foreground text-xs mt-0.5">
                PR lifecycle, heatmaps, team leaderboards, email digests and more.
              </div>
            </div>
            <Link
              href="/repos"
              className="shrink-0 bg-primary text-primary-foreground hover:opacity-90 px-4 py-2 rounded-md text-sm font-medium transition"
            >
              Track this repo
            </Link>
          </div>
        </div>
      )}

      {/* Trending section — shown when no search result */}
      {!repoData && !loading && (
        <div>
          <div className="flex items-center gap-2 mb-4">
            <TrendingUp className="w-4 h-4 text-muted-foreground" />
            <h2 className="font-semibold text-sm">Trending This Week</h2>
            <span className="text-xs text-muted-foreground">most starred repos active in the last 7 days</span>
          </div>
          {trendingLoading ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 animate-pulse">
              {Array.from({ length: 6 }).map((_, i) => (
                <div key={i} className="h-28 bg-muted rounded-xl" />
              ))}
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {trendingRepos.map((repo) => (
                <TrendingCard
                  key={repo.full_name}
                  repo={repo}
                  onSelect={(name) => {
                    setQuery(name);
                    triggerSearch(name);
                  }}
                />
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
