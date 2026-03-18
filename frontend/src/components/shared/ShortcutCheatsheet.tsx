"use client";

import { useEffect } from "react";
import { X, Keyboard } from "lucide-react";

const shortcuts = [
  { key: "D", description: "Go to Dashboard" },
  { key: "R", description: "Go to Repositories" },
  { key: "A", description: "Go to Analytics" },
  { key: "T", description: "Go to Team" },
  { key: "S", description: "Go to Settings" },
  { key: "E", description: "Go to Explore" },
  { key: "?", description: "Show keyboard shortcuts" },
];

interface Props {
  open: boolean;
  onClose: () => void;
}

export function ShortcutCheatsheet({ open, onClose }: Props) {
  useEffect(() => {
    if (!open) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        className="bg-card border border-border rounded-xl p-6 w-full max-w-sm shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between mb-5">
          <div className="flex items-center gap-2">
            <Keyboard className="w-5 h-5 text-primary" />
            <h2 className="font-semibold text-base">Keyboard Shortcuts</h2>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded-md text-muted-foreground hover:text-foreground hover:bg-accent transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
        <div className="space-y-2">
          {shortcuts.map(({ key, description }) => (
            <div key={key} className="flex items-center justify-between">
              <span className="text-sm text-muted-foreground">{description}</span>
              <kbd className="inline-flex items-center px-2 py-0.5 rounded border border-border bg-muted text-xs font-mono font-semibold">
                {key}
              </kbd>
            </div>
          ))}
        </div>
        <p className="text-xs text-muted-foreground mt-5 text-center">
          Shortcuts are disabled when typing in inputs.
        </p>
      </div>
    </div>
  );
}
