"use client";

import Link from "next/link";
import { Megaphone, ShieldX, PlugZap } from "lucide-react";
import type { ReactNode } from "react";

import { Button, buttonVariants } from "@/components/ui/button";
import { useTextos } from "@/lib/config/textos-provider";

interface PropsDoVazio {
  titulo: string;
  descricao: string;
  acao?: ReactNode;
  icone?: ReactNode;
}

/** Estado vazio com ícone, explicação e o próximo passo (CTA), nunca uma tabela em branco. */
export function EstadoVazio({ titulo, descricao, acao, icone }: PropsDoVazio) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-border bg-card px-6 py-12 text-center">
      <span className="flex size-12 items-center justify-center rounded-full bg-accent text-accent-foreground">
        {icone ?? <Megaphone className="size-6" aria-hidden />}
      </span>
      <div className="space-y-1">
        <h2 className="text-base font-bold">{titulo}</h2>
        <p className="mx-auto max-w-md text-sm text-muted-foreground">{descricao}</p>
      </div>
      {acao}
    </div>
  );
}

export function CampanhasIndisponiveis() {
  const textos = useTextos().campanhas;
  return (
    <main className="p-6">
      <EstadoVazio
        icone={<PlugZap className="size-6" aria-hidden />}
        titulo={textos.indisponivelTitulo}
        descricao={textos.indisponivelDescricao}
        acao={
          <Link href="/atendimentos" className={buttonVariants({ variant: "outline" })}>
            {textos.voltar}
          </Link>
        }
      />
    </main>
  );
}

export function SemAcessoACampanhas() {
  const textos = useTextos().campanhas;
  return (
    <main className="flex min-h-[60vh] items-center justify-center p-6">
      <div className="max-w-md space-y-4 rounded-xl border bg-card p-8 text-center shadow-sm">
        <ShieldX className="mx-auto size-10 text-destructive" aria-hidden />
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

/** Botão desabilitado + a razão visível ao lado (um botão desabilitado não recebe foco nem tooltip). */
export function AcaoSoDoAdministrador({
  rotulo,
  explicacao,
  id,
}: {
  rotulo: string;
  explicacao: string;
  id: string;
}) {
  return (
    <div className="flex flex-col gap-1">
      <Button type="button" disabled aria-describedby={id} variant="outline">
        {rotulo}
      </Button>
      <p id={id} className="max-w-56 text-xs text-muted-foreground">
        {explicacao}
      </p>
    </div>
  );
}

/** Criar e salvar rascunho são do administrador (o backend recusa os demais com 403). */
export function SoAdministradorCria() {
  const textos = useTextos().campanhas;
  return (
    <main className="p-6">
      <EstadoVazio
        icone={<ShieldX className="size-6" aria-hidden />}
        titulo={textos.somenteAdministrador}
        descricao={textos.criarSomenteAdministrador}
        acao={
          <Link href="/campanhas" className={buttonVariants({ variant: "outline" })}>
            {textos.voltar}
          </Link>
        }
      />
    </main>
  );
}
