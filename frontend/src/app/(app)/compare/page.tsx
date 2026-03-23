"use client";

import { useState } from "react";
import { useOverview, usePRLifecycle, useCommitTrend, useCompareRepos, useCompareContributors } from "@/hooks/useAnalytics";
import { useRepos } from "@/hooks/useRepos";
import { subDays, formatISO, format, parseISO, isValid } from "date-fns";
import {
  AreaChart, Area, ResponsiveContainer, XAxis, YAxis, CartesianGrid, Tooltip as ReTooltip,
} from "recharts";
import { TrendingUp, TrendingDown, Minus, SlidersHorizontal } from "lucide-react";

// ── Presets ──────────────────────────────────────────────────────────────────

interface PeriodPreset {
  label: string;
  aFrom: () => Date;
  aTo: () => Date;
  bFrom: () => Date;
  bTo: () => Date;
}

const PRESETS: PeriodPreset[] = [
  {
    label: "This month vs last month",
    aFrom: () => new Date(new Date().getFullYear(), new Date().getMonth(), 1),
    aTo:   () => new Date(),
    bFrom: () => new Date(new Date().getFullYear(), new Date().getMonth() - 1, 1),
    bTo:   () => new Date(new Date().getFullYear(), new Date().getMonth(), 0),
  },
  {
    label: "Last 30d vs previous 30d",
    aFrom: () => subDays(new Date(), 30),
    aTo:   () => new Date(),
    bFrom: () => subDays(new Date(), 60),
    bTo:   () => subDays(new Date(), 30),
  },
  {
    label: "Last 7d vs previous 7d",
    aFrom: () => subDays(new Date(), 7),
    aTo:   () => new Date(),
    bFrom: () => subDays(new Date(), 14),
    bTo:   () => subDays(new Date(), 7),
  },
  {
    label: "This year vs last year",
    aFrom: () => new Date(new Date().getFullYear(), 0, 1),
    aTo:   () => new Date(),
    bFrom: () => new Date(new Date().getFullYear() - 1, 0, 1),
    bTo:   () => new Date(new Date().getFullYear() - 1, 11, 31),
  },
];

// ── Helpers ───────────────────────────────────────────────────────────────────

function delta(a: number, b: number) {
  if (b === 0) return a > 0 ? 100 : 0;
  return Math.round(((a - b) / b) * 100);
}

function formatHours(h: number) {
  if (h < 1) return "<1h";
  if (h < 24) return `${Math.round(h)}h`;
  return `${(h / 24).toFixed(1)}d`;
}

function Skeleton({ className = "" }: { className?: string }) {
  return <div className={`animate-pulse bg-muted rounded ${className}`} />;
}

// ── Delta badge ────────────────────────────────────────────────────────────

function DeltaBadge({ pct, inverse = false }: { pct: number; inverse?: boolean }) {
  const positive = inverse ? pct < 0 : pct > 0;
  const neutral = pct === 0;
  return (
    <span className={`inline-flex items-center gap-0.5 text-xs font-medium px-1.5 py-0.5 rounded-full ${
      neutral
        ? "bg-muted text-muted-foreground"
        : positive
          ? "bg-green-500/15 text-green-600"
          : "bg-red-500/15 text-red-500"
    }`}>
      {neutral ? <Minus className="w-3 h-3" /> : positive ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
      {neutral ? "—" : `${pct > 0 ? "+" : ""}${pct}%`}
    </span>
  );
}

// ── Comparison metric card ─────────────────────────────────────────────────

interface CompareCardProps {
  label: string;
  aValue: string | number;
  bValue: string | number;
  pct: number;
  aLabel: string;
  bLabel: string;
  isLoading: boolean;
  inverse?: boolean;
}

function CompareCard({ label, aValue, bValue, pct, aLabel, bLabel, isLoading, inverse }: CompareCardProps) {
  return (
    <div className="bg-card border border-border rounded-xl p-4 sm:p-5">
      <div className="text-xs text-muted-foreground font-medium uppercase tracking-wide mb-3">{label}</div>
      {isLoading ? (
        <div className="space-y-2">
          <Skeleton className="h-7 w-24" />
          <Skeleton className="h-4 w-32" />
        </div>
      ) : (
        <>
          <div className="flex items-end gap-3 mb-2">
            <div className="text-xl sm:text-2xl font-bold">{aValue}</div>
            <DeltaBadge pct={pct} inverse={inverse} />
          </div>
          <div className="flex items-center justify-between gap-1 flex-wrap text-xs text-muted-foreground">
            <span><span className="font-medium text-foreground/80">{aLabel}:</span> {aValue}</span>
            <span><span className="font-medium text-foreground/80">{bLabel}:</span> {bValue}</span>
          </div>
        </>
      )}
    </div>
  );
}

// ── Trend mini-chart ───────────────────────────────────────────────────────

function MiniTrendChart({
  data,
  color,
  gradientId,
  isLoading,
}: {
  data: { date: string; commits: number }[];
  color: string;
  gradientId: string;
  isLoading: boolean;
}) {
  if (isLoading) return <Skeleton className="h-28" />;
  if (!data.length) return <div className="h-28 flex items-center justify-center text-xs text-muted-foreground">No data</div>;
  return (
    <ResponsiveContainer width="100%" height={112}>
      <AreaChart data={data} margin={{ top: 4, right: 4, left: -30, bottom: 0 }}>
        <defs>
          <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%" stopColor={color} stopOpacity={0.25} />
            <stop offset="95%" stopColor={color} stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
        <XAxis dataKey="date" tick={{ fontSize: 9, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} interval="preserveStartEnd" />
        <YAxis tick={{ fontSize: 9, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} allowDecimals={false} />
        <ReTooltip
          contentStyle={{ background: "hsl(var(--card))", border: "1px solid hsl(var(--border))", borderRadius: "8px", fontSize: "11px" }}
          formatter={(v: number) => [v, "commits"]}
        />
        <Area type="monotone" dataKey="commits" stroke={color} strokeWidth={2} fill={`url(#${gradientId})`} dot={false} />
      </AreaChart>
    </ResponsiveContainer>
  );
}

// ── Date input ─────────────────────────────────────────────────────────────

function DateInput({ label, value, onChange }: {
  label: string; value: string; onChange: (v: string) => void;
}) {
  return (
    <div className="flex flex-col gap-1">
      <label className="text-xs text-muted-foreground">{label}</label>
      <input
        type="date"
        value={value}
        onClick={(e) => (e.currentTarget as HTMLInputElement).showPicker?.()}
        onChange={(e) => onChange(e.target.value)}
        className="border border-border bg-background text-foreground text-sm rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary/50 [color-scheme:auto] cursor-pointer"
      />
    </div>
  );
}

// ── Repos Compare Tab ──────────────────────────────────────────────────────

function ReposCompareTab() {
  const { data: repos } = useRepos();
  const [repoAId, setRepoAId] = useState("");
  const [repoBId, setRepoBId] = useState("");

  const selectedIds = [repoAId, repoBId].filter(Boolean);
  const { data: compared, isLoading } = useCompareRepos(selectedIds);

  const repoA = compared?.[0];
  const repoB = compared?.[1];

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="text-xs text-muted-foreground block mb-1">Repository A</label>
          <select
            value={repoAId}
            onChange={(e) => setRepoAId(e.target.value)}
            className="w-full border border-border bg-background text-foreground text-sm rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary/50"
          >
            <option value="">Select a repo…</option>
            {(repos ?? []).map((r) => (
              <option key={r.id} value={r.id}>{r.fullName}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="text-xs text-muted-foreground block mb-1">Repository B</label>
          <select
            value={repoBId}
            onChange={(e) => setRepoBId(e.target.value)}
            className="w-full border border-border bg-background text-foreground text-sm rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary/50"
          >
            <option value="">Select a repo…</option>
            {(repos ?? []).filter((r) => r.id !== repoAId).map((r) => (
              <option key={r.id} value={r.id}>{r.fullName}</option>
            ))}
          </select>
        </div>
      </div>

      {repoAId && repoBId && (
        isLoading ? (
          <div className="grid grid-cols-2 gap-4">
            {[...Array(6)].map((_, i) => <Skeleton key={i} className="h-24 rounded-xl" />)}
          </div>
        ) : repoA && repoB ? (
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              {[
                { label: "Health Score", a: `${repoA.healthScore}% (${repoA.healthLabel})`, b: `${repoB.healthScore}% (${repoB.healthLabel})`, rawA: repoA.healthScore, rawB: repoB.healthScore },
                { label: "Total Commits", a: repoA.totalCommits, b: repoB.totalCommits, rawA: repoA.totalCommits, rawB: repoB.totalCommits },
                { label: "Merged PRs", a: repoA.mergedPRs, b: repoB.mergedPRs, rawA: repoA.mergedPRs, rawB: repoB.mergedPRs },
                { label: "Stars", a: repoA.stars, b: repoB.stars, rawA: repoA.stars, rawB: repoB.stars },
                { label: "Forks", a: repoA.forks, b: repoB.forks, rawA: repoA.forks, rawB: repoB.forks },
                { label: "Open Issues", a: repoA.openIssues, b: repoB.openIssues, rawA: repoA.openIssues, rawB: repoB.openIssues, inverse: true },
              ].map((m) => (
                <CompareCard
                  key={m.label}
                  label={m.label}
                  aValue={m.a}
                  bValue={m.b}
                  pct={delta(Number(m.rawA), Number(m.rawB))}
                  aLabel={repoA.fullName.split("/")[1]}
                  bLabel={repoB.fullName.split("/")[1]}
                  isLoading={false}
                  inverse={m.inverse}
                />
              ))}
            </div>
            {(repoA.topContributor || repoB.topContributor) && (
              <div className="grid grid-cols-2 gap-4">
                <div className="bg-card border border-border rounded-xl p-5">
                  <div className="text-xs text-muted-foreground uppercase tracking-wide mb-2">Top Contributor</div>
                  <div className="font-semibold">{repoA.topContributor ?? "—"}</div>
                  <div className="text-xs text-muted-foreground">{repoA.topContributorPct}% of commits</div>
                </div>
                <div className="bg-card border border-border rounded-xl p-5">
                  <div className="text-xs text-muted-foreground uppercase tracking-wide mb-2">Top Contributor</div>
                  <div className="font-semibold">{repoB.topContributor ?? "—"}</div>
                  <div className="text-xs text-muted-foreground">{repoB.topContributorPct}% of commits</div>
                </div>
              </div>
            )}
          </div>
        ) : null
      )}

      {!repoAId || !repoBId ? (
        <div className="bg-muted/20 border border-border rounded-xl p-8 text-center text-muted-foreground text-sm">
          Select two repositories above to compare them side by side.
        </div>
      ) : null}
    </div>
  );
}

// ── Contributors Compare Tab ───────────────────────────────────────────────

function ContributorsCompareTab() {
  const [loginA, setLoginA] = useState("");
  const [loginB, setLoginB] = useState("");

  const logins = [loginA.trim(), loginB.trim()].filter(Boolean);
  const { data: compared, isLoading } = useCompareContributors(logins);

  const contribA = compared?.[0];
  const contribB = compared?.[1];

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="text-xs text-muted-foreground block mb-1">Contributor A (GitHub login)</label>
          <input
            type="text"
            value={loginA}
            onChange={(e) => setLoginA(e.target.value)}
            placeholder="e.g. torvalds"
            className="w-full border border-border bg-background text-foreground text-sm rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary/50"
          />
        </div>
        <div>
          <label className="text-xs text-muted-foreground block mb-1">Contributor B (GitHub login)</label>
          <input
            type="text"
            value={loginB}
            onChange={(e) => setLoginB(e.target.value)}
            placeholder="e.g. gvanrossum"
            className="w-full border border-border bg-background text-foreground text-sm rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary/50"
          />
        </div>
      </div>

      {loginA.trim() && loginB.trim() && (
        isLoading ? (
          <div className="grid grid-cols-2 gap-4">
            {[...Array(6)].map((_, i) => <Skeleton key={i} className="h-24 rounded-xl" />)}
          </div>
        ) : contribA && contribB ? (
          <div className="grid grid-cols-2 lg:grid-cols-3 gap-4">
            {[
              { label: "Total Commits", a: contribA.totalCommits, b: contribB.totalCommits },
              { label: "Lines Added", a: contribA.linesAdded, b: contribB.linesAdded },
              { label: "Lines Removed", a: contribA.linesRemoved, b: contribB.linesRemoved },
              { label: "PRs Authored", a: contribA.totalPRs, b: contribB.totalPRs },
              { label: "PRs Merged", a: contribA.mergedPRs, b: contribB.mergedPRs },
              { label: "Reviews Given", a: contribA.reviewsGiven, b: contribB.reviewsGiven },
            ].map((m) => (
              <CompareCard
                key={m.label}
                label={m.label}
                aValue={m.a.toLocaleString()}
                bValue={m.b.toLocaleString()}
                pct={delta(m.a, m.b)}
                aLabel={contribA.login}
                bLabel={contribB.login}
                isLoading={false}
              />
            ))}
          </div>
        ) : (
          <div className="bg-muted/20 border border-border rounded-xl p-8 text-center text-muted-foreground text-sm">
            No data found for these contributors in your tracked repositories.
          </div>
        )
      )}

      {(!loginA.trim() || !loginB.trim()) && (
        <div className="bg-muted/20 border border-border rounded-xl p-8 text-center text-muted-foreground text-sm">
          Enter two GitHub logins above to compare contributors side by side.
        </div>
      )}
    </div>
  );
}

// ── Main page ──────────────────────────────────────────────────────────────

function toDateStr(d: Date) {
  return format(d, "yyyy-MM-dd");
}

type CompareTab = "periods" | "repos" | "contributors";

export default function ComparePage() {
  const [compareTab, setCompareTab] = useState<CompareTab>("periods");

  const [mode, setMode] = useState<"preset" | "custom">("preset");
  const [preset, setPreset] = useState(0);
  const [openPicker, setOpenPicker] = useState<"A" | "B" | null>(null);

  const [customAFrom, setCustomAFrom] = useState(toDateStr(subDays(new Date(), 30)));
  const [customATo,   setCustomATo]   = useState(toDateStr(new Date()));
  const [customBFrom, setCustomBFrom] = useState(toDateStr(subDays(new Date(), 60)));
  const [customBTo,   setCustomBTo]   = useState(toDateStr(subDays(new Date(), 30)));

  const p = PRESETS[preset];

  const parseSafe = (s: string) => { const d = parseISO(s); return isValid(d) ? d : null; };

  const customDatesValid = !!(
    parseSafe(customAFrom) && parseSafe(customATo) &&
    parseSafe(customBFrom) && parseSafe(customBTo)
  );

  const aFrom = mode === "custom" && parseSafe(customAFrom) ? formatISO(parseSafe(customAFrom)!) : formatISO(p.aFrom());
  const aTo   = mode === "custom" && parseSafe(customATo)   ? formatISO(parseSafe(customATo)!)   : formatISO(p.aTo());
  const bFrom = mode === "custom" && parseSafe(customBFrom) ? formatISO(parseSafe(customBFrom)!) : formatISO(p.bFrom());
  const bTo   = mode === "custom" && parseSafe(customBTo)   ? formatISO(parseSafe(customBTo)!)   : formatISO(p.bTo());

  const enabled = compareTab === "periods" && (mode === "preset" || customDatesValid);

  const { data: overviewA, isLoading: loadA } = useOverview(aFrom, aTo, { enabled });
  const { data: overviewB, isLoading: loadB } = useOverview(bFrom, bTo, { enabled });
  const { data: lifecycleA, isLoading: lcLoadA } = usePRLifecycle(aFrom, aTo, { enabled });
  const { data: lifecycleB, isLoading: lcLoadB } = usePRLifecycle(bFrom, bTo, { enabled });
  const { data: trendA, isLoading: trendLoadA } = useCommitTrend(aFrom, aTo, "daily", { enabled });
  const { data: trendB, isLoading: trendLoadB } = useCommitTrend(bFrom, bTo, "daily", { enabled });

  const overviewLoading = loadA || loadB;
  const lifecycleLoading = lcLoadA || lcLoadB;

  const fmtRange = (from: string, to: string) => {
    const a = parseSafe(from), b = parseSafe(to);
    return a && b ? `${format(a, "MMM d")} – ${format(b, "MMM d")}` : "—";
  };

  const aLabel = mode === "custom" ? fmtRange(customAFrom, customATo) : format(p.aFrom(), "MMM d") + " – " + format(p.aTo(), "MMM d");
  const bLabel = mode === "custom" ? fmtRange(customBFrom, customBTo) : format(p.bFrom(), "MMM d") + " – " + format(p.bTo(), "MMM d");

  const toChartData = (trend: typeof trendA) =>
    (trend ?? []).map((pt) => ({
      date: format(new Date(pt.date), "MMM d"),
      commits: pt.count,
    }));

  const metrics = [
    {
      label: "Commits",
      aValue: overviewA?.commits ?? 0,
      bValue: overviewB?.commits ?? 0,
      isLoading: overviewLoading,
    },
    {
      label: "PRs Authored",
      aValue: overviewA?.prsAuthored ?? 0,
      bValue: overviewB?.prsAuthored ?? 0,
      isLoading: overviewLoading,
    },
    {
      label: "Reviews Given",
      aValue: overviewA?.reviewsGiven ?? 0,
      bValue: overviewB?.reviewsGiven ?? 0,
      isLoading: overviewLoading,
    },
    {
      label: "Lines Added",
      aValue: (overviewA?.linesAdded ?? 0).toLocaleString(),
      bValue: (overviewB?.linesAdded ?? 0).toLocaleString(),
      rawA: overviewA?.linesAdded ?? 0,
      rawB: overviewB?.linesAdded ?? 0,
      isLoading: overviewLoading,
    },
    {
      label: "Avg Merge Time",
      aValue: formatHours(lifecycleA?.avgHoursToMerge ?? 0),
      bValue: formatHours(lifecycleB?.avgHoursToMerge ?? 0),
      rawA: lifecycleA?.avgHoursToMerge ?? 0,
      rawB: lifecycleB?.avgHoursToMerge ?? 0,
      isLoading: lifecycleLoading,
      inverse: true,
    },
    {
      label: "PRs Merged",
      aValue: lifecycleA?.mergedCount ?? 0,
      bValue: lifecycleB?.mergedCount ?? 0,
      isLoading: lifecycleLoading,
    },
  ];

  return (
    <div className="space-y-8 max-w-4xl">
      <div>
        <h1 className="text-2xl font-bold">Compare</h1>
        <p className="text-muted-foreground text-sm mt-1">
          Compare periods, repositories, or contributors side by side.
        </p>
      </div>

      {/* Top-level tabs */}
      <div className="flex border-b border-border">
        {(["periods", "repos", "contributors"] as CompareTab[]).map((t) => (
          <button
            key={t}
            onClick={() => setCompareTab(t)}
            className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors capitalize ${
              compareTab === t ? "border-primary text-primary" : "border-transparent text-muted-foreground hover:text-foreground"
            }`}
          >
            {t === "periods" ? "Periods" : t === "repos" ? "Repositories" : "Contributors"}
          </button>
        ))}
      </div>

      {/* Repos tab */}
      {compareTab === "repos" && <ReposCompareTab />}

      {/* Contributors tab */}
      {compareTab === "contributors" && <ContributorsCompareTab />}

      {/* Periods tab (original content) */}
      {compareTab === "periods" && (
        <>
          {/* Preset selector + custom toggle */}
          <div className="flex gap-2 flex-wrap items-center">
            {PRESETS.map((pr, i) => (
              <button
                key={pr.label}
                onClick={() => { setPreset(i); setMode("preset"); setOpenPicker(null); }}
                className={`px-4 py-2 text-sm rounded-lg border transition-colors ${
                  mode === "preset" && preset === i
                    ? "bg-primary text-primary-foreground border-primary"
                    : "border-border text-muted-foreground hover:text-foreground hover:bg-muted/40"
                }`}
              >
                {pr.label}
              </button>
            ))}
            <button
              onClick={() => setMode("custom")}
              className={`inline-flex items-center gap-1.5 px-4 py-2 text-sm rounded-lg border transition-colors ${
                mode === "custom"
                  ? "bg-primary text-primary-foreground border-primary"
                  : "border-border text-muted-foreground hover:text-foreground hover:bg-muted/40"
              }`}
            >
              <SlidersHorizontal className="w-3.5 h-3.5" />
              Custom
            </button>
          </div>

          {/* Period labels — clickable in custom mode */}
          <div className="space-y-3">
            <div className="grid grid-cols-2 gap-4">
              {/* Period B box — shown first so users pick the baseline first */}
              <div
                onClick={() => {
                  if (mode !== "custom") return;
                  setOpenPicker(openPicker === "B" ? null : "B");
                }}
                className={`rounded-xl border-2 px-4 py-3 text-sm font-medium text-center transition-colors select-none ${
                  mode === "custom"
                    ? openPicker === "B"
                      ? "border-muted-foreground bg-muted/50 cursor-pointer"
                      : "border-muted bg-muted/30 cursor-pointer hover:bg-muted/50"
                    : "border-muted bg-muted/30"
                }`}
              >
                <span className="text-xs text-muted-foreground block mb-0.5">Period B (previous)</span>
                {bLabel}
                {mode === "custom" && (
                  <span className="text-[10px] text-muted-foreground block mt-1 font-normal">
                    {openPicker === "B" ? "▲ close" : "▼ edit"}
                  </span>
                )}
              </div>

              {/* Period A box */}
              <div
                onClick={() => {
                  if (mode !== "custom") return;
                  setOpenPicker(openPicker === "A" ? null : "A");
                }}
                className={`rounded-xl border-2 px-4 py-3 text-sm font-medium text-center transition-colors select-none ${
                  mode === "custom"
                    ? openPicker === "A"
                      ? "border-primary bg-primary/10 cursor-pointer"
                      : "border-primary/40 bg-primary/5 cursor-pointer hover:bg-primary/10"
                    : "border-primary/40 bg-primary/5"
                }`}
              >
                <span className="text-xs text-muted-foreground block mb-0.5">Period A (current)</span>
                {aLabel}
                {mode === "custom" && (
                  <span className="text-[10px] text-primary block mt-1 font-normal">
                    {openPicker === "A" ? "▲ close" : "▼ edit"}
                  </span>
                )}
              </div>
            </div>

            {/* Inline date picker panel */}
            {mode === "custom" && openPicker && (
              <div className="bg-muted/20 border border-border rounded-xl p-5 space-y-4">
                <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                  {openPicker === "A" ? "Period A — current period" : "Period B — comparison period"}
                </p>
                <div className="flex flex-wrap gap-4 items-end">
                  <DateInput
                    label="From"
                    value={openPicker === "A" ? customAFrom : customBFrom}
                    onChange={openPicker === "A" ? setCustomAFrom : setCustomBFrom}
                  />
                  <DateInput
                    label="To"
                    value={openPicker === "A" ? customATo : customBTo}
                    onChange={openPicker === "A" ? setCustomATo : setCustomBTo}
                  />
                  <button
                    onClick={() => setOpenPicker(null)}
                    className="px-5 py-2 text-sm font-medium bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors"
                  >
                    Done
                  </button>
                </div>
                {(() => {
                  const aF = parseSafe(customAFrom), bT = parseSafe(customBTo);
                  const overlap = aF && bT && aF < bT;
                  return overlap ? (
                    <p className="text-[11px] text-amber-500">Period A overlaps with Period B — results may be inaccurate.</p>
                  ) : null;
                })()}
              </div>
            )}
          </div>

          {/* Metrics grid */}
          <div className="grid grid-cols-2 lg:grid-cols-3 gap-4">
            {metrics.map((m) => (
              <CompareCard
                key={m.label}
                label={m.label}
                aValue={m.aValue}
                bValue={m.bValue}
                pct={delta(
                  typeof m.aValue === "number" ? m.aValue : ((m as any).rawA ?? 0),
                  typeof m.bValue === "number" ? m.bValue : ((m as any).rawB ?? 0),
                )}
                aLabel="This period"
                bLabel="Previous"
                isLoading={m.isLoading}
                inverse={(m as any).inverse}
              />
            ))}
          </div>

          {/* Commit trend side-by-side */}
          <div className="bg-card border border-border rounded-xl p-6">
            <h2 className="font-semibold mb-5">Commit Trend</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div>
                <div className="text-xs font-medium text-primary mb-2">
                  Period A · {aLabel}
                </div>
                <MiniTrendChart
                  data={toChartData(trendA)}
                  color="hsl(var(--primary))"
                  gradientId="compare-grad-a"
                  isLoading={trendLoadA}
                />
              </div>
              <div>
                <div className="text-xs font-medium text-muted-foreground mb-2">
                  Period B · {bLabel}
                </div>
                <MiniTrendChart
                  data={toChartData(trendB)}
                  color="hsl(var(--muted-foreground))"
                  gradientId="compare-grad-b"
                  isLoading={trendLoadB}
                />
              </div>
            </div>
          </div>

          {/* Key takeaway */}
          {!overviewLoading && overviewA && overviewB && (
            <div className="bg-muted/30 border border-border rounded-xl p-5">
              <h3 className="text-sm font-semibold mb-2">Summary</h3>
              <div className="text-sm text-muted-foreground space-y-1">
                {(() => {
                  const commitDelta = delta(overviewA.commits, overviewB.commits);
                  const prDelta = delta(overviewA.prsAuthored, overviewB.prsAuthored);
                  const reviewDelta = delta(overviewA.reviewsGiven, overviewB.reviewsGiven);
                  return (
                    <>
                      <p>
                        Commits:{" "}
                        <span className={commitDelta >= 0 ? "text-green-600 font-medium" : "text-red-500 font-medium"}>
                          {commitDelta >= 0 ? "▲" : "▼"} {Math.abs(commitDelta)}% {commitDelta >= 0 ? "more" : "fewer"}
                        </span>{" "}
                        than the previous period ({overviewA.commits} vs {overviewB.commits}).
                      </p>
                      <p>
                        PRs:{" "}
                        <span className={prDelta >= 0 ? "text-green-600 font-medium" : "text-red-500 font-medium"}>
                          {prDelta >= 0 ? "▲" : "▼"} {Math.abs(prDelta)}% {prDelta >= 0 ? "more" : "fewer"}
                        </span>{" "}
                        pull requests authored.
                      </p>
                      <p>
                        Reviews:{" "}
                        <span className={reviewDelta >= 0 ? "text-green-600 font-medium" : "text-red-500 font-medium"}>
                          {reviewDelta >= 0 ? "▲" : "▼"} {Math.abs(reviewDelta)}%
                        </span>{" "}
                        code reviews given.
                      </p>
                    </>
                  );
                })()}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
