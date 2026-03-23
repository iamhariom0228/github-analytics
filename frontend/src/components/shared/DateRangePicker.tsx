"use client";

import { subDays, subMonths, subYears, formatISO } from "date-fns";
import { useMemo, useState } from "react";

const DATE_PRESET_KEY = "global:datePreset";

export function useDatePreset(): [DatePreset, (p: DatePreset) => void] {
  const [preset, setPreset] = useState<DatePreset>(() =>
    (typeof window !== "undefined" ? (localStorage.getItem(DATE_PRESET_KEY) as DatePreset) : null) ?? "30d"
  );
  return [preset, (p: DatePreset) => {
    setPreset(p);
    if (typeof window !== "undefined") localStorage.setItem(DATE_PRESET_KEY, p);
  }];
}

const PRESETS = [
  { label: "7d",  getDays: () => subDays(new Date(), 7) },
  { label: "30d", getDays: () => subDays(new Date(), 30) },
  { label: "90d", getDays: () => subDays(new Date(), 90) },
  { label: "6m",  getDays: () => subMonths(new Date(), 6) },
  { label: "1y",  getDays: () => subYears(new Date(), 1) },
  { label: "All", getDays: () => new Date("2000-01-01") },
] as const;

export type DatePreset = typeof PRESETS[number]["label"];

interface Props {
  value: DatePreset;
  onChange: (preset: DatePreset, from: string, to: string) => void;
}

export function DateRangePicker({ value, onChange }: Props) {
  const handleSelect = (preset: typeof PRESETS[number]) => {
    const from = formatISO(preset.getDays());
    const to = formatISO(new Date());
    onChange(preset.label, from, to);
  };

  return (
    <div className="flex flex-wrap gap-1 bg-muted/50 rounded-lg p-1">
      {PRESETS.map((p) => (
        <button
          key={p.label}
          onClick={() => handleSelect(p)}
          className={`px-3 py-1 text-xs font-medium rounded-md transition-colors ${
            value === p.label
              ? "bg-background shadow text-foreground"
              : "text-muted-foreground hover:text-foreground"
          }`}
        >
          {p.label}
        </button>
      ))}
    </div>
  );
}

export function usePresetDates(preset: DatePreset): { from: string; to: string } {
  return useMemo(() => {
    const p = PRESETS.find((x) => x.label === preset)!;
    return {
      from: formatISO(p.getDays()),
      to: formatISO(new Date()),
    };
  }, [preset]);
}
