"use client";

import { useState, useSyncExternalStore } from "react";

import { SinalizadorShellPronto } from "@/components/auth/sinalizador-shell-pronto";
import { Sidebar } from "@/components/shell/sidebar";

const CONSULTA_TELA_ESTREITA = "(max-width: 639px)";

function assinarMudancaDeViewport(notificar: () => void) {
  if (typeof window.matchMedia !== "function") return () => undefined;
  const consulta = window.matchMedia(CONSULTA_TELA_ESTREITA);
  consulta.addEventListener("change", notificar);
  return () => consulta.removeEventListener("change", notificar);
}

function telaEstreitaNoCliente() {
  return typeof window.matchMedia === "function"
    ? window.matchMedia(CONSULTA_TELA_ESTREITA).matches
    : false;
}

function telaEstreitaNoServidor() {
  return false;
}

export function ShellComSidebar({ children }: { children: React.ReactNode }) {
  const [sidebarRetraida, setSidebarRetraida] = useState(false);
  const telaEstreita = useSyncExternalStore(
    assinarMudancaDeViewport,
    telaEstreitaNoCliente,
    telaEstreitaNoServidor,
  );
  const sidebarEfetivamenteRetraida = sidebarRetraida || telaEstreita;

  return (
    <div
      className="flex min-h-0 flex-1 overflow-hidden bg-[var(--fundo-canvas)]"
      data-slot="page-canvas"
    >
      <Sidebar
        retraida={sidebarEfetivamenteRetraida}
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
