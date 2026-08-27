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
    <main className="min-h-full bg-muted/40">
      <header className="border-b bg-card px-6 py-5">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <ShieldCheck className="size-6 text-primary" aria-hidden />
              <h1 className="text-2xl font-bold text-foreground">{textos.titulo}</h1>
              <Badge variant="secondary" className="uppercase">
                {textos.restrito}
              </Badge>
            </div>
            <p className="mt-1 text-sm text-muted-foreground">{textos.descricao}</p>
          </div>
          <Badge variant="outline" className="gap-2">
            <span className="size-2 rounded-full bg-muted-foreground" aria-hidden />
            {textos.estadoSistema}
          </Badge>
        </div>
      </header>

      <nav aria-label={textos.navegacao} className="border-b bg-card px-6">
        <ul className="flex gap-1 overflow-x-auto">
          {ABAS.map((aba) => {
            const ativa = aba.exata ? pathname === aba.rota : pathname.startsWith(aba.rota);
            const Icone = aba.icone;
            return (
              <li key={aba.chave}>
                <Link
                  href={aba.rota}
                  aria-current={ativa ? "page" : undefined}
                  className={cn(
                    "flex items-center gap-2 border-b-2 px-4 py-3 text-sm font-semibold whitespace-nowrap",
                    ativa
                      ? "border-primary text-primary"
                      : "border-transparent text-muted-foreground hover:text-foreground",
                  )}
                >
                  <Icone className="size-4" aria-hidden />
                  {textos.abas[aba.chave]}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>

      <div className="p-6">{children}</div>
    </main>
  );
}
