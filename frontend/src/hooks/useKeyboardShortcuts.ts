"use client";
import { useEffect } from "react";
import { useRouter } from "next/navigation";

export function useKeyboardShortcuts() {
  const router = useRouter();

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      // Don't trigger when typing in inputs
      const active = document.activeElement;
      if (
        active instanceof HTMLInputElement ||
        active instanceof HTMLTextAreaElement ||
        active instanceof HTMLSelectElement ||
        (active as HTMLElement)?.isContentEditable
      )
        return;
      // Don't trigger with modifier keys
      if (e.metaKey || e.ctrlKey || e.altKey) return;

      switch (e.key.toLowerCase()) {
        case "d":
          router.push("/dashboard");
          break;
        case "r":
          router.push("/repos");
          break;
        case "a":
          router.push("/analytics");
          break;
        case "t":
          router.push("/team");
          break;
        case "s":
          router.push("/settings");
          break;
        case "e":
          router.push("/explore");
          break;
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [router]);
}
