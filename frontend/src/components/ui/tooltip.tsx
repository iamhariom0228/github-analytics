"use client";

import * as TooltipPrimitive from "@radix-ui/react-tooltip";
import { cn } from "@/lib/utils";

const TooltipProvider = TooltipPrimitive.Provider;
const Tooltip = TooltipPrimitive.Root;
const TooltipTrigger = TooltipPrimitive.Trigger;

const TooltipContent = ({
  className,
  sideOffset = 4,
  children,
  ...props
}: React.ComponentPropsWithoutRef<typeof TooltipPrimitive.Content>) => (
  <TooltipPrimitive.Content
    sideOffset={sideOffset}
    className={cn(
      "z-50 overflow-hidden rounded-md border bg-popover px-3 py-1.5 text-xs text-popover-foreground shadow-md",
      className
    )}
    {...props}
  >
    {children}
    <TooltipPrimitive.Arrow className="fill-popover" />
  </TooltipPrimitive.Content>
);

export { TooltipProvider, Tooltip, TooltipTrigger, TooltipContent };
