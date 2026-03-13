export interface ApiResponse<T> {
  success: boolean;
  data: T;
  error?: string;
  timestamp: string;
  requestId: string;
}

export interface UserProfile {
  id: string;
  username: string;
  email: string | null;
  avatarUrl: string | null;
}

export interface Repo {
  id: string;
  owner: string;
  name: string;
  fullName: string;
  isPrivate: boolean;
  syncStatus: "PENDING" | "SYNCING" | "DONE" | "FAILED";
  lastSyncedAt: string | null;
}

export interface SyncStatus {
  jobId: string | null;
  jobStatus: string;
  repoSyncStatus: string;
}

export interface DashboardSummary {
  weeklyCommits: number;
  monthlyPRsMerged: number;
  avgMergeTimeHours: number;
  currentStreak: number;
  recentPRs: PrSummary[];
}

export interface PrSummary {
  id: number;
  prNumber: number;
  title: string;
  state: "OPEN" | "CLOSED" | "MERGED";
  createdAt: string;
}

export interface HeatmapCell {
  day: number;
  hour: number;
  count: number;
}

export interface PRLifecycle {
  avgHoursToFirstReview: number;
  avgHoursToMerge: number;
  mergedCount: number;
  totalCount: number;
}

export interface PRSizeDistribution {
  buckets: Record<string, number>;
}

export interface ContributorStats {
  login: string;
  commits: number;
  linesAdded: number;
  linesRemoved: number;
}

export interface BusFactor {
  topContributor: string;
  topContributorPercentage: number;
  totalContributors: number;
}

export interface Streak {
  currentStreak: number;
  longestStreak: number;
}

export interface DigestPreferences {
  digestEnabled: boolean;
  digestDayOfWeek: number;
  digestHour: number;
  timezone: string;
}

export interface GitHubRepoSuggestion {
  id: number;
  name: string;
  fullName: string;
  privateRepo: boolean;
  owner: { login: string };
}
