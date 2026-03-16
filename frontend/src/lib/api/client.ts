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
} from "@/types";
import type { ApiResponse } from "@/types";

const unwrap = <T>(res: { data: ApiResponse<T> }) => res.data.data;

// Auth
export const getMe = () =>
  apiClient.get<ApiResponse<UserProfile>>("/auth/me").then(unwrap);

export const logout = () =>
  apiClient.post<ApiResponse<void>>("/auth/logout").then(unwrap);

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
