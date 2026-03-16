"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  getDigestPreferences,
  updateDigestPreferences,
  sendDigestPreview,
  deleteAccount,
} from "@/lib/api/client";
import type { DigestPreferences } from "@/types";

const DAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

export default function SettingsPage() {
  const router = useRouter();
  const qc = useQueryClient();
  const { data: prefs } = useQuery({
    queryKey: ["digest-prefs"],
    queryFn: getDigestPreferences,
  });

  const [form, setForm] = useState<DigestPreferences>({
    digestEnabled: true,
    digestDayOfWeek: 1,
    digestHour: 9,
    timezone: "UTC",
  });

  useEffect(() => {
    if (prefs) setForm(prefs);
  }, [prefs]);

  const updateMutation = useMutation({
    mutationFn: (data: DigestPreferences) => updateDigestPreferences(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["digest-prefs"] }),
  });

  const previewMutation = useMutation({ mutationFn: sendDigestPreview });

  return (
    <div className="space-y-6 max-w-xl">
      <h1 className="text-2xl font-bold">Settings</h1>

      <div className="bg-card border border-border rounded-xl p-6 space-y-6">
        <h2 className="font-semibold">Weekly Email Digest</h2>

        {/* Toggle */}
        <div className="flex items-center justify-between">
          <div>
            <div className="text-sm font-medium">Enable weekly digest</div>
            <div className="text-xs text-muted-foreground">Receive a summary every week</div>
          </div>
          <button
            onClick={() => setForm((f) => ({ ...f, digestEnabled: !f.digestEnabled }))}
            className={`relative w-11 h-6 rounded-full transition-colors ${
              form.digestEnabled ? "bg-primary" : "bg-muted"
            }`}
          >
            <span
              className={`absolute top-1 w-4 h-4 rounded-full bg-white shadow transition-transform ${
                form.digestEnabled ? "translate-x-6" : "translate-x-1"
              }`}
            />
          </button>
        </div>

        {/* Day */}
        <div className="space-y-1">
          <label className="text-sm font-medium">Send on</label>
          <select
            value={form.digestDayOfWeek}
            onChange={(e) => setForm((f) => ({ ...f, digestDayOfWeek: Number(e.target.value) }))}
            className="w-full border border-input rounded-md px-3 py-2 text-sm bg-background"
          >
            {DAYS.map((d, i) => (
              <option key={d} value={i}>{d}</option>
            ))}
          </select>
        </div>

        {/* Hour */}
        <div className="space-y-1">
          <label className="text-sm font-medium">At hour (0-23)</label>
          <input
            type="number"
            min={0}
            max={23}
            value={form.digestHour}
            onChange={(e) => setForm((f) => ({ ...f, digestHour: Number(e.target.value) }))}
            className="w-full border border-input rounded-md px-3 py-2 text-sm bg-background"
          />
        </div>

        {/* Timezone */}
        <div className="space-y-1">
          <label className="text-sm font-medium">Timezone</label>
          <input
            type="text"
            value={form.timezone}
            onChange={(e) => setForm((f) => ({ ...f, timezone: e.target.value }))}
            placeholder="e.g. Asia/Kolkata"
            className="w-full border border-input rounded-md px-3 py-2 text-sm bg-background"
          />
        </div>

        <div className="flex gap-3">
          <button
            onClick={() => updateMutation.mutate(form)}
            disabled={updateMutation.isPending}
            className="bg-primary text-primary-foreground px-4 py-2 rounded-md text-sm font-medium disabled:opacity-50"
          >
            {updateMutation.isPending ? "Saving..." : "Save preferences"}
          </button>
          <button
            onClick={() => previewMutation.mutate()}
            disabled={previewMutation.isPending}
            className="border border-border px-4 py-2 rounded-md text-sm hover:bg-muted disabled:opacity-50"
          >
            {previewMutation.isPending ? "Sending..." : "Send preview now"}
          </button>
        </div>

        {previewMutation.isSuccess && (
          <p className="text-green-600 text-sm">Preview sent! Check your inbox.</p>
        )}
        {updateMutation.isSuccess && (
          <p className="text-green-600 text-sm">Preferences saved.</p>
        )}
      </div>

      {/* Danger Zone */}
      <div className="bg-card border border-destructive/50 rounded-xl p-6 space-y-4">
        <h2 className="font-semibold text-destructive">Danger Zone</h2>
        <div className="flex items-center justify-between">
          <div>
            <div className="text-sm font-medium">Disconnect GitHub Account</div>
            <div className="text-xs text-muted-foreground">
              Permanently deletes your account and all associated data. This cannot be undone.
            </div>
          </div>
          <button
            onClick={async () => {
              const confirmed = window.confirm(
                "Are you sure? This will delete all your data and cannot be undone."
              );
              if (!confirmed) return;
              await deleteAccount();
              window.location.href = "/";
            }}
            className="bg-destructive text-destructive-foreground px-4 py-2 rounded-md text-sm font-medium hover:bg-destructive/90"
          >
            Disconnect Account
          </button>
        </div>
      </div>
    </div>
  );
}
