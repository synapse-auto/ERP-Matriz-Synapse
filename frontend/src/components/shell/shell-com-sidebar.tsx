"use client";

import { SinalizadorShellPronto } from "@/components/auth/sinalizador-shell-pronto";
import { NavegacaoInferior } from "@/components/shell/navegacao-inferior";
import { Sidebar } from "@/components/shell/sidebar";
import { ProvedorConversaEmTelaCheia, useConversaEmTelaCheia } from "@/lib/navegacao/conversa-em-tela-cheia";
import { useTelaEstreita } from "@/lib/navegacao/tela-estreita";
import { cn } from "@/lib/utils";

import { estiloDaLarguraDaSidebar, useExpansaoDaSidebar } from "./expansao-da-sidebar";

export function ShellComSidebar({ children }: { children: React.ReactNode }) {
  return (
    <ProvedorConversaEmTelaCheia>
      <ShellInterno>{children}</ShellInterno>
    </ProvedorConversaEmTelaCheia>
  );
}

function ShellInterno({ children }: { children: React.ReactNode }) {
  const expansao = useExpansaoDaSidebar();
  const telaEstreita = useTelaEstreita();
  const { ativa: conversaEmTelaCheia } = useConversaEmTelaCheia();
  const mostrarBarraInferior = telaEstreita && !conversaEmTelaCheia;

  return (
    <div
      className="flex min-h-0 flex-1 overflow-hidden bg-[var(--fundo-canvas)]"
      data-slot="page-canvas"
    >
      {!telaEstreita && (
        <div
          className="relative h-full shrink-0"
          style={estiloDaLarguraDaSidebar(expansao.expandida)}
          data-slot="sidebar-slot"
          data-fixada={expansao.fixada ? "true" : "false"}
          data-expandida={expansao.expandida ? "true" : "false"}
        >
          <Sidebar
            retraida={!expansao.expandida}
            fixada={expansao.fixada}
            onAlternar={expansao.alternarFixacao}
            onPonteiroEntrar={expansao.aoPonteiroEntrar}
            onPonteiroSair={expansao.aoPonteiroSair}
            onFocoDentro={expansao.aoFocoDentro}
            onFocoFora={expansao.aoFocoFora}
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
