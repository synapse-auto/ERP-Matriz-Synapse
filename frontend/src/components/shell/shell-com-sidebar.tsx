"use client";

import { useState } from "react";

import { SinalizadorShellPronto } from "@/components/auth/sinalizador-shell-pronto";
import { Sidebar } from "@/components/shell/sidebar";

export function ShellComSidebar({ children }: { children: React.ReactNode }) {
  const [sidebarRetraida, setSidebarRetraida] = useState(false);

  return (
    <div
      className="flex min-h-0 flex-1 overflow-hidden bg-[var(--fundo-canvas)]"
      data-slot="page-canvas"
    >
      <Sidebar
        retraida={sidebarRetraida}
        onAlternar={() => setSidebarRetraida((atual) => !atual)}
      />
      <div className="min-w-0 flex-1 overflow-x-hidden">
        <main
          className="flex h-full min-h-0 flex-col overflow-y-auto"
          data-slot="page-surface"
        >
          {children}
        </main>
        <SinalizadorShellPronto />
      </div>
    </div>
  );
}
