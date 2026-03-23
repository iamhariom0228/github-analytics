"use client";

import { useRouter } from "next/navigation";
import { deleteAccount } from "@/lib/api/client";

export default function SettingsPage() {
  return (
    <div className="space-y-6 max-w-xl">
      <h1 className="text-2xl font-bold">Settings</h1>

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
