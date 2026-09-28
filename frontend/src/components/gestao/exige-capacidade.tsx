"use client";

import Link from "next/link";
import { ShieldX } from "lucide-react";

import { buttonVariants } from "@/components/ui/button";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { useTextos } from "@/lib/config/textos-provider";
import { useCapacidades } from "@/lib/gestao/use-capacidades";

/**
 * Guarda de rota por capacidade de leitura (Gestão, docs/47). Cobre a URL digitada à mão e a
 * revogação com a tela aberta: a página some sem F5 e no lugar fica um estado recuperável. Enquanto
 * a permissão não é conhecida, nada da página é desenhado — nenhum controle aparece e some depois.
 */
export function ExigeCapacidade({ capacidade, children }: { capacidade: string; children: React.ReactNode }) {
  const textos = useTextos().gestao.acesso;
  const capacidades = useCapacidades();

  if (capacidades.estado === "carregando") {
    return (
      <p className="p-6 text-sm text-muted-foreground" role="status">
        {textos.verificando}
      </p>
    );
  }
  if (capacidades.estado === "erro") {
    return (
      <div className="p-6">
        <ErroDeCarregamento mensagem={textos.erro} onTentarNovamente={capacidades.recarregar} />
      </div>
    );
  }
  if (!capacidades.pode(capacidade)) {
    return (
      <main className="flex min-h-[60vh] items-center justify-center p-6">
        <div className="max-w-md space-y-4 rounded-xl border bg-card p-8 text-center shadow-sm">
          <ShieldX className="mx-auto size-[calc(var(--tamanho-icone-interface)*2.5)] text-destructive" aria-hidden />
          <div>
            <h1 className="text-xl font-bold">{textos.semAcessoTitulo}</h1>
            <p className="mt-1 text-sm text-muted-foreground">{textos.semAcessoDescricao}</p>
          </div>
          <Link href="/atendimentos" className={buttonVariants()}>
            {textos.voltar}
          </Link>
        </div>
      </main>
    );
  }
  return children;
}
