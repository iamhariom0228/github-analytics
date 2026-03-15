"use client";

import type { Insight } from "@/types";

const config = {
  positive: {
    gradient: "from-green-500/10 to-emerald-500/5",
    border: "border-green-500/30",
    metricColor: "text-green-500",
    iconBg: "bg-green-500/10",
    icon: "↑",
    iconColor: "text-green-600",
  },
  warning: {
    gradient: "from-yellow-500/10 to-orange-500/5",
    border: "border-yellow-500/30",
    metricColor: "text-yellow-500",
    iconBg: "bg-yellow-500/10",
    icon: "!",
    iconColor: "text-yellow-600",
  },
  info: {
    gradient: "from-blue-500/10 to-indigo-500/5",
    border: "border-blue-500/30",
    metricColor: "text-blue-500",
    iconBg: "bg-blue-500/10",
    icon: "i",
    iconColor: "text-blue-600",
  },
};

interface InsightCardProps {
  insight: Insight;
}

function InsightCard({ insight }: InsightCardProps) {
  const c = config[insight.type];

  if (insight.metric) {
    // Visual card with prominent metric
    return (
      <div className={`rounded-xl border bg-gradient-to-br ${c.gradient} ${c.border} p-4 flex gap-4 items-start`}>
        <div className={`flex-shrink-0 w-10 h-10 rounded-lg ${c.iconBg} flex items-center justify-center`}>
          <span className={`text-sm font-bold ${c.iconColor}`}>{c.icon}</span>
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-baseline gap-2 flex-wrap">
            <span className={`text-2xl font-bold tracking-tight ${c.metricColor}`}>
              {insight.metric}
            </span>
            {insight.metricLabel && (
              <span className="text-xs text-muted-foreground font-medium uppercase tracking-wide">
                {insight.metricLabel}
              </span>
            )}
          </div>
          <p className="text-sm text-muted-foreground mt-0.5 leading-snug">{insight.message}</p>
        </div>
      </div>
    );
  }

  // AI insight — special treatment
  if (insight.metricLabel === "AI Insight") {
    return (
      <div className="rounded-xl border border-violet-500/30 bg-gradient-to-br from-violet-500/10 to-purple-500/5 p-4 flex gap-3 items-start sm:col-span-2">
        <div className="flex-shrink-0 w-7 h-7 rounded-lg bg-violet-500/15 flex items-center justify-center mt-0.5">
          <span className="text-sm">✦</span>
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <span className="text-xs font-semibold text-violet-500 uppercase tracking-wide">AI Coaching Insight</span>
          </div>
          <p className="text-sm leading-relaxed text-foreground">{insight.message}</p>
        </div>
      </div>
    );
  }

  // Text-only card (fallback)
  return (
    <div className={`rounded-xl border bg-gradient-to-br ${c.gradient} ${c.border} p-4 flex gap-3 items-start`}>
      <div className={`flex-shrink-0 w-6 h-6 rounded-full ${c.iconBg} flex items-center justify-center mt-0.5`}>
        <span className={`text-xs font-bold ${c.iconColor}`}>{c.icon}</span>
      </div>
      <p className="text-sm leading-relaxed text-foreground">{insight.message}</p>
    </div>
  );
}

interface Props {
  insights: Insight[];
}

export function InsightsPanel({ insights }: Props) {
  return (
    <div className="bg-card border border-border rounded-xl p-6">
      <h2 className="font-semibold mb-4">Insights</h2>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        {insights.map((insight, i) => (
          <InsightCard key={i} insight={insight} />
        ))}
      </div>
    </div>
  );
}
