"use client";

import { useState } from "react";
import { usePathname } from "next/navigation";

import { SinalizadorShellPronto } from "@/components/auth/sinalizador-shell-pronto";
import { AvisoContaTeste } from "@/components/auth/aviso-conta-teste";
import { NavegacaoInferior } from "@/components/shell/navegacao-inferior";
import { Sidebar } from "@/components/shell/sidebar";
import { ProvedorConversaEmTelaCheia, useConversaEmTelaCheia } from "@/lib/navegacao/conversa-em-tela-cheia";
import { useTelaEstreita } from "@/lib/navegacao/tela-estreita";
import { cn } from "@/lib/utils";
import { useFuncionalidadesHabilitadas } from "@/lib/config/use-funcionalidades";
import { fonteFrontendV2 } from "@/lib/config/fonte-frontend-v2";
import estilosV2 from "./shell-v2.module.css";

import { estiloDaLarguraDoSlot, useExpansaoDaSidebar } from "./expansao-da-sidebar";

export function ShellComSidebar({ children }: { children: React.ReactNode }) {
  return (
    <ProvedorConversaEmTelaCheia>
      <ShellInterno>{children}</ShellInterno>
    </ProvedorConversaEmTelaCheia>
  );
}

function ShellInterno({ children }: { children: React.ReactNode }) {
  const expansao = useExpansaoDaSidebar();
  const pathname = usePathname();
  const funcionalidades = useFuncionalidadesHabilitadas();
  const dashboardV2 = funcionalidades.data?.includes("dashboard_layout_v2") ?? false;
  const [dashboardFixada, setDashboardFixada] = useState<boolean | null>(null);
  const noDashboard = pathname === "/dashboard";
  const visualV2 = noDashboard && dashboardV2;
  const fixada = noDashboard ? dashboardFixada ?? !dashboardV2 : expansao.fixada;
  const expandida = noDashboard ? fixada || expansao.expandida : expansao.expandida;
  const alternarFixacao = noDashboard
    ? () => setDashboardFixada((atual) => !(atual ?? !dashboardV2))
    : expansao.alternarFixacao;
  const telaEstreita = useTelaEstreita();
  const { ativa: conversaEmTelaCheia } = useConversaEmTelaCheia();
  const mostrarBarraInferior = telaEstreita && !conversaEmTelaCheia;

  return (
    <div
      className={cn("flex min-h-0 flex-1 overflow-hidden bg-[var(--fundo-canvas)]", visualV2 && [estilosV2.canvas, fonteFrontendV2.variable])}
      data-slot="page-canvas"
    >
      {!telaEstreita && (
        <div
          className="relative h-full shrink-0"
          style={visualV2
            ? { ...estiloDaLarguraDoSlot(fixada), width: fixada ? 288 : 88 }
            : estiloDaLarguraDoSlot(fixada)}
          data-slot="sidebar-slot"
          data-fixada={fixada ? "true" : "false"}
          data-expandida={expandida ? "true" : "false"}
          data-sobreposta={expandida && !fixada ? "true" : "false"}
        >
          <Sidebar
            visualV2={visualV2}
            retraida={!expandida}
            fixada={fixada}
            onAlternar={alternarFixacao}
            onPonteiroEntrar={expansao.aoPonteiroEntrar}
            onPonteiroSair={expansao.aoPonteiroSair}
            onFocoDentro={expansao.aoFocoDentro}
            onFocoFora={expansao.aoFocoFora}
          />
        </div>
      )}
      <div className="flex h-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
        <AvisoContaTeste />
        {/*
          `[&>*]:shrink-0` é o que faz esta superfície ROLAR de verdade, e não é cosmético.
          Como o <main> é um flex column com altura definida, todo filho direto nasce com
          `flex-shrink: 1`: quando o conteúdo da página passa da altura da tela, o Flexbox
          ENCOLHE o filho até caber em vez de deixá-lo transbordar, e o encolhimento cascateia
          para os filhos dele — o último cartão do Dashboard chegava a 32px de altura com 282px
          de conteúdo, cortado pelo `overflow-hidden` do próprio Card. O resultado é o pior dos
          dois mundos: quase nada rola (scrollHeight 939 vs clientHeight 760) e o fim da página
          some. O `overflow-hidden` do page-canvas não tinha parte nisso — o corte acontecia
          dentro do <main>, antes de chegar lá.

          Com `shrink-0`, o filho fica no tamanho natural do conteúdo e o <main> rola. Páginas de
          painel (Atendimentos) não mudam: a raiz delas usa `h-full flex-1` (flex-basis 0 +
          grow 1), então continua resolvendo exatamente para a altura do <main> — o shrink nunca
          participava daquele cálculo, e a rolagem interna dos painéis segue intacta.
        */}
        <main
          className={cn(
            "flex h-full min-h-0 flex-1 flex-col overflow-y-auto [&>*]:shrink-0",
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
