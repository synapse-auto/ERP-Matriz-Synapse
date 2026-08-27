"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Activity, KeyRound, MessageSquareText, ShieldCheck } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { useTextos } from "@/lib/config/textos-provider";
import { cn } from "@/lib/utils";

const ABAS = [
  { chave: "visaoGeral", rota: "/administracao", icone: Activity, exata: true },
  { chave: "acessos", rota: "/administracao/acessos", icone: KeyRound, exata: false },
  { chave: "feedbacks", rota: "/administracao/feedbacks", icone: MessageSquareText, exata: false },
] as const;

export function LayoutAdministracao({ children }: { children: React.ReactNode }) {
  const textos = useTextos().administracao;
  const pathname = usePathname();

  return (
    <main className="flex h-full min-h-full flex-col bg-[var(--fundo-canvas)]">
      <header className="border-b border-border bg-card px-5 py-4 shadow-sm sm:px-7">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <ShieldCheck className="size-6 text-cor-ia" aria-hidden />
              <h1 className="text-2xl font-bold tracking-tight text-foreground">{textos.titulo}</h1>
              <Badge variant="secondary" className="bg-cor-ia/10 text-cor-ia uppercase">
                {textos.restrito}
              </Badge>
            </div>
            <p className="mt-1 text-[13px] text-muted-foreground">{textos.descricao}</p>
          </div>
          <Badge variant="outline" className="gap-2">
            <span className="size-2 rounded-full bg-muted-foreground" aria-hidden />
            {textos.estadoSistema}
          </Badge>
        </div>
      </header>

      <div className="flex min-h-0 flex-col sm:flex-1 sm:flex-row">
        <nav
          aria-label={textos.navegacao}
          className="shrink-0 border-b border-border bg-card px-2 sm:w-64 sm:self-stretch sm:border-r sm:border-b-0 sm:px-3 sm:py-5"
        >
          <ul className="flex gap-1 overflow-x-auto sm:flex-col sm:overflow-visible">
            {ABAS.map((aba) => {
              const ativa = aba.exata ? pathname === aba.rota : pathname.startsWith(aba.rota);
              const Icone = aba.icone;
              return (
                <li key={aba.chave} className="shrink-0">
                  <Link
                    href={aba.rota}
                    aria-current={ativa ? "page" : undefined}
                    className={cn(
                      "flex items-center gap-1.5 border-b-2 px-2 py-3 text-xs font-semibold whitespace-nowrap sm:gap-3 sm:rounded-xl sm:border sm:px-3 sm:text-sm",
                      ativa
                        ? "border-cor-ia text-cor-ia sm:border-cor-ia/20 sm:bg-cor-ia/10"
                        : "border-transparent text-muted-foreground hover:text-foreground sm:hover:bg-muted/50",
                    )}
                  >
                    <Icone className="size-4 shrink-0" aria-hidden />
                    {textos.abas[aba.chave]}
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>

        <div className="min-w-0 flex-1 p-5 sm:p-7">{children}</div>
      </div>
    </main>
  );
}
