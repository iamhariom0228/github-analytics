"use client";

import { useRepoHealth } from "@/hooks/useAnalytics";
import { cn } from "@/lib/utils";
import { ShieldCheck, ShieldAlert, ShieldX, ChevronDown, ChevronUp } from "lucide-react";

const healthConfig = {
  "Healthy": { icon: ShieldCheck, color: "text-green-500", bg: "bg-green-500/10" },
  "At Risk": { icon: ShieldAlert, color: "text-yellow-500", bg: "bg-yellow-500/10" },
  "Needs Attention": { icon: ShieldX, color: "text-red-500", bg: "bg-red-500/10" },
} as const;

interface Props {
  repoId: string;
  syncStatus: string;
  expanded: boolean;
  onToggle: () => void;
}

export function HealthBadge({ repoId, syncStatus, expanded, onToggle }: Props) {
  const { data: health, isLoading } = useRepoHealth(repoId);

  if (syncStatus !== "DONE" || isLoading || !health) return null;

  const cfg = healthConfig[health.label as keyof typeof healthConfig] ?? healthConfig["Needs Attention"];
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
