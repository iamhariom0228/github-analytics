"use client";

import { useRef, useEffect, useCallback } from "react";
import dynamic from "next/dynamic";
import type { CollaborationData } from "@/types";

const ForceGraph2D = dynamic(() => import("react-force-graph-2d"), { ssr: false });

interface Node { id: string; val: number; color: string; isSelf: boolean; }
interface Link { source: string; target: string; value: number; }

interface Props {
  data: CollaborationData;
  selfLogin: string;
}

function buildGraph(data: CollaborationData, selfLogin: string) {
  const nodeMap = new Map<string, Node>();
  const linkMap = new Map<string, Link>();

  const ensureNode = (login: string) => {
    if (!nodeMap.has(login)) {
      nodeMap.set(login, {
        id: login,
        val: 1,
        color: login === selfLogin ? "hsl(var(--primary))" : "#64748b",
        isSelf: login === selfLogin,
      });
    }
  };

  ensureNode(selfLogin);

  for (const entry of data.topReviewersOfMe) {
    ensureNode(entry.login);
    const key = [entry.login, selfLogin].sort().join("__");
    const existing = linkMap.get(key);
    linkMap.set(key, {
      source: entry.login,
      target: selfLogin,
      value: (existing?.value ?? 0) + entry.count,
    });
    const node = nodeMap.get(entry.login)!;
    node.val = Math.max(node.val, Math.sqrt(entry.count) * 2);
  }

  for (const entry of data.topPeopleIReview) {
    ensureNode(entry.login);
    const key = [selfLogin, entry.login].sort().join("__");
    const existing = linkMap.get(key);
    linkMap.set(key, {
      source: selfLogin,
      target: entry.login,
      value: (existing?.value ?? 0) + entry.count,
    });
    const node = nodeMap.get(entry.login)!;
    node.val = Math.max(node.val, Math.sqrt(entry.count) * 2);
  }

  return {
    nodes: Array.from(nodeMap.values()),
    links: Array.from(linkMap.values()),
  };
}

export function ContributorNetworkGraph({ data, selfLogin }: Props) {
  const graphData = buildGraph(data, selfLogin);
  const fgRef = useRef<{ centerAt: (x: number, y: number, ms: number) => void } | null>(null);

  useEffect(() => {
    setTimeout(() => fgRef.current?.centerAt(0, 0, 400), 300);
  }, []);

  const nodeLabel = useCallback((node: unknown) => {
    const n = node as Node;
    return `<div style="background:#1e293b;color:#f1f5f9;padding:4px 8px;border-radius:6px;font-size:12px">${n.id}</div>`;
  }, []);

  const linkColor = useCallback(
    (link: unknown) => {
      const l = link as Link;
      return `rgba(100,116,139,${Math.min(0.8, 0.15 + l.value * 0.05)})`;
    },
    []
  );

  const linkWidth = useCallback((link: unknown) => {
    const l = link as Link;
    return Math.min(6, 1 + l.value * 0.4);
  }, []);

  if (graphData.nodes.length <= 1) {
    return (
      <div className="h-64 flex items-center justify-center text-muted-foreground text-sm">
        Not enough collaboration data to render graph.
      </div>
    );
  }

  return (
    <div className="w-full rounded-xl overflow-hidden border border-border bg-card" style={{ height: 400 }}>
      <ForceGraph2D
        ref={fgRef as any}
        graphData={graphData as any}
        nodeLabel={nodeLabel as any}
        nodeVal="val"
        nodeColor="color"
        linkColor={linkColor as any}
        linkWidth={linkWidth as any}
        linkDirectionalArrowLength={4}
        linkDirectionalArrowRelPos={1}
        backgroundColor="transparent"
        nodeCanvasObjectMode={() => "after"}
        nodeCanvasObject={(node: any, ctx: any, globalScale: any) => {
          const label = (node as Node).id;
          const fontSize = Math.max(8, 12 / globalScale);
          ctx.font = `${fontSize}px sans-serif`;
          ctx.fillStyle = (node as Node).isSelf
            ? "hsl(var(--primary))"
            : "hsl(var(--muted-foreground))";
          ctx.textAlign = "center";
          ctx.textBaseline = "top";
          ctx.fillText(label, node.x ?? 0, (node.y ?? 0) + 8);
        }}
        cooldownTicks={100}
        d3AlphaDecay={0.02}
        d3VelocityDecay={0.3}
      />
    </div>
  );
}
