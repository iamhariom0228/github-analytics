"use client";

import {
  AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid,
} from "recharts";
import { Skeleton } from "@/components/shared/Skeleton";

type TrendPoint = { date: string; fullDate: string; commits: number };

interface Props {
  data: TrendPoint[];
  isLoading: boolean;
  granularity: "daily" | "weekly";
  onGranularityChange: (g: "daily" | "weekly") => void;
}

export function CommitTrendChart({ data, isLoading, granularity, onGranularityChange }: Props) {
  return (
    <div className="bg-card border border-border rounded-xl p-6">
      <div className="flex items-center justify-between gap-2 flex-wrap mb-4">
        <h2 className="font-semibold">Commit Activity</h2>
        <div className="flex gap-0 text-xs border border-border rounded-md overflow-hidden">
          {(["daily", "weekly"] as const).map((g) => (
            <button
              key={g}
              onClick={() => onGranularityChange(g)}
              className={`px-3 py-1 capitalize transition-colors ${
                granularity === g ? "bg-primary text-primary-foreground" : "hover:bg-muted"
              }`}
            >
              {g}
            </button>
          ))}
        </div>
      </div>
      {isLoading ? (
        <Skeleton className="h-52" />
      ) : data.length === 0 ? (
        <div className="h-52 flex items-center justify-center text-muted-foreground text-sm">
          No commit data in this period — try a wider date range
        </div>
      ) : (
        <ResponsiveContainer width="100%" height={210}>
          <AreaChart data={data}>
            <defs>
              <linearGradient id="commitGradient" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="hsl(var(--primary))" stopOpacity={0.3} />
                <stop offset="95%" stopColor="hsl(var(--primary))" stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
            <XAxis dataKey="date" tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} interval="preserveStartEnd" />
            <YAxis tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} tickLine={false} axisLine={false} allowDecimals={false} />
            <Tooltip
              contentStyle={{ background: "hsl(var(--card))", border: "1px solid hsl(var(--border))", borderRadius: "8px", fontSize: "12px" }}
              labelFormatter={(_, payload) => payload?.[0]?.payload?.fullDate ?? ""}
            />
            <Area type="monotone" dataKey="commits" stroke="hsl(var(--primary))" strokeWidth={2} fill="url(#commitGradient)" />
          </AreaChart>
        </ResponsiveContainer>
      )}
    </div>
  );
}
