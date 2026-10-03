"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ArrowLeft, ArrowRight, CircleCheck, Save } from "lucide-react";

import { Button, buttonVariants } from "@/components/ui/button";
import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";
import { iniciarCampanha } from "@/lib/campanhas/api";
import {
  filtroSemVazios,
  pedidoDeCampanha,
  type EstadoDoAssistente,
} from "@/lib/campanhas/estado-do-assistente";
import {
  usePodeEmCampanhas,
  usePreviaDoPublico,
  useSalvarRascunho,
  useValorComAtraso,
} from "@/lib/campanhas/hooks";
import { PASSOS, passoValido } from "@/lib/campanhas/passos";
import { useTextos } from "@/lib/config/textos-provider";

import { IndicadorDePassos } from "./indicador-de-passos";
import { PassoPublico } from "./passo-publico";
import { PassoRevisao } from "./passo-revisao";
import { PassoRitmo } from "./passo-ritmo";
import { PassoTemplate } from "./passo-template";

interface Props {
  /** Estado de partida: vazio (nova campanha) ou o rascunho reaberto. */
  inicial: EstadoDoAssistente;
  tetoDaInstancia: number;
  limiteMeta: number;
  passoInicial?: number;
}

function mensagemDe(erro: unknown, padrao: string): string {
  return erro instanceof Error && erro.message && !erro.message.startsWith("Erro ") ? erro.message : padrao;
}

export function AssistenteDeCampanha({ inicial, tetoDaInstancia, limiteMeta, passoInicial = 0 }: Props) {
  const textos = useTextos().campanhas;
  const t = textos.assistente;
  const roteador = useRouter();
  const podeOperar = usePodeEmCampanhas("operar");
  const salvar = useSalvarRascunho();
  const [estado, setEstado] = useState<EstadoDoAssistente>(inicial);
  const [passo, setPasso] = useState(passoInicial);
  const [maiorPasso, setMaiorPasso] = useState(passoInicial);
  const [tentouAvancar, setTentouAvancar] = useState(false);
  const [salvo, setSalvo] = useState(inicial.rascunhoId !== null);
  const [iniciando, setIniciando] = useState(false);
  const [erroAoIniciar, setErroAoIniciar] = useState<string | null>(null);
  const previa = usePreviaDoPublico(filtroSemVazios(useValorComAtraso(estado.filtro)));

  const mudar = (mudanca: Partial<EstadoDoAssistente>) => {
    setSalvo(false);
    setEstado((atual) => ({ ...atual, ...mudanca }));
  };
  const contexto = { tetoDaInstancia, elegiveis: previa.data?.elegiveis ?? null, agora: new Date() };
  const valido = passoValido(PASSOS[passo], estado, contexto);

  /** Salva (ou cria) o rascunho no servidor e guarda o id para os próximos passos. */
  async function salvarRascunho(): Promise<string | null> {
    const pedido = pedidoDeCampanha(estado);
    if (!pedido) return null;
    const campanha = await salvar.mutateAsync({ id: estado.rascunhoId, pedido });
    setEstado((atual) => ({ ...atual, rascunhoId: campanha.id }));
    setSalvo(true);
    return campanha.id;
  }

  async function avancar() {
    setTentouAvancar(true);
    if (!valido) return;
    try {
      await salvarRascunho();
    } catch {
      return;
    }
    setTentouAvancar(false);
    setPasso(passo + 1);
    setMaiorPasso(Math.max(maiorPasso, passo + 1));
  }

  async function iniciar() {
    setErroAoIniciar(null);
    setIniciando(true);
    try {
      const id = await salvarRascunho();
      if (!id) throw new Error(textos.passoRevisao.erroIniciar);
      await iniciarCampanha(id);
      roteador.push(`/campanhas/${id}`);
    } catch (erro) {
      setErroAoIniciar(mensagemDe(erro, textos.passoRevisao.erroIniciar));
      setIniciando(false);
    }
  }

  return (
    <main className="space-y-6 p-6">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <Link href="/campanhas" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:underline">
            <ArrowLeft className="size-4" aria-hidden />
            {textos.voltar}
          </Link>
          <h1 className="text-2xl font-bold">{estado.rascunhoId ? t.tituloEdicao : t.titulo}</h1>
          <p className="text-sm text-muted-foreground">
            {interpolarCatalogo(t.passoDe, { passo: String(passo + 1), total: String(PASSOS.length) })}
          </p>
        </div>
        <p role="status" className="flex items-center gap-1.5 text-sm text-muted-foreground">
          {salvar.isPending && t.salvando}
          {!salvar.isPending && salvo && (
            <>
              <CircleCheck className="size-4 text-cor-sucesso" aria-hidden />
              {t.rascunhoSalvo}
            </>
          )}
        </p>
      </header>
      <IndicadorDePassos passo={passo} concluidos={maiorPasso} />
      <section>
        {PASSOS[passo] === "template" && <PassoTemplate estado={estado} aoMudar={mudar} mostrarErros={tentouAvancar} />}
        {PASSOS[passo] === "publico" && <PassoPublico estado={estado} aoMudar={mudar} />}
        {PASSOS[passo] === "ritmo" && (
          <PassoRitmo estado={estado} teto={tetoDaInstancia} limiteMeta={limiteMeta} aoMudar={mudar} mostrarErros={tentouAvancar} />
        )}
        {PASSOS[passo] === "revisao" && (
          <PassoRevisao
            estado={estado}
            ehAdministrador={podeOperar}
            iniciando={iniciando}
            erroAoIniciar={erroAoIniciar}
            aoMudar={mudar}
            aoIniciar={() => void iniciar()}
          />
        )}
      </section>
      {salvar.isError && (
        <p role="alert" className="text-sm text-destructive">
          {mensagemDe(salvar.error, t.erroSalvar)}
        </p>
      )}
      <footer className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-4">
        <Link href="/campanhas" className={buttonVariants({ variant: "ghost" })}>
          {t.sair}
        </Link>
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="outline" disabled={passo === 0} onClick={() => setPasso(passo - 1)}>
            <ArrowLeft aria-hidden />
            {t.voltar}
          </Button>
          {estado.template && estado.nome.trim() && (
            <Button type="button" variant="outline" disabled={salvar.isPending} onClick={() => void salvarRascunho().catch(() => undefined)}>
              <Save aria-hidden />
              {t.salvarRascunho}
            </Button>
          )}
          {passo < PASSOS.length - 1 && (
            <Button type="button" disabled={salvar.isPending} aria-disabled={!valido} onClick={() => void avancar()}>
              {t.avancar}
              <ArrowRight aria-hidden />
            </Button>
          )}
        </div>
      </footer>
    </main>
  );
}
