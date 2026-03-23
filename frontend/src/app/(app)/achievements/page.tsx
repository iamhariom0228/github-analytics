"use client";

import { useDashboard, useOverview, useStreak } from "@/hooks/useAnalytics";
import { subDays, formatISO } from "date-fns";

// Use a fixed 90-day window for achievement calculations
const from90 = formatISO(subDays(new Date(), 90));
const to = formatISO(new Date());
const from30 = formatISO(subDays(new Date(), 30));

interface Achievement {
  id: string;
  title: string;
  description: string;
  icon: string;
  category: "streak" | "commits" | "prs" | "reviews" | "code";
  unlocked: boolean;
  progress: number; // 0–100
  progressLabel: string;
  rarity: "common" | "rare" | "epic" | "legendary";
}

const RARITY_STYLES: Record<Achievement["rarity"], string> = {
  common: "border-border",
  rare: "border-blue-500/50 bg-blue-500/5",
  epic: "border-violet-500/50 bg-violet-500/5",
  legendary: "border-yellow-500/50 bg-yellow-500/5",
};

const RARITY_BADGE: Record<Achievement["rarity"], string> = {
  common: "bg-muted text-muted-foreground",
  rare: "bg-blue-500/20 text-blue-500",
  epic: "bg-violet-500/20 text-violet-500",
  legendary: "bg-yellow-500/20 text-yellow-600",
};

const CATEGORY_LABEL: Record<Achievement["category"], string> = {
  streak: "Streak",
  commits: "Commits",
  prs: "Pull Requests",
  reviews: "Reviews",
  code: "Code Impact",
};

const CATEGORY_COLORS: Record<Achievement["category"], string> = {
  streak: "text-orange-500",
  commits: "text-green-500",
  prs: "text-blue-500",
  reviews: "text-violet-500",
  code: "text-pink-500",
};

function AchievementCard({ achievement }: { achievement: Achievement }) {
  const { unlocked, rarity } = achievement;

  return (
    <div
      className={`flex flex-col border rounded-xl p-5 transition-all duration-300 ${
        unlocked ? RARITY_STYLES[rarity] : "border-border opacity-50 grayscale"
      }`}
    >
      <div className="flex items-start gap-4">
        <div className={`text-3xl ${unlocked ? "" : "opacity-40"}`}>{achievement.icon}</div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <h3 className="font-semibold text-sm">{achievement.title}</h3>
            <span className={`text-xs font-medium ${CATEGORY_COLORS[achievement.category]}`}>
              {CATEGORY_LABEL[achievement.category]}
            </span>
          </div>
          <p className="text-xs text-muted-foreground mt-0.5 mb-3">{achievement.description}</p>

          {/* Progress bar */}
          <div className="space-y-1">
            <div className="h-1.5 bg-muted rounded-full overflow-hidden">
              <div
                className={`h-full rounded-full transition-all duration-700 ${
                  unlocked ? "bg-primary" : "bg-muted-foreground/40"
                }`}
                style={{ width: `${achievement.progress}%` }}
              />
            </div>
            <div className="text-xs text-muted-foreground">{achievement.progressLabel}</div>
          </div>
        </div>
      </div>

      {/* Footer row: unlocked status on left, rarity badge on right */}
      <div className="mt-auto pt-3 border-t border-border/50 flex items-center justify-between">
        {unlocked ? (
          <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <span className="text-green-500">✓</span>
            Unlocked
          </span>
        ) : (
          <span className="text-xs text-muted-foreground">Locked</span>
        )}
        <span className={`text-xs font-medium px-2 py-0.5 rounded-full capitalize ${RARITY_BADGE[rarity]}`}>
          {rarity}
        </span>
      </div>
    </div>
  );
}

function Skeleton({ className = "" }: { className?: string }) {
  return <div className={`animate-pulse bg-muted rounded ${className}`} />;
}

export default function AchievementsPage() {
  const { data: dashboard, isLoading: dashLoading } = useDashboard();
  const { data: overview30, isLoading: overviewLoading } = useOverview(from30, to);
  const { data: overview90 } = useOverview(from90, to);
  const { data: streak } = useStreak();

  const isLoading = dashLoading || overviewLoading;

  const currentStreak = streak?.currentStreak ?? dashboard?.currentStreak ?? 0;
  const longestStreak = streak?.longestStreak ?? currentStreak;
  const commits30 = overview30?.commits ?? 0;
  const commits90 = overview90?.commits ?? 0;
  const prs30 = overview30?.prsAuthored ?? 0;
  const reviews30 = overview30?.reviewsGiven ?? 0;
  const linesAdded = overview90?.linesAdded ?? 0;

  const achievements: Achievement[] = [
    // --- Streak ---
    {
      id: "streak-7",
      title: "On Fire",
      description: "Maintain a 7-day contribution streak.",
      icon: "🔥",
      category: "streak",
      unlocked: currentStreak >= 7,
      progress: Math.min((currentStreak / 7) * 100, 100),
      progressLabel: `${currentStreak} / 7 days`,
      rarity: "common",
    },
    {
      id: "streak-30",
      title: "Consistent Coder",
      description: "Maintain a 30-day contribution streak.",
      icon: "⚡",
      category: "streak",
      unlocked: currentStreak >= 30,
      progress: Math.min((currentStreak / 30) * 100, 100),
      progressLabel: `${currentStreak} / 30 days`,
      rarity: "rare",
    },
    {
      id: "streak-100",
      title: "Unstoppable",
      description: "Maintain a 100-day contribution streak.",
      icon: "💎",
      category: "streak",
      unlocked: longestStreak >= 100,
      progress: Math.min((longestStreak / 100) * 100, 100),
      progressLabel: `${longestStreak} / 100 days (all-time best)`,
      rarity: "legendary",
    },

    // --- Commits ---
    {
      id: "commits-first",
      title: "First Commit",
      description: "Make your first commit in any tracked repo.",
      icon: "🌱",
      category: "commits",
      unlocked: commits90 >= 1,
      progress: Math.min(commits90 * 100, 100),
      progressLabel: `${commits90} commit${commits90 !== 1 ? "s" : ""}`,
      rarity: "common",
    },
    {
      id: "commits-50",
      title: "Commit Regular",
      description: "Make 50 commits in 30 days.",
      icon: "📦",
      category: "commits",
      unlocked: commits30 >= 50,
      progress: Math.min((commits30 / 50) * 100, 100),
      progressLabel: `${commits30} / 50 commits (last 30 days)`,
      rarity: "rare",
    },
    {
      id: "commits-200",
      title: "Prolific Coder",
      description: "Make 200 commits in 90 days.",
      icon: "🚀",
      category: "commits",
      unlocked: commits90 >= 200,
      progress: Math.min((commits90 / 200) * 100, 100),
      progressLabel: `${commits90} / 200 commits (last 90 days)`,
      rarity: "epic",
    },

    // --- PRs ---
    {
      id: "pr-first",
      title: "First Ship",
      description: "Open your first pull request.",
      icon: "🎯",
      category: "prs",
      unlocked: prs30 >= 1,
      progress: Math.min(prs30 * 100, 100),
      progressLabel: `${prs30} PR${prs30 !== 1 ? "s" : ""} (last 30 days)`,
      rarity: "common",
    },
    {
      id: "pr-10",
      title: "PR Machine",
      description: "Open 10 pull requests in 30 days.",
      icon: "🏗️",
      category: "prs",
      unlocked: prs30 >= 10,
      progress: Math.min((prs30 / 10) * 100, 100),
      progressLabel: `${prs30} / 10 PRs (last 30 days)`,
      rarity: "rare",
    },
    {
      id: "pr-25",
      title: "Ship It",
      description: "Open 25 pull requests in 30 days.",
      icon: "🛸",
      category: "prs",
      unlocked: prs30 >= 25,
      progress: Math.min((prs30 / 25) * 100, 100),
      progressLabel: `${prs30} / 25 PRs (last 30 days)`,
      rarity: "legendary",
    },

    // --- Reviews ---
    {
      id: "review-first",
      title: "Code Reviewer",
      description: "Give your first code review.",
      icon: "👁️",
      category: "reviews",
      unlocked: reviews30 >= 1,
      progress: Math.min(reviews30 * 100, 100),
      progressLabel: `${reviews30} review${reviews30 !== 1 ? "s" : ""} (last 30 days)`,
      rarity: "common",
    },
    {
      id: "review-20",
      title: "Thorough Reviewer",
      description: "Give 20 code reviews in 30 days.",
      icon: "🔍",
      category: "reviews",
      unlocked: reviews30 >= 20,
      progress: Math.min((reviews30 / 20) * 100, 100),
      progressLabel: `${reviews30} / 20 reviews (last 30 days)`,
      rarity: "epic",
    },
    {
      id: "review-50",
      title: "Review Champion",
      description: "Give 50 code reviews in 30 days.",
      icon: "🏆",
      category: "reviews",
      unlocked: reviews30 >= 50,
      progress: Math.min((reviews30 / 50) * 100, 100),
      progressLabel: `${reviews30} / 50 reviews (last 30 days)`,
      rarity: "legendary",
    },

    // --- Code Impact ---
    {
      id: "lines-1k",
      title: "1K Club",
      description: "Add 1,000 lines of code in 90 days.",
      icon: "📝",
      category: "code",
      unlocked: linesAdded >= 1000,
      progress: Math.min((linesAdded / 1000) * 100, 100),
      progressLabel: `${linesAdded.toLocaleString()} / 1,000 lines (last 90 days)`,
      rarity: "common",
    },
    {
      id: "lines-10k",
      title: "10K Engineer",
      description: "Add 10,000 lines of code in 90 days.",
      icon: "🌊",
      category: "code",
      unlocked: linesAdded >= 10000,
      progress: Math.min((linesAdded / 10000) * 100, 100),
      progressLabel: `${linesAdded.toLocaleString()} / 10,000 lines (last 90 days)`,
      rarity: "legendary",
    },
  ];

  const unlocked = achievements.filter((a) => a.unlocked);
  const locked = achievements.filter((a) => !a.unlocked);

  return (
    <div className="space-y-8 max-w-4xl">
      <div>
        <h1 className="text-2xl font-bold">Achievements</h1>
        <p className="text-muted-foreground text-sm mt-1">
          Milestones earned from your coding activity across all tracked repositories.
        </p>
      </div>

      {/* Summary bar */}
      {isLoading ? (
        <Skeleton className="h-20 rounded-xl" />
      ) : (
        <div className="bg-card border border-border rounded-xl p-5 flex items-center gap-4 sm:gap-8 flex-wrap">
          <div className="text-center">
            <div className="text-3xl font-bold text-primary">{unlocked.length}</div>
            <div className="text-xs text-muted-foreground mt-0.5">Unlocked</div>
          </div>
          <div className="text-center">
            <div className="text-3xl font-bold">{achievements.length}</div>
            <div className="text-xs text-muted-foreground mt-0.5">Total</div>
          </div>
          <div className="flex-1 min-w-0">
            <div className="flex justify-between text-xs text-muted-foreground mb-1.5">
              <span>Overall progress</span>
              <span>{Math.round((unlocked.length / achievements.length) * 100)}%</span>
            </div>
            <div className="h-2.5 bg-muted rounded-full overflow-hidden">
              <div
                className="h-full bg-primary rounded-full transition-all duration-700"
                style={{ width: `${(unlocked.length / achievements.length) * 100}%` }}
              />
            </div>
          </div>
          {/* Rarity breakdown */}
          <div className="flex items-center gap-3 text-xs flex-wrap">
            {(["legendary", "epic", "rare", "common"] as const).map((r) => {
              const count = unlocked.filter((a) => a.rarity === r).length;
              const total = achievements.filter((a) => a.rarity === r).length;
              return (
                <div key={r} className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full ${RARITY_BADGE[r]}`}>
                  <span className="capitalize font-medium">{r}</span>
                  <span className="opacity-60">·</span>
                  <span className="font-semibold">{count}/{total} unlocked</span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Achievements by category */}
      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-36 rounded-xl" />
          ))}
        </div>
      ) : (
        <div className="space-y-8">
          {/* Unlocked first */}
          {unlocked.length > 0 && (
            <div>
              <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide mb-4">
                ✓ Unlocked ({unlocked.length})
              </h2>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {unlocked
                  .sort((a, b) => {
                    const order = { legendary: 0, epic: 1, rare: 2, common: 3 };
                    return order[a.rarity] - order[b.rarity];
                  })
                  .map((a) => (
                    <AchievementCard key={a.id} achievement={a} />
                  ))}
              </div>
            </div>
          )}

          {/* Locked — grouped by rarity so users know exactly what each tier requires */}
          {locked.length > 0 && (
            <div>
              <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide mb-4">
                Locked ({locked.length})
              </h2>
              {(["legendary", "epic", "rare", "common"] as const).map((r) => {
                const items = locked.filter((a) => a.rarity === r);
                if (items.length === 0) return null;
                const rarityDesc: Record<string, string> = {
                  legendary: "The hardest badges — rare feats very few developers reach.",
                  epic:       "Serious milestones that require sustained effort.",
                  rare:       "Meaningful achievements for consistent contributors.",
                  common:     "Good starting points — keep going!",
                };
                return (
                  <div key={r} className="mb-8">
                    <div className="flex items-center gap-3 mb-1">
                      <span className={`text-xs font-semibold px-2.5 py-1 rounded-full capitalize ${RARITY_BADGE[r]}`}>
                        {r}
                      </span>
                      <span className="text-xs text-muted-foreground">{rarityDesc[r]}</span>
                      <span className="text-xs text-muted-foreground ml-auto">
                        {items.length} remaining
                      </span>
                    </div>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-3">
                      {items.map((a) => (
                        <AchievementCard key={a.id} achievement={a} />
                      ))}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
