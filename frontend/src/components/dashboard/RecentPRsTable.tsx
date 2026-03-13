"use client";

import type { PrSummary } from "@/types";
import { cn } from "@/lib/utils";
import { formatDistanceToNow } from "date-fns";

const stateColor: Record<string, string> = {
  OPEN: "text-green-500 bg-green-500/10",
  MERGED: "text-purple-500 bg-purple-500/10",
  CLOSED: "text-red-500 bg-red-500/10",
};

export function RecentPRsTable({ prs }: { prs: PrSummary[] }) {
  if (!prs.length) {
    return <p className="text-muted-foreground text-sm">No recent pull requests.</p>;
  }
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border text-muted-foreground">
            <th className="text-left py-2 pr-4">PR</th>
            <th className="text-left py-2 pr-4">Title</th>
            <th className="text-left py-2 pr-4">State</th>
            <th className="text-left py-2">Opened</th>
          </tr>
        </thead>
        <tbody>
          {prs.map((pr) => (
            <tr key={pr.id} className="border-b border-border/50 hover:bg-muted/50">
              <td className="py-2 pr-4 text-muted-foreground">#{pr.prNumber}</td>
              <td className="py-2 pr-4 max-w-xs truncate">{pr.title}</td>
              <td className="py-2 pr-4">
                <span className={cn("px-2 py-0.5 rounded text-xs font-medium", stateColor[pr.state])}>
                  {pr.state}
                </span>
              </td>
              <td className="py-2 text-muted-foreground">
                {formatDistanceToNow(new Date(pr.createdAt), { addSuffix: true })}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
