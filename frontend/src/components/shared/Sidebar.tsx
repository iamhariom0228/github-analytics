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
  Compass,
  Trophy,
  Target,
  Activity,
  GitPullRequestArrow,
  GitCompareArrows,
  Network,
  Keyboard,
  X,
} from "lucide-react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTheme } from "next-themes";
import { logout } from "@/lib/api/client";
import { useAuth } from "@/hooks/useAuth";
import { useCallback, useEffect, useState } from "react";
import { useKeyboardShortcuts } from "@/hooks/useKeyboardShortcuts";
import { ShortcutCheatsheet } from "./ShortcutCheatsheet";

const navItems = [
  { href: "/dashboard", icon: LayoutDashboard, label: "Dashboard" },
  { href: "/repos", icon: GitBranch, label: "Repositories" },
  { href: "/analytics", icon: BarChart2, label: "Analytics" },
  { href: "/team", icon: Users, label: "Team" },
  { href: "/collaboration", icon: Network, label: "Collaboration" },
  { href: "/review-queue", icon: GitPullRequestArrow, label: "Review Queue" },
  { href: "/compare", icon: GitCompareArrows, label: "Compare" },
  { href: "/feed", icon: Activity, label: "Activity Feed" },
  { href: "/achievements", icon: Trophy, label: "Achievements" },
  { href: "/goals", icon: Target, label: "Goals" },
  { href: "/explore", icon: Compass, label: "Explore" },
  { href: "/settings", icon: Settings, label: "Settings" },
];

interface SidebarProps {
  isOpen?: boolean;
  onClose?: () => void;
}

export function Sidebar({ isOpen = false, onClose }: SidebarProps) {
  const pathname = usePathname();
  const qc = useQueryClient();
  const { data: user } = useAuth();
  const { theme, setTheme } = useTheme();
  const [mounted, setMounted] = useState(false);
  const [shortcutsOpen, setShortcutsOpen] = useState(false);
  useEffect(() => setMounted(true), []);
  const openShortcuts = useCallback(() => setShortcutsOpen(true), []);
  useKeyboardShortcuts(openShortcuts);

  const logoutMutation = useMutation({
    mutationFn: logout,
    onSuccess: () => {
      qc.clear();
      window.location.href = "/";
    },
  });

  const sidebarContent = (
    <aside className="w-64 border-r border-border bg-card flex flex-col h-full">
      {/* Brand */}
      <div className="p-4 md:p-6 border-b border-border flex items-center justify-between">
        <span className="font-bold text-lg">GitHub Analytics</span>
        <div className="flex items-center gap-1">
          <button
            onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
            className="p-1.5 rounded-md text-muted-foreground hover:bg-accent hover:text-foreground transition-colors"
            aria-label="Toggle theme"
          >
            {mounted && theme === "dark" ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
          </button>
          {/* Close button — mobile only */}
          <button
            onClick={onClose}
            className="p-1.5 rounded-md text-muted-foreground hover:bg-accent hover:text-foreground transition-colors md:hidden"
            aria-label="Close menu"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* User */}
      {user && (
        <div className="px-4 py-3 border-b border-border flex items-center gap-3 min-w-0">
          {user.avatarUrl && (
            <img src={user.avatarUrl} alt={user.username} className="w-8 h-8 rounded-full shrink-0" />
          )}
          <span className="text-sm font-medium truncate min-w-0">{user.username}</span>
        </div>
      )}

      {/* Nav */}
      <nav className="flex-1 p-4 space-y-1 overflow-y-auto">
        {navItems.map(({ href, icon: Icon, label }) => (
          <Link
            key={href}
            href={href}
            onClick={onClose}
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

      {/* Logout + shortcuts */}
      <div className="p-4 border-t border-border space-y-1">
        <button
          onClick={() => setShortcutsOpen(true)}
          className="flex items-center gap-3 px-3 py-2 rounded-md text-sm text-muted-foreground hover:bg-accent hover:text-foreground w-full transition-colors"
        >
          <Keyboard className="w-4 h-4" />
          Shortcuts
          <kbd className="ml-auto text-xs border border-border rounded px-1 font-mono">?</kbd>
        </button>
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

  return (
    <>
      <ShortcutCheatsheet open={shortcutsOpen} onClose={() => setShortcutsOpen(false)} />

      {/* Desktop sidebar — always visible */}
      <div className="hidden md:flex h-screen sticky top-0 flex-shrink-0">
        {sidebarContent}
      </div>

      {/* Mobile drawer overlay */}
      {isOpen && (
        <div className="md:hidden fixed inset-0 z-50 flex">
          {/* Backdrop */}
          <div
            className="absolute inset-0 bg-black/50"
            onClick={onClose}
            aria-hidden="true"
          />
          {/* Drawer */}
          <div className="relative z-10 h-full overflow-y-auto">
            {sidebarContent}
          </div>
        </div>
      )}
    </>
  );
}
