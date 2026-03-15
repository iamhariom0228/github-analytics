"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import {
  LayoutDashboard,
  GitBranch,
  BarChart2,
  Users,
  Settings,
  LogOut,
  Sun,
  Moon,
} from "lucide-react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTheme } from "next-themes";
import { logout } from "@/lib/api/client";
import { useAuth } from "@/hooks/useAuth";
import { useEffect, useState } from "react";

const navItems = [
  { href: "/dashboard", icon: LayoutDashboard, label: "Dashboard" },
  { href: "/repos", icon: GitBranch, label: "Repositories" },
  { href: "/analytics", icon: BarChart2, label: "Analytics" },
  { href: "/team", icon: Users, label: "Team" },
  { href: "/settings", icon: Settings, label: "Settings" },
];

export function Sidebar() {
  const pathname = usePathname();
  const qc = useQueryClient();
  const { data: user } = useAuth();
  const { theme, setTheme } = useTheme();
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);

  const logoutMutation = useMutation({
    mutationFn: logout,
    onSuccess: () => {
      qc.clear();
      window.location.href = "/";
    },
  });

  return (
    <aside className="w-64 border-r border-border bg-card flex flex-col h-screen sticky top-0">
      {/* Brand */}
      <div className="p-6 border-b border-border flex items-center justify-between">
        <span className="font-bold text-lg">GitHub Analytics</span>
        <button
          onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
          className="p-1.5 rounded-md text-muted-foreground hover:bg-accent hover:text-foreground transition-colors"
          aria-label="Toggle theme"
        >
          {mounted && theme === "dark" ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
        </button>
      </div>

      {/* User */}
      {user && (
        <div className="px-4 py-3 border-b border-border flex items-center gap-3">
          {user.avatarUrl && (
            <img src={user.avatarUrl} alt={user.username} className="w-8 h-8 rounded-full" />
          )}
          <span className="text-sm font-medium truncate">{user.username}</span>
        </div>
      )}

      {/* Nav */}
      <nav className="flex-1 p-4 space-y-1">
        {navItems.map(({ href, icon: Icon, label }) => (
          <Link
            key={href}
            href={href}
            className={cn(
              "flex items-center gap-3 px-3 py-2 rounded-md text-sm transition-colors",
              pathname === href
                ? "bg-primary text-primary-foreground"
                : "text-muted-foreground hover:bg-accent hover:text-foreground"
            )}
          >
            <Icon className="w-4 h-4" />
            {label}
          </Link>
        ))}
      </nav>

      {/* Logout */}
      <div className="p-4 border-t border-border">
        <button
          onClick={() => logoutMutation.mutate()}
          className="flex items-center gap-3 px-3 py-2 rounded-md text-sm text-muted-foreground hover:bg-accent hover:text-foreground w-full transition-colors"
        >
          <LogOut className="w-4 h-4" />
          Logout
        </button>
      </div>
    </aside>
  );
}
