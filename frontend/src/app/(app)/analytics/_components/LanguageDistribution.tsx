"use client";

import { useMemo } from "react";
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer, Legend } from "recharts";
import type { Repo, RepoLanguageStat } from "@/types";

interface Props {
  repos: Repo[];
  byteStats?: RepoLanguageStat[];
}

const PALETTE = [
  "hsl(var(--primary))",
  "#3b82f6", "#10b981", "#f59e0b", "#ef4444",
  "#8b5cf6", "#ec4899", "#06b6d4", "#84cc16", "#f97316",
  "#6366f1", "#14b8a6",
];

const LANGUAGE_COLORS: Record<string, string> = {
  TypeScript:  "#3178c6",
  JavaScript:  "#f7df1e",
  Python:      "#3572A5",
  Java:        "#b07219",
  Go:          "#00ADD8",
  Rust:        "#dea584",
  "C++":       "#f34b7d",
  C:           "#555555",
  Ruby:        "#701516",
  PHP:         "#4F5D95",
  Swift:       "#F05138",
  Kotlin:      "#A97BFF",
  Dart:        "#00B4AB",
  Shell:       "#89e051",
  CSS:         "#563d7c",
  HTML:        "#e34c26",
};

function getColor(lang: string, idx: number) {
  return LANGUAGE_COLORS[lang] ?? PALETTE[idx % PALETTE.length];
}

interface LangStat {
  language: string;
  count: number;
  pct: number;
  color: string;
}

export function LanguageDistribution({ repos, byteStats }: Props) {
  const stats = useMemo<LangStat[]>(() => {
    // Prefer byte-based stats when available
    if (byteStats && byteStats.length > 0) {
      const totalBytes = byteStats.reduce((sum, s) => sum + s.bytes, 0) || 1;
      return byteStats.map((s, idx) => ({
        language: s.language,
        count: s.bytes,
        pct: Math.round((s.bytes / totalBytes) * 100),
        color: getColor(s.language, idx),
      }));
    }
    const map: Record<string, number> = {};
    for (const repo of repos) {
      const lang = repo.language ?? "Unknown";
      map[lang] = (map[lang] ?? 0) + 1;
    }
    const total = repos.length || 1;
    return Object.entries(map)
      .sort((a, b) => b[1] - a[1])
      .map(([language, count], idx) => ({
        language,
        count,
        pct: Math.round((count / total) * 100),
        color: getColor(language, idx),
      }));
  }, [repos, byteStats]);

  if (repos.length === 0) {
    return (
      <div className="h-52 flex items-center justify-center text-muted-foreground text-sm">
        No repositories tracked yet.
      </div>
    );
  }

  const CustomTooltip = ({ active, payload }: { active?: boolean; payload?: { payload: LangStat }[] }) => {
    if (!active || !payload?.length) return null;
    const { language, count, pct } = payload[0].payload;
    const displayCount = byteStats && byteStats.length > 0
      ? count > 1024 * 1024
        ? `${(count / (1024 * 1024)).toFixed(1)} MB`
        : `${(count / 1024).toFixed(1)} KB`
      : `${count} repo${count !== 1 ? "s" : ""}`;
    return (
      <div className="bg-card border border-border text-foreground text-xs px-3 py-2 rounded-lg shadow-lg">
        <div className="font-semibold">{language}</div>
        <div className="text-muted-foreground mt-0.5">{displayCount} · {pct}%</div>
      </div>
    );
  };

  return (
    <div className="space-y-6">
      {/* Donut chart */}
      <div className="flex flex-col md:flex-row items-center gap-6">
        <div className="w-full md:w-64 shrink-0" style={{ height: 220 }}>
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={stats}
                dataKey="count"
                nameKey="language"
                cx="50%"
                cy="50%"
                innerRadius={60}
                outerRadius={95}
                paddingAngle={stats.length > 1 ? 2 : 0}
                strokeWidth={0}
              >
                {stats.map((entry) => (
                  <Cell key={entry.language} fill={entry.color} />
                ))}
              </Pie>
              <Tooltip content={<CustomTooltip />} />
            </PieChart>
          </ResponsiveContainer>
        </div>

        {/* Legend / breakdown table */}
        <div className="flex-1 w-full space-y-2">
          {stats.map((s) => (
            <div key={s.language} className="flex items-center gap-3">
              <div className="w-2.5 h-2.5 rounded-full shrink-0" style={{ backgroundColor: s.color }} />
              <span className="text-sm font-medium w-28 truncate">{s.language}</span>
              <div className="flex-1 h-2 bg-muted rounded-full overflow-hidden">
                <div
                  className="h-full rounded-full transition-all duration-500"
                  style={{ width: `${s.pct}%`, backgroundColor: s.color }}
                />
              </div>
              <span className="text-xs text-muted-foreground w-16 text-right shrink-0">
                {byteStats && byteStats.length > 0
                  ? `${s.pct}%`
                  : `${s.count} repo${s.count !== 1 ? "s" : ""} · ${s.pct}%`}
              </span>
            </div>
          ))}
        </div>
      </div>

      {/* Summary chips */}
      <div className="flex flex-wrap gap-2 pt-2 border-t border-border">
        {stats.slice(0, 6).map((s) => (
          <span
            key={s.language}
            className="flex items-center gap-1.5 text-xs px-2.5 py-1 rounded-full border border-border"
          >
            <span className="w-2 h-2 rounded-full shrink-0" style={{ backgroundColor: s.color }} />
            {s.language}
            <span className="text-muted-foreground">{s.pct}%</span>
          </span>
        ))}
        {stats.length > 6 && (
          <span className="text-xs px-2.5 py-1 rounded-full border border-border text-muted-foreground">
            +{stats.length - 6} more
          </span>
        )}
      </div>
    </div>
  );
}
