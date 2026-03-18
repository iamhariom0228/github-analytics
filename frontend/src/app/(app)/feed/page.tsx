"use client";

import { useDashboard, useCommitTrend, useActivityFeed } from "@/hooks/useAnalytics";
import { useRepos } from "@/hooks/useRepos";
import { DateRangePicker, usePresetDates, useDatePreset } from "@/components/shared/DateRangePicker";
import { GitMerge, GitPullRequest, GitCommitHorizontal, RefreshCw, Clock, Code } from "lucide-react";
import { format, parseISO, isToday, isYesterday, formatDistanceToNow } from "date-fns";
import type { ActivityEvent } from "@/types";

function Skeleton({ className = "" }: { className?: string }) {
  return <div className={`animate-pulse bg-muted rounded ${className}`} />;
}

type DisplayEventType = "COMMIT" | "PR_OPENED" | "PR_MERGED" | "PR_CLOSED" | "COMMIT_BATCH" | "SYNC";

interface FeedEvent {
  id: string;
  type: DisplayEventType;
  title: string;
  subtitle?: string;
  date: string;
  meta?: string;
  linesAdded?: number | null;
  linesRemoved?: number | null;
}

function eventIcon(type: DisplayEventType) {
  switch (type) {
    case "PR_MERGED":     return <GitMerge className="w-4 h-4 text-violet-500" />;
    case "PR_OPENED":     return <GitPullRequest className="w-4 h-4 text-blue-500" />;
    case "PR_CLOSED":     return <GitPullRequest className="w-4 h-4 text-red-500" />;
    case "COMMIT":
    case "COMMIT_BATCH":  return <GitCommitHorizontal className="w-4 h-4 text-green-500" />;
    case "SYNC":          return <RefreshCw className="w-4 h-4 text-muted-foreground" />;
  }
}

function eventDotColor(type: DisplayEventType) {
  switch (type) {
    case "PR_MERGED":     return "bg-violet-500";
    case "PR_OPENED":     return "bg-blue-500";
    case "PR_CLOSED":     return "bg-red-500";
    case "COMMIT":
    case "COMMIT_BATCH":  return "bg-green-500";
    case "SYNC":          return "bg-muted-foreground";
  }
}

function groupEventsByDate(events: FeedEvent[]): Map<string, FeedEvent[]> {
  const groups = new Map<string, FeedEvent[]>();
  for (const event of events) {
    try {
      const d = parseISO(event.date);
      let key: string;
      if (isToday(d)) key = "Today";
      else if (isYesterday(d)) key = "Yesterday";
      else key = format(d, "EEEE, MMMM d");
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key)!.push(event);
    } catch {
      // skip malformed dates
    }
  }
  return groups;
}

export default function FeedPage() {
  const [preset, setPreset] = useDatePreset();
  const { from, to } = usePresetDates(preset);

  // Primary: real activity feed from backend
  const { data: activityData, isLoading: activityLoading } = useActivityFeed(60);

  // Fallback sources when backend has no data yet
  const { data: dashboard, isLoading: dashLoading } = useDashboard();
  const { data: trend, isLoading: trendLoading } = useCommitTrend(from, to, "daily");
  const { data: repos } = useRepos();

  const isLoading = activityLoading || dashLoading || trendLoading;

  // Build unified event list
  const events: FeedEvent[] = [];

  if (activityData && activityData.length > 0) {
    // Use rich backend data
    for (const ev of activityData as ActivityEvent[]) {
      events.push({
        id: `backend-${ev.type}-${ev.sha ?? ev.prNumber ?? ev.occurredAt}`,
        type: ev.type as DisplayEventType,
        title: ev.title,
        subtitle: ev.repoFullName + (ev.sha ? ` · ${ev.sha.slice(0, 7)}` : ev.prNumber ? ` · #${ev.prNumber}` : ""),
        date: ev.occurredAt,
        linesAdded: ev.linesAdded,
        linesRemoved: ev.linesRemoved,
      });
    }
  } else {
    // Fallback: compose from existing endpoints
    if (dashboard?.recentPRs) {
      for (const pr of dashboard.recentPRs) {
        let type: DisplayEventType = "PR_OPENED";
        if (pr.state === "MERGED") type = "PR_MERGED";
        else if (pr.state === "CLOSED") type = "PR_CLOSED";
        events.push({
          id: `pr-${pr.id}`,
          type,
          title: pr.title,
          subtitle: `PR #${pr.prNumber}`,
          date: pr.createdAt,
          meta: pr.state === "MERGED" ? "Merged" : pr.state === "CLOSED" ? "Closed" : "Opened",
        });
      }
    }

    if (trend) {
      for (const point of trend) {
        if (point.count === 0) continue;
        const dateStr = `${point.date.split("T")[0]}T12:00:00.000Z`;
        events.push({
          id: `commits-${point.date}`,
          type: "COMMIT_BATCH",
          title: `${point.count} commit${point.count !== 1 ? "s" : ""}`,
          subtitle: format(parseISO(point.date), "EEEE, MMMM d"),
          date: dateStr,
        });
      }
    }
  }

  // Always append sync events from repos
  if (repos) {
    for (const repo of repos) {
      if (repo.lastSyncedAt) {
        events.push({
          id: `sync-${repo.id}`,
          type: "SYNC",
          title: `${repo.fullName} synced`,
          date: repo.lastSyncedAt,
          meta: formatDistanceToNow(parseISO(repo.lastSyncedAt), { addSuffix: true }),
        });
      }
    }
  }

  // Sort descending by date
  events.sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());

  const grouped = groupEventsByDate(events);
  const dayKeys = Array.from(grouped.keys());

  const usingRealData = !!(activityData && activityData.length > 0);

  return (
    <div className="space-y-8 max-w-2xl">
      <div className="flex items-center justify-between flex-wrap gap-4">
        <div>
          <h1 className="text-2xl font-bold">Activity Feed</h1>
          <p className="text-muted-foreground text-sm mt-1">
            A chronological view of your commits, PRs, and repository events.
          </p>
        </div>
        <DateRangePicker value={preset} onChange={(p) => setPreset(p)} />
      </div>

      {isLoading ? (
        <div className="space-y-6">
          {Array.from({ length: 3 }).map((_, g) => (
            <div key={g}>
              <Skeleton className="h-4 w-24 mb-4 rounded" />
              <div className="space-y-2">
                {Array.from({ length: 3 }).map((_, i) => (
                  <Skeleton key={i} className="h-16 rounded-xl" />
                ))}
              </div>
            </div>
          ))}
        </div>
      ) : events.length === 0 ? (
        <div className="bg-card border border-border rounded-xl p-12 text-center">
          <div className="text-4xl mb-4">📭</div>
          <p className="text-muted-foreground text-sm">No activity found for this period.</p>
          <p className="text-muted-foreground text-xs mt-1">
            Try expanding the date range or syncing your repositories.
          </p>
        </div>
      ) : (
        <div className="space-y-8">
          {dayKeys.map((dayKey) => {
            const dayEvents = grouped.get(dayKey)!;
            return (
              <div key={dayKey}>
                <div className="flex items-center gap-3 mb-4">
                  <h2 className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">
                    {dayKey}
                  </h2>
                  <div className="flex-1 h-px bg-border" />
                  <span className="text-xs text-muted-foreground">
                    {dayEvents.length} event{dayEvents.length !== 1 ? "s" : ""}
                  </span>
                </div>

                <div className="relative">
                  <div className="absolute left-[15px] top-0 bottom-0 w-px bg-border" />

                  <div className="space-y-1">
                    {dayEvents.map((event) => (
                      <div key={event.id} className="flex gap-4">
                        <div className="relative flex-shrink-0 flex items-start pt-4">
                          <div
                            className={`w-[9px] h-[9px] rounded-full ${eventDotColor(event.type)} border-2 border-background z-10`}
                          />
                        </div>

                        <div className="flex-1 bg-card border border-border rounded-xl px-4 py-3 mb-2 flex items-start gap-3 hover:border-border/80 transition-colors">
                          <div className="mt-0.5 shrink-0">{eventIcon(event.type)}</div>
                          <div className="flex-1 min-w-0">
                            <div className="text-sm font-medium leading-snug truncate">{event.title}</div>
                            {event.subtitle && (
                              <div className="text-xs text-muted-foreground mt-0.5">{event.subtitle}</div>
                            )}
                            {/* Lines added/removed for commits */}
                            {(event.linesAdded != null || event.linesRemoved != null) && (
                              <div className="flex items-center gap-2 mt-1 text-xs">
                                <Code className="w-3 h-3 text-muted-foreground" />
                                {event.linesAdded != null && (
                                  <span className="text-green-500">+{event.linesAdded.toLocaleString()}</span>
                                )}
                                {event.linesRemoved != null && (
                                  <span className="text-red-500">-{event.linesRemoved.toLocaleString()}</span>
                                )}
                              </div>
                            )}
                          </div>
                          {event.meta && (
                            <div className="shrink-0 flex items-center gap-1 text-xs text-muted-foreground">
                              <Clock className="w-3 h-3" />
                              {event.meta}
                            </div>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            );
          })}

          <div className="text-center text-xs text-muted-foreground py-4 border-t border-border">
            {events.length} total events
            {usingRealData ? " · individual commits & PRs" : " · aggregate view"}
          </div>
        </div>
      )}
    </div>
  );
}
