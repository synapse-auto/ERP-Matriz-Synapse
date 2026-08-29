"use client";

import { useState } from "react";

import { SinalizadorShellPronto } from "@/components/auth/sinalizador-shell-pronto";
import { NavegacaoInferior } from "@/components/shell/navegacao-inferior";
import { Sidebar } from "@/components/shell/sidebar";
import { ProvedorConversaEmTelaCheia, useConversaEmTelaCheia } from "@/lib/navegacao/conversa-em-tela-cheia";
import { useTelaEstreita } from "@/lib/navegacao/tela-estreita";
import { cn } from "@/lib/utils";

export function ShellComSidebar({ children }: { children: React.ReactNode }) {
  return (
    <ProvedorConversaEmTelaCheia>
      <ShellInterno>{children}</ShellInterno>
    </ProvedorConversaEmTelaCheia>
  );
}

function ShellInterno({ children }: { children: React.ReactNode }) {
  const [sidebarFixada, setSidebarFixada] = useState(false);
  const [sidebarTemporaria, setSidebarTemporaria] = useState(false);
  const telaEstreita = useTelaEstreita();
  const { ativa: conversaEmTelaCheia } = useConversaEmTelaCheia();
  const mostrarBarraInferior = telaEstreita && !conversaEmTelaCheia;
  const expandida = sidebarFixada || sidebarTemporaria;

  return (
    <div
      className="flex min-h-0 flex-1 overflow-hidden bg-[var(--fundo-canvas)]"
      data-slot="page-canvas"
    >
      {!telaEstreita && (
        <div
          className={cn(
            "relative h-full shrink-0 transition-[width] duration-200",
            sidebarFixada ? "w-[260px]" : "w-[76px]",
          )}
          data-slot="sidebar-slot"
          data-fixada={sidebarFixada ? "true" : "false"}
        >
          <Sidebar
            retraida={!expandida}
            fixada={sidebarFixada}
            sobreposta={!sidebarFixada}
            onAlternar={() => setSidebarFixada((atual) => !atual)}
            onPonteiroEntrar={() => setSidebarTemporaria(true)}
            onPonteiroSair={() => setSidebarTemporaria(false)}
            onFocoDentro={() => setSidebarTemporaria(true)}
            onFocoFora={() => setSidebarTemporaria(false)}
          />
        </div>
      )}
      <div className="flex h-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <main
          className={cn(
            "flex h-full min-h-0 flex-1 flex-col overflow-y-auto",
            mostrarBarraInferior && "pb-[calc(4.5rem+env(safe-area-inset-bottom,0px))]",
          )}
          data-slot="page-surface"
        >
          {children}
        </main>
        {mostrarBarraInferior && <NavegacaoInferior />}
        <SinalizadorShellPronto />
      </div>
    </div>
  );
}
