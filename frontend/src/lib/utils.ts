import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatHours(hours: number): string {
  if (hours < 1) return `${Math.round(hours * 60)}m`;
  if (hours < 24) return `${hours.toFixed(1)}h`;
  return `${(hours / 24).toFixed(1)}d`;
}

export function formatNumber(n: number): string {
  return new Intl.NumberFormat().format(n);
}

export const DAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
export const PR_SIZE_COLORS: Record<string, string> = {
  XS: "#22c55e",
  S:  "#84cc16",
  M:  "#eab308",
  L:  "#f97316",
  XL: "#ef4444",
};
