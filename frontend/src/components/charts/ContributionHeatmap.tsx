"use client";

import { useState } from "react";
import type { HeatmapCell } from "@/types";
import { DAYS } from "@/lib/utils";

interface Props {
  data: HeatmapCell[];
}

const HOURS = Array.from({ length: 24 }, (_, i) => i);
const HOUR_LABELS = [0, 4, 8, 12, 16, 20];

function getColor(count: number, maxCount: number): string {
  if (count === 0) return "hsl(var(--muted))";
  const intensity = count / maxCount;
  if (intensity < 0.2)  return "hsl(var(--primary) / 0.2)";
  if (intensity < 0.4)  return "hsl(var(--primary) / 0.4)";
  if (intensity < 0.65) return "hsl(var(--primary) / 0.65)";
  if (intensity < 0.85) return "hsl(var(--primary) / 0.85)";
  return "hsl(var(--primary))";
}

interface TooltipState {
  content: string;
  x: number;
  y: number;
}

export function ContributionHeatmap({ data }: Props) {
  const [tooltip, setTooltip] = useState<TooltipState | null>(null);

  const maxCount = Math.max(...data.map((d) => d.count), 1);
  const grid: Record<string, number> = {};
  data.forEach((d) => { grid[`${d.day}-${d.hour}`] = d.count; });

  const CELL = 18;   // px
  const GAP  = 4;    // px
  const DAY_LABEL_W = 36; // px

  return (
    <div className="relative select-none">
      {/* Hour axis labels */}
      <div
        className="flex mb-2"
        style={{ paddingLeft: DAY_LABEL_W }}
      >
        {HOURS.map((h) => (
          <div
            key={h}
            className="text-[10px] text-muted-foreground"
            style={{
              width: CELL + GAP,
              visibility: HOUR_LABELS.includes(h) ? "visible" : "hidden",
            }}
          >
            {String(h).padStart(2, "0")}:00
          </div>
        ))}
      </div>

      {/* Grid rows */}
      {DAYS.map((day, dayIdx) => (
        <div key={day} className="flex items-center" style={{ marginBottom: GAP }}>
          {/* Day label */}
          <span
            className="text-[11px] text-muted-foreground shrink-0 text-right pr-2"
            style={{ width: DAY_LABEL_W }}
          >
            {day}
          </span>

          {/* Cells */}
          {HOURS.map((hour) => {
            const count = grid[`${dayIdx}-${hour}`] ?? 0;
            const bg = getColor(count, maxCount);
            return (
              <div
                key={hour}
                className="rounded-[4px] cursor-pointer transition-transform hover:scale-125 hover:z-10 relative shrink-0"
                style={{
                  width: CELL,
                  height: CELL,
                  marginRight: GAP,
                  backgroundColor: bg,
                  boxShadow: count > 0 ? `0 0 0 1px hsl(var(--primary) / 0.15)` : undefined,
                }}
                onMouseEnter={(e) => {
                  const rect = e.currentTarget.getBoundingClientRect();
                  setTooltip({
                    content: `${count} commit${count !== 1 ? "s" : ""} · ${day} ${String(hour).padStart(2, "0")}:00`,
                    x: rect.left + rect.width / 2,
                    y: rect.top,
                  });
                }}
                onMouseLeave={() => setTooltip(null)}
              />
            );
          })}
        </div>
      ))}

      {/* Legend */}
      <div className="flex items-center gap-2 mt-4 justify-end">
        <span className="text-[10px] text-muted-foreground">Less</span>
        {[0, 0.2, 0.4, 0.65, 0.85, 1].map((intensity) => (
          <div
            key={intensity}
            className="rounded-[3px]"
            style={{
              width: 14,
              height: 14,
              backgroundColor: intensity === 0
                ? "hsl(var(--muted))"
                : `hsl(var(--primary) / ${intensity})`,
            }}
          />
        ))}
        <span className="text-[10px] text-muted-foreground">More</span>
      </div>

      {/* Custom tooltip — rendered in fixed position to avoid overflow clipping */}
      {tooltip && (
        <div
          className="fixed z-50 pointer-events-none -translate-x-1/2 -translate-y-full mb-2"
          style={{ left: tooltip.x, top: tooltip.y - 8 }}
        >
          <div className="bg-card border border-border text-foreground text-xs font-medium px-2.5 py-1.5 rounded-lg shadow-lg whitespace-nowrap">
            {tooltip.content}
          </div>
          {/* Arrow */}
          <div className="w-2 h-2 bg-card border-r border-b border-border rotate-45 mx-auto -mt-1" />
        </div>
      )}
    </div>
  );
}
