import axios from "axios";

const apiClient = axios.create({
  baseURL: "/api/backend",
  withCredentials: true, // send httpOnly JWT cookie
  headers: {
    "Content-Type": "application/json",
  },
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Redirect to login on 401
      if (typeof window !== "undefined") {
        window.location.href = "/";
      }
    } else if (error.response?.status === 403) {
      // Redirect to login on 403 (forbidden / insufficient permissions)
      if (typeof window !== "undefined") {
        window.location.href = "/";
      }
    }
    return Promise.reject(error);
  }
);

export default apiClient;

// ----- API functions -----

import type {
  UserProfile,
  Repo,
  SyncStatus,
  DashboardSummary,
  HeatmapCell,
  PRLifecycle,
  PRSizeDistribution,
  ReviewsSummary,
  ContributorStats,
  BusFactor,
  Streak,
  DigestPreferences,
  GitHubRepoSuggestion,
  PrSummary,
  Insight,
  CommitTrendPoint,
  Overview,
  RepoHealth,
  AiSummary,
  ActivityEvent,
  CollaborationData,
  PublicRepoStats,
  PRMergeRatePoint,
  ReviewerCoverage,
  StarsForksSnapshot,
  ReleaseTrendPoint,
  IssueAnalytics,
  RepoLanguageStat,
  RepoCompare,
  ContributorCompare,
  ShareSnapshot,
  CreateShareResponse,
} from "@/types";
import type { ApiResponse } from "@/types";

const unwrap = <T>(res: { data: ApiResponse<T> }) => res.data.data;

// Auth
export const getMe = () =>
  apiClient.get<ApiResponse<UserProfile>>("/auth/me").then(unwrap);

export const logout = () =>
  axios.post("/api/auth/logout").then(() => undefined);

// Repos
export const getRepos = () =>
  apiClient.get<ApiResponse<Repo[]>>("/repos").then(unwrap);

export const addRepo = (owner: string, name: string) =>
  apiClient.post<ApiResponse<Repo>>("/repos", { owner, name }).then(unwrap);

export const deleteRepo = (id: string) =>
  apiClient.delete<ApiResponse<void>>(`/repos/${id}`).then(unwrap);

export const triggerSync = (id: string) =>
  apiClient.post<ApiResponse<SyncStatus>>(`/repos/${id}/sync`).then(unwrap);

export const getSyncStatus = (id: string) =>
  apiClient.get<ApiResponse<SyncStatus>>(`/repos/${id}/sync-status`).then(unwrap);

export const getRepoSuggestions = () =>
  apiClient.get<ApiResponse<GitHubRepoSuggestion[]>>("/repos/suggestions").then(unwrap);

export const forkRepo = (owner: string, repo: string) =>
  apiClient.post<ApiResponse<{ htmlUrl: string }>>("/repos/fork", { owner, repo }).then(unwrap);

// Dashboard
export const getDashboard = () =>
  apiClient.get<ApiResponse<DashboardSummary>>("/dashboard").then(unwrap);

// Analytics
export const getHeatmap = (repoId?: string, timezone?: string, from?: string, to?: string) =>
  apiClient
    .get<ApiResponse<HeatmapCell[]>>("/analytics/commits/heatmap", {
      params: { repoId, timezone, from, to },
    })
    .then(unwrap);

export const getPRLifecycle = (from: string, to: string) =>
  apiClient
    .get<ApiResponse<PRLifecycle>>("/analytics/prs/lifecycle", { params: { from, to } })
    .then(unwrap);

export const getPRSizeDistribution = (from: string, to: string) =>
  apiClient
    .get<ApiResponse<PRSizeDistribution>>("/analytics/prs/size-distribution", {
      params: { from, to },
    })
    .then(unwrap);

export const getReviewsSummary = (from: string, to: string) =>
  apiClient
    .get<ApiResponse<ReviewsSummary>>("/analytics/reviews", { params: { from, to } })
    .then(unwrap);

export const getStreak = (timezone?: string) =>
  apiClient
    .get<ApiResponse<Streak>>("/analytics/streak", { params: { timezone } })
    .then(unwrap);

export const getLeaderboard = (repoId: string, from: string, to: string) =>
  apiClient
    .get<ApiResponse<ContributorStats[]>>("/analytics/team/leaderboard", {
      params: { repoId, from, to },
    })
    .then(unwrap);

export const getBusFactor = (repoId: string) =>
  apiClient
    .get<ApiResponse<BusFactor>>("/analytics/team/bus-factor", { params: { repoId } })
    .then(unwrap);

export const getStalePRs = (repoId: string, olderThanDays = 7) =>
  apiClient
    .get<ApiResponse<PrSummary[]>>("/analytics/team/stale-prs", {
      params: { repoId, olderThanDays },
    })
    .then(unwrap);

export const getInsights = (timezone?: string, from?: string, to?: string) =>
  apiClient
    .get<ApiResponse<Insight[]>>("/analytics/insights", { params: { timezone, from, to } })
    .then(unwrap);

export const getCommitTrend = (from: string, to: string, granularity = "daily") =>
  apiClient
    .get<ApiResponse<CommitTrendPoint[]>>("/analytics/commits/trend", {
      params: { from, to, granularity },
    })
    .then(unwrap);

export const getOverview = (from: string, to: string) =>
  apiClient
    .get<ApiResponse<Overview>>("/analytics/overview", { params: { from, to } })
    .then(unwrap);

export const getRepoHealth = (repoId: string) =>
  apiClient
    .get<ApiResponse<RepoHealth>>(`/analytics/repos/${repoId}/health`)
    .then(unwrap);

export const getAiSummary = (timezone?: string, from?: string, to?: string) =>
  apiClient
    .get<ApiResponse<AiSummary>>("/analytics/ai-summary", { params: { timezone, from, to } })
    .then(unwrap);

// Digest
export const getDigestPreferences = () =>
  apiClient.get<ApiResponse<DigestPreferences>>("/digest/preferences").then(unwrap);

export const updateDigestPreferences = (prefs: DigestPreferences) =>
  apiClient
    .put<ApiResponse<DigestPreferences>>("/digest/preferences", prefs)
    .then(unwrap);

export const sendDigestPreview = () =>
  apiClient.post<ApiResponse<void>>("/digest/preview").then(unwrap);

export const deleteAccount = () =>
  apiClient.delete<ApiResponse<void>>("/auth/account").then(unwrap);

// Activity Feed
export const getActivityFeed = (limit = 40) =>
  apiClient
    .get<ApiResponse<ActivityEvent[]>>("/analytics/activity", { params: { limit } })
    .then(unwrap);

// Review Queue
export const getReviewQueue = () =>
  apiClient.get<ApiResponse<import("@/types").ReviewQueueItem[]>>("/analytics/review-queue").then(unwrap);

// Collaboration
export const getCollaboration = (from: string, to: string) =>
  apiClient
    .get<ApiResponse<CollaborationData>>("/analytics/collaboration", { params: { from, to } })
    .then(unwrap);

// Public Repo Stats
export const getPublicRepoStats = (repoId: string) =>
  apiClient
    .get<ApiResponse<PublicRepoStats>>(`/analytics/repos/${repoId}/stats`)
    .then(unwrap);

// Repo Commit Trend
export const getRepoCommitTrend = (repoId: string, from: string, to: string) =>
  apiClient
    .get<ApiResponse<CommitTrendPoint[]>>(`/analytics/repos/${repoId}/commit-trend`, { params: { from, to } })
    .then(unwrap);

// PR Merge Rate Trend
export const getPRMergeRateTrend = (from: string, to: string) =>
  apiClient
    .get<ApiResponse<PRMergeRatePoint[]>>("/analytics/prs/merge-rate-trend", { params: { from, to } })
    .then(unwrap);

// Reviewer Coverage
export const getReviewerCoverage = (from: string, to: string) =>
  apiClient
    .get<ApiResponse<ReviewerCoverage>>("/analytics/prs/reviewer-coverage", { params: { from, to } })
    .then(unwrap);

// Churn Leaderboard
export const getChurnLeaderboard = (repoId: string, from: string, to: string) =>
  apiClient
    .get<ApiResponse<ContributorStats[]>>("/analytics/team/churn", { params: { repoId, from, to } })
    .then(unwrap);

// Stars & Forks Trend
export const getStarsForksTrend = (repoId: string) =>
  apiClient
    .get<ApiResponse<StarsForksSnapshot[]>>(`/analytics/repos/${repoId}/stars-forks-trend`)
    .then(unwrap);

// Release Trend
export const getReleaseTrend = (repoId: string) =>
  apiClient
    .get<ApiResponse<ReleaseTrendPoint[]>>(`/analytics/repos/${repoId}/release-trend`)
    .then(unwrap);

// Issue Analytics
export const getIssueAnalytics = (repoId: string) =>
  apiClient
    .get<ApiResponse<IssueAnalytics>>(`/analytics/repos/${repoId}/issues`)
    .then(unwrap);

// Language Bytes
export const getLanguageBytes = (repoId: string) =>
  apiClient
    .get<ApiResponse<RepoLanguageStat[]>>(`/analytics/repos/${repoId}/language-bytes`)
    .then(unwrap);

// Compare Two Repos
export const compareRepos = (repoIds: string[]) =>
  apiClient
    .get<ApiResponse<RepoCompare[]>>("/analytics/compare/repos", { params: { repoIds: repoIds.join(",") } })
    .then(unwrap);

// Compare Two Contributors
export const compareContributors = (logins: string[]) =>
  apiClient
    .get<ApiResponse<ContributorCompare[]>>("/analytics/compare/contributors", { params: { logins: logins.join(",") } })
    .then(unwrap);

// Share Snapshot
export const createShare = (timezone?: string, from?: string, to?: string) =>
  apiClient
    .post<ApiResponse<CreateShareResponse>>("/analytics/share", null, { params: { timezone, from, to } })
    .then(unwrap);

// Get Public Share
export const getPublicShare = (token: string) =>
  axios
    .get<ApiResponse<ShareSnapshot>>(`/api/backend/public/share/${token}`)
    .then((res) => res.data.data);
