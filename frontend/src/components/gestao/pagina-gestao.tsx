"use client";

import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ShieldCheck, UserPlus, UserRoundCog, Users } from "lucide-react";

import { Button } from "@/components/ui/button";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useTextos } from "@/lib/config/textos-provider";
import { visivelNaEquipe } from "@/lib/equipe/papel";
import { useEquipe } from "@/lib/equipe/use-equipe";
import type { MinhasPermissoes, Papel } from "@/lib/gestao/types";
import { useMinhasPermissoes, usePerfis, usePermissoesDaEquipe } from "@/lib/gestao/use-gestao";

import { AbaEquipe } from "./aba-equipe";
import { AbaExcecoes } from "./aba-excecoes";
import { AbaPermissoes } from "./aba-permissoes";
import { pode, useConfirmacaoDeDescarte, type TextosGestao } from "./apoio";

type Aba = "equipe" | "permissoes" | "excecoes";
const ABAS: readonly Aba[] = ["equipe", "permissoes", "excecoes"];

function normalizarAba(valor: string | null): Aba {
  return ABAS.includes(valor as Aba) ? (valor as Aba) : "equipe";
}

function selo(textos: TextosGestao, minhas: MinhasPermissoes): string {
  if (minhas.papel === "ADMINISTRADOR") return textos.selo.administrador;
  if (minhas.papel === "GESTOR") return textos.selo.gestao;
  const delegado = minhas.editaPerfis || minhas.editaExcecoes
    || ["equipe.criar", "equipe.editar", "equipe.desativar", "equipe.senha_provisoria"].some((c) => pode(minhas, c));
  return delegado ? textos.selo.delegado : textos.selo.leitura;
}

/**
 * Gestão: Equipe, Permissões e Exceções por usuário (docs/47). O acesso vem do backend
 * (`acessaGestao`); a tela só decide o que desenhar — cada ação é revalidada no servidor.
 */
export function PaginaGestao() {
  const textos = useTextos().gestao;
  const minhas = useMinhasPermissoes();

  if (minhas.isLoading) return <p className="p-6 text-sm text-muted-foreground">{textos.carregando}</p>;
  if (minhas.isError || !minhas.data) {
    return (
      <div className="p-6">
        <ErroDeCarregamento mensagem={textos.erro} onTentarNovamente={() => void minhas.refetch()} />
      </div>
    );
  }
  if (!minhas.data.acessaGestao) {
    return (
      <section className="m-6 rounded-xl border border-destructive/30 bg-destructive/5 p-6" role="alert">
        <h1 className="text-lg font-semibold">{textos.titulo}</h1>
        <p className="mt-2 text-sm text-destructive">{textos.semAcesso}</p>
      </section>
    );
  }
  return <Conteudo textos={textos} minhas={minhas.data} />;
}

function Conteudo({ textos, minhas }: { textos: TextosGestao; minhas: MinhasPermissoes }) {
  const router = useRouter();
  const parametros = useSearchParams();
  const aba = normalizarAba(parametros.get("aba"));
  const perfilDaUrl = parametros.get("perfil") as Papel | null;
  const usuarioDaUrl = parametros.get("usuario");
  const [sujo, setSujo] = useState(false);
  const [novoAberto, setNovoAberto] = useState(false);
  const confirmacao = useConfirmacaoDeDescarte(sujo, textos);
  const equipe = useEquipe();
  const perfis = usePerfis();
  const permissoes = usePermissoesDaEquipe();

  function navegar(novaAba: Aba, extra: Record<string, string> = {}) {
    confirmacao.executar(() => {
      setSujo(false);
      const query = new URLSearchParams({ ...(novaAba === "equipe" ? {} : { aba: novaAba }), ...extra }).toString();
      router.replace(query ? `/gestao?${query}` : "/gestao", { scroll: false });
    });
  }

  const ativos = (equipe.data ?? []).filter((u) => u.ativo && visivelNaEquipe(u.papel)).length;
  const comExcecoes = (permissoes.data ?? []).filter((u) => u.excecoes > 0).length;
  const contadores: Record<Aba, number | undefined> = {
    equipe: equipe.data ? ativos : undefined,
    permissoes: perfis.data?.length,
    excecoes: permissoes.data ? comExcecoes : undefined,
  };
  const icones = { equipe: Users, permissoes: ShieldCheck, excecoes: UserRoundCog } as const;

  return (
    <div className="flex min-h-full flex-col">
      <header className="border-b border-border bg-card px-4 pt-5 sm:px-8">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <h1 className="flex flex-wrap items-center gap-3 text-xl font-bold text-foreground">
              {textos.titulo}
              <span className="inline-flex items-center gap-1 rounded-md bg-accent px-2 py-0.5 text-[10px] font-extrabold tracking-wide text-accent-foreground uppercase">
                <ShieldCheck className="size-3.5" aria-hidden />
                {selo(textos, minhas)}
              </span>
            </h1>
            <p className="mt-1 text-sm text-muted-foreground">{textos.descricoes[aba]}</p>
          </div>
          {aba === "equipe" && pode(minhas, "equipe.criar") && (
            <Button className="h-11 rounded-xl px-5 shadow-md" onClick={() => setNovoAberto(true)}>
              <UserPlus className="size-(--tamanho-icone-interface)" aria-hidden />
              {textos.equipe.novo}
            </Button>
          )}
        </div>
        <Tabs value={aba} onValueChange={(valor) => navegar(normalizarAba(String(valor)))} className="mt-4">
          <TabsList variant="line" aria-label={textos.abas.rotulo} className="h-auto max-w-full justify-start gap-4 overflow-x-auto p-0">
            {ABAS.map((a) => {
              const Icone = icones[a];
              return (
                <TabsTrigger key={a} value={a} className="h-11 flex-none px-3 data-active:text-primary after:bg-primary">
                  <Icone className="size-4" aria-hidden />
                  {textos.abas[a]}
                  {contadores[a] != null && (
                    <span className="rounded-md bg-muted px-1.5 py-px text-[10px] font-bold text-muted-foreground tabular-nums">
                      {contadores[a]}
                    </span>
                  )}
                </TabsTrigger>
              );
            })}
          </TabsList>
        </Tabs>
      </header>
      <main className="flex-1 px-4 py-6 sm:px-8">
        {aba === "equipe" && (
          <AbaEquipe
            textos={textos}
            minhas={minhas}
            novoAberto={novoAberto}
            onFecharNovo={() => setNovoAberto(false)}
            onAbrirPerfil={(papel) => navegar("permissoes", { perfil: papel })}
            onAbrirExcecoes={(id) => navegar("excecoes", { usuario: id })}
          />
        )}
        {aba === "permissoes" && (
          <AbaPermissoes
            key={perfilDaUrl ?? "padrao"}
            textos={textos}
            minhas={minhas}
            papelInicial={perfilDaUrl === "ATENDENTE" || perfilDaUrl === "SUBGESTOR" || perfilDaUrl === "GESTOR" ? perfilDaUrl : undefined}
            onSujoChange={setSujo}
          />
        )}
        {aba === "excecoes" && (
          <AbaExcecoes key={usuarioDaUrl ?? "padrao"} textos={textos} minhas={minhas} usuarioInicial={usuarioDaUrl ?? undefined} onSujoChange={setSujo} />
        )}
      </main>
      {confirmacao.dialogo}
    </div>
  );
}
