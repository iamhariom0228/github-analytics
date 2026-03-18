"use client";

import { useState, useEffect } from "react";
import { useOverview } from "@/hooks/useAnalytics";
import { startOfMonth, endOfMonth, formatISO, format, getDaysInMonth } from "date-fns";
import { Pencil, Check, X, Target, TrendingUp, Trophy } from "lucide-react";

const now = new Date();
const from = formatISO(startOfMonth(now));
const to = formatISO(endOfMonth(now));

const GOALS_KEY = "github-analytics:goals";

interface GoalConfig {
  commits: number;
  prs: number;
  reviews: number;
  linesAdded: number;
}

const DEFAULTS: GoalConfig = {
  commits: 50,
  prs: 10,
  reviews: 15,
  linesAdded: 2000,
};

function loadGoals(): GoalConfig {
  if (typeof window === "undefined") return DEFAULTS;
  try {
    const stored = localStorage.getItem(GOALS_KEY);
    if (!stored) return DEFAULTS;
    return { ...DEFAULTS, ...JSON.parse(stored) };
  } catch {
    return DEFAULTS;
  }
}

function saveGoals(goals: GoalConfig) {
  localStorage.setItem(GOALS_KEY, JSON.stringify(goals));
}

interface GoalRowProps {
  label: string;
  icon: string;
  current: number;
  target: number;
  unit: string;
  color: string;
  onEdit: (value: number) => void;
}

function GoalRow({ label, icon, current, target, unit, color, onEdit }: GoalRowProps) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(String(target));

  const pct = target > 0 ? Math.min(Math.round((current / target) * 100), 100) : 0;
  const done = current >= target;

  const commit = () => {
    const val = parseInt(draft, 10);
    if (!isNaN(val) && val > 0) onEdit(val);
    setEditing(false);
  };

  const cancel = () => {
    setDraft(String(target));
    setEditing(false);
  };

  return (
    <div className="bg-card border border-border rounded-xl p-5">
      <div className="flex items-start justify-between gap-3 mb-4">
        <div className="flex items-center gap-3">
          <span className="text-2xl">{icon}</span>
          <div className="min-w-0">
            <div className="font-semibold text-sm">{label}</div>
            <div className="text-xs text-muted-foreground mt-0.5">{format(new Date(), "MMMM")} target</div>
          </div>
        </div>

        {editing ? (
          <div className="flex items-center gap-2 shrink-0">
            <input
              type="number"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") commit(); if (e.key === "Escape") cancel(); }}
              className="w-24 px-2 py-1 text-sm border border-border rounded-md bg-muted/50 text-right focus:outline-none focus:border-primary"
              autoFocus
              min={1}
            />
            <button onClick={commit} className="text-green-500 hover:text-green-600 transition">
              <Check className="w-4 h-4" />
            </button>
            <button onClick={cancel} className="text-muted-foreground hover:text-foreground transition">
              <X className="w-4 h-4" />
            </button>
          </div>
        ) : (
          <button
            onClick={() => { setDraft(String(target)); setEditing(true); }}
            className="flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition shrink-0"
          >
            <Pencil className="w-3.5 h-3.5" />
            Edit
          </button>
        )}
      </div>

      {/* Progress bar */}
      <div className="mb-3">
        <div className="h-3 bg-muted rounded-full overflow-hidden">
          <div
            className={`h-full rounded-full transition-all duration-700 ${color}`}
            style={{ width: `${pct}%` }}
          />
        </div>
      </div>

      <div className="flex items-center justify-between text-sm">
        <span className="font-semibold tabular-nums">
          {current.toLocaleString()}
          <span className="text-muted-foreground font-normal"> {unit}</span>
        </span>
        {done ? (
          <span className="inline-flex items-center gap-1 bg-green-500/15 text-green-600 text-xs font-medium px-2 py-0.5 rounded-full">
            <Trophy className="w-3 h-3" /> Goal met!
          </span>
        ) : (
          <span className="text-muted-foreground text-xs">
            {pct}% of {target.toLocaleString()} {unit}
          </span>
        )}
      </div>

      {/* Projection */}
      {current > 0 && !done && (
        <div className="mt-3 pt-3 border-t border-border/60 flex items-center gap-1.5 text-xs text-muted-foreground">
          <TrendingUp className="w-3.5 h-3.5" />
          {(() => {
            const today = new Date();
            const daysPassed = Math.max(1, today.getDate());
            const totalDays = getDaysInMonth(today);
            const daysLeft = totalDays - daysPassed;
            const pace = current / daysPassed;
            const projected = Math.round(pace * totalDays);
            const onTrack = projected >= target;
            return (
              <span className={onTrack ? "text-green-600" : "text-orange-500"}>
                {daysLeft > 0
                  ? `At this pace, ~${projected.toLocaleString()} ${unit} total by end of month — ${daysLeft} day${daysLeft !== 1 ? "s" : ""} left${onTrack ? " ✓" : ""}`
                  : onTrack
                    ? `Goal met for ${format(today, "MMMM")} ✓`
                    : `Month ended — ${current.toLocaleString()} of ${target.toLocaleString()} ${unit}`}
              </span>
            );
          })()}
        </div>
      )}
    </div>
  );
}

function Skeleton({ className = "" }: { className?: string }) {
  return <div className={`animate-pulse bg-muted rounded ${className}`} />;
}

export default function GoalsPage() {
  const { data: overview, isLoading } = useOverview(from, to);
  const [goals, setGoals] = useState<GoalConfig>(DEFAULTS);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setGoals(loadGoals());
    setMounted(true);
  }, []);

  const updateGoal = <K extends keyof GoalConfig>(key: K, value: number) => {
    const next = { ...goals, [key]: value };
    setGoals(next);
    saveGoals(next);
  };

  const resetGoals = () => {
    setGoals(DEFAULTS);
    saveGoals(DEFAULTS);
  };

  const current = {
    commits: overview?.commits ?? 0,
    prs: overview?.prsAuthored ?? 0,
    reviews: overview?.reviewsGiven ?? 0,
    linesAdded: overview?.linesAdded ?? 0,
  };

  const totalGoals = 4;
  const metGoals = [
    current.commits >= goals.commits,
    current.prs >= goals.prs,
    current.reviews >= goals.reviews,
    current.linesAdded >= goals.linesAdded,
  ].filter(Boolean).length;

  const monthLabel = format(new Date(), "MMMM yyyy");

  return (
    <div className="space-y-8 max-w-3xl">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">Goals</h1>
          <p className="text-muted-foreground text-sm mt-1">
            Track your monthly coding targets. Progress resets automatically each month.
          </p>
        </div>
        <button
          onClick={resetGoals}
          className="text-xs text-muted-foreground hover:text-foreground border border-border px-3 py-1.5 rounded-md transition shrink-0"
        >
          Reset to defaults
        </button>
      </div>

      {/* Summary */}
      {isLoading || !mounted ? (
        <Skeleton className="h-24 rounded-xl" />
      ) : (
        <div className="bg-card border border-border rounded-xl p-5 flex items-center gap-6 flex-wrap">
          <div className="flex items-center gap-3">
            <Target className="w-6 h-6 text-primary" />
            <div>
              <div className="text-2xl font-bold">
                {metGoals} / {totalGoals}
              </div>
              <div className="text-xs text-muted-foreground">Goals met this month</div>
            </div>
          </div>
          <div className="flex-1 min-w-40">
            <div className="flex justify-between text-xs text-muted-foreground mb-1.5">
              <span>{monthLabel}</span>
              <span>{Math.round((metGoals / totalGoals) * 100)}% complete</span>
            </div>
            <div className="h-2.5 bg-muted rounded-full overflow-hidden">
              <div
                className="h-full bg-primary rounded-full transition-all duration-700"
                style={{ width: `${(metGoals / totalGoals) * 100}%` }}
              />
            </div>
          </div>
          {metGoals === totalGoals && (
            <div className="flex items-center gap-2 bg-green-500/10 border border-green-500/30 text-green-600 px-4 py-2 rounded-lg text-sm font-medium">
              🏆 All goals met!
            </div>
          )}
        </div>
      )}

      {/* Goal cards */}
      {isLoading || !mounted ? (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-44 rounded-xl" />
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <GoalRow
            label="Commits"
            icon="📦"
            current={current.commits}
            target={goals.commits}
            unit="commits"
            color="bg-green-500"
            onEdit={(v) => updateGoal("commits", v)}
          />
          <GoalRow
            label="Pull Requests"
            icon="🎯"
            current={current.prs}
            target={goals.prs}
            unit="PRs"
            color="bg-blue-500"
            onEdit={(v) => updateGoal("prs", v)}
          />
          <GoalRow
            label="Code Reviews"
            icon="👁️"
            current={current.reviews}
            target={goals.reviews}
            unit="reviews"
            color="bg-violet-500"
            onEdit={(v) => updateGoal("reviews", v)}
          />
          <GoalRow
            label="Lines Added"
            icon="📝"
            current={current.linesAdded}
            target={goals.linesAdded}
            unit="lines"
            color="bg-pink-500"
            onEdit={(v) => updateGoal("linesAdded", v)}
          />
        </div>
      )}

      {/* Tips */}
      <div className="bg-muted/30 border border-border rounded-xl p-5">
        <h3 className="text-sm font-semibold mb-3">Tips to hit your goals</h3>
        <ul className="space-y-2 text-sm text-muted-foreground">
          <li className="flex gap-2">
            <span className="shrink-0">💡</span>
            Break large features into smaller PRs — they're easier to review and count toward your PR goal.
          </li>
          <li className="flex gap-2">
            <span className="shrink-0">💡</span>
            Schedule a daily commit time — even documentation or test updates count.
          </li>
          <li className="flex gap-2">
            <span className="shrink-0">💡</span>
            Review open PRs in the Team tab to contribute to your review goal.
          </li>
        </ul>
      </div>
    </div>
  );
}
