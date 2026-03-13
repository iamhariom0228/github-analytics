"use client";

import type { HeatmapCell } from "@/types";
import { DAYS } from "@/lib/utils";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";

interface Props {
  data: HeatmapCell[];
}

export function ContributionHeatmap({ data }: Props) {
  const maxCount = Math.max(...data.map((d) => d.count), 1);
  const grid: Record<string, number> = {};
  data.forEach((d) => { grid[`${d.day}-${d.hour}`] = d.count; });

  const getColor = (count: number) => {
    if (count === 0) return "#ebedf0";
    const intensity = count / maxCount;
    if (intensity < 0.25) return "#9be9a8";
    if (intensity < 0.5) return "#40c463";
    if (intensity < 0.75) return "#30a14e";
    return "#216e39";
  };

  return (
    <TooltipProvider>
      <div className="overflow-x-auto">
        <div className="inline-block">
          {/* Hour labels */}
          <div className="flex ml-8 mb-1">
            {[0, 3, 6, 9, 12, 15, 18, 21].map((h) => (
              <div key={h} className="text-xs text-muted-foreground" style={{ width: `${(24 / 8) * 14}px` }}>
                {String(h).padStart(2, "0")}:00
              </div>
            ))}
          </div>
          {/* Grid */}
          {DAYS.map((day, dayIdx) => (
            <div key={day} className="flex items-center mb-1">
              <span className="text-xs text-muted-foreground w-8">{day}</span>
              {Array.from({ length: 24 }, (_, hour) => {
                const count = grid[`${dayIdx}-${hour}`] ?? 0;
                return (
                  <Tooltip key={hour}>
                    <TooltipTrigger>
                      <div
                        className="w-3 h-3 rounded-sm mr-0.5 cursor-pointer"
                        style={{ backgroundColor: getColor(count) }}
                      />
                    </TooltipTrigger>
                    <TooltipContent>
                      <p>{count} commits — {day} {hour}:00</p>
                    </TooltipContent>
                  </Tooltip>
                );
              })}
            </div>
          ))}
        </div>
      </div>
    </TooltipProvider>
  );
}
