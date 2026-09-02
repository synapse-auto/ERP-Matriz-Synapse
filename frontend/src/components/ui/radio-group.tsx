"use client";

import type { ReactNode } from "react";
import { Radio as RadioPrimitive } from "@base-ui/react/radio";
import { RadioGroup as RadioGroupPrimitive } from "@base-ui/react/radio-group";

import { cn } from "@/lib/utils";

function RadioGroup({ className, ...props }: RadioGroupPrimitive.Props) {
  return (
    <RadioGroupPrimitive
      data-slot="radio-group"
      className={cn("flex flex-col gap-2", className)}
      {...props}
    />
  );
}

function RadioItem({
  className,
  children,
  ...props
}: RadioPrimitive.Root.Props & { children: ReactNode }) {
  return (
    <RadioPrimitive.Root
      data-slot="radio-item"
      className={cn(
        "group flex cursor-pointer items-center gap-2 rounded-md border border-border px-3 py-2 text-sm text-foreground outline-none transition-colors hover:bg-muted/60 focus-visible:ring-2 focus-visible:ring-ring data-checked:border-primary data-checked:bg-primary/5",
        className,
      )}
      {...props}
    >
      <span
        className="flex size-4 shrink-0 items-center justify-center rounded-full border border-border group-data-checked:border-primary"
        aria-hidden
      >
        <RadioPrimitive.Indicator className="size-2 rounded-full bg-primary" />
      </span>
      <span className="min-w-0 flex-1">{children}</span>
    </RadioPrimitive.Root>
  );
}

export { RadioGroup, RadioItem };
