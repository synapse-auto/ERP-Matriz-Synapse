"use client";

import { useState } from "react";
import type { ReactNode } from "react";

import { Database, Link2, MessageSquareText, UserRoundCheck } from "lucide-react";

import { Button } from "@/components/ui/button";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useTextos } from "@/lib/config/textos-provider";
import {
  useAtualizarParametroAutomacao,
  useConfiguracaoAutomacao,
  useTelemetriaAutomacao,
} from "@/lib/automacao/use-automacao";
import type { ParametroAutomacao, StatusAutomacaoTelemetria } from "@/lib/automacao/types";

export function PaginaAutomacao() {
  const t = useTextos().automacao;
  const parametros = useConfiguracaoAutomacao();
  const telemetria = useTelemetriaAutomacao();

  return (
    <div className="space-y-5 p-6">
      <header>
        <h1 className="text-xl font-semibold">{t.titulo}</h1>
        <p className="text-sm text-muted-foreground">{t.descricao}</p>
      </header>

      <CardsDeTelemetria
        dados={telemetria.data}
        carregando={telemetria.isLoading}
        comErro={telemetria.isError}
        onTentarNovamente={() => telemetria.refetch()}
      />

      {parametros.isLoading ? (
        <p>{t.carregando}</p>
      ) : parametros.isError ? (
        <ErroDeCarregamento
          mensagem={t.erro}
          onTentarNovamente={() => parametros.refetch()}
        />
      ) : !parametros.data?.length ? (
        <p className="text-muted-foreground">{t.vazio}</p>
      ) : (
        <div className="space-y-3">
          {parametros.data.map((parametro) => (
            <LinhaParametro key={parametro.chave} parametro={parametro} />
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * Os quatro cards do topo (E17b §Bloco 5): mensagens enviadas, clientes transferidos, conexão da
 * Automação e status do CRM — snapshot de status_automacao_telemetria (Prompt A). Sem esqueleto
 * quando carregando ou com erro: a tela some os cards em vez de mostrar zero, que pareceria dado
 * real.
 */
function CardsDeTelemetria({
  dados,
  carregando,
  comErro,
  onTentarNovamente,
}: {
  dados: StatusAutomacaoTelemetria | undefined;
  carregando: boolean;
  comErro: boolean;
  onTentarNovamente: () => void | Promise<unknown>;
}) {
  const t = useTextos().automacao.telemetria;

  if (carregando) return null;
  if (comErro || !dados) {
    return <ErroDeCarregamento mensagem={t.erro} onTentarNovamente={onTentarNovamente} />;
  }

  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <CardDeTelemetria
        icone={<MessageSquareText className="size-5 text-primary" />}
        rotulo={t.mensagensEnviadas}
        valor={dados.mensagensEnviadas.toLocaleString("pt-BR")}
      />
      <CardDeTelemetria
        icone={<UserRoundCheck className="size-5 text-cor-ia" />}
        rotulo={t.clientesTransferidos}
        valor={dados.clientesTransferidos.toLocaleString("pt-BR")}
      />
      <CardDeTelemetria
        icone={<Link2 className="size-5 text-cor-sucesso" />}
        rotulo={t.conexaoAutomacao}
        status={dados.conexaoAutomacaoAtiva}
        rotuloAtivo={t.conectado}
        rotuloInativo={t.desconectado}
      />
      <CardDeTelemetria
        icone={<Database className="size-5 text-cor-sucesso" />}
        rotulo={t.statusDoCrm}
        status={dados.crmOnline}
        rotuloAtivo={t.online}
        rotuloInativo={t.offline}
      />
    </div>
  );
}

function CardDeTelemetria({
  icone,
  rotulo,
  valor,
  status,
  rotuloAtivo,
  rotuloInativo,
}: {
  icone: ReactNode;
  rotulo: string;
  valor?: string;
  status?: boolean;
  rotuloAtivo?: string;
  rotuloInativo?: string;
}) {
  return (
    <div className="flex items-center gap-3 rounded-lg border bg-card p-4">
      <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-muted">
        {icone}
      </div>
      <div className="min-w-0">
        <p className="text-xs font-medium text-muted-foreground">{rotulo}</p>
        {status != null ? (
          <p
            className={
              status
                ? "mt-0.5 flex items-center gap-1.5 text-sm font-bold text-cor-sucesso"
                : "mt-0.5 flex items-center gap-1.5 text-sm font-bold text-cor-erro"
            }
          >
            <span
              className={status ? "size-2 rounded-full bg-cor-sucesso" : "size-2 rounded-full bg-cor-erro"}
            />
            {status ? rotuloAtivo : rotuloInativo}
          </p>
        ) : (
          <p className="text-lg font-bold">{valor}</p>
        )}
      </div>
    </div>
  );
}

function LinhaParametro({ parametro }: { parametro: ParametroAutomacao }) {
  const t = useTextos().automacao;
  const atualizar = useAtualizarParametroAutomacao();
  const [valor, setValor] = useState(parametro.valor);

  const alterado = valor !== parametro.valor;
  const foraDaFaixa = valorForaDaFaixa(parametro, valor);
  const podeSalvar = alterado && !foraDaFaixa && !atualizar.isPending;
  const temFaixa = parametro.valorMin != null || parametro.valorMax != null;

  function salvar() {
    atualizar.mutate({ chave: parametro.chave, valor });
  }

  return (
    <div className="rounded-lg border p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-medium">{parametro.descricao ?? parametro.chave}</p>
          <p className="font-mono text-xs text-muted-foreground">{parametro.chave}</p>
        </div>
        {parametro.tipo !== "BOOLEAN" && temFaixa && (
          <p className="shrink-0 text-xs text-muted-foreground">
            {t.faixaLabel}: {parametro.valorMin ?? "—"}–{parametro.valorMax ?? "—"}
            {parametro.unidade ? ` ${parametro.unidade}` : ""}
          </p>
        )}
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-3">
        <CampoValor parametro={parametro} valor={valor} onChange={setValor} />
        {foraDaFaixa && (
          <p role="alert" className="text-xs text-destructive">
            {t.erroFaixa}
          </p>
        )}
        {atualizar.isError && (
          <p role="alert" className="text-xs text-destructive">
            {t.erroSalvar}
          </p>
        )}
        <Button size="sm" disabled={!podeSalvar} onClick={salvar} className="ml-auto">
          {atualizar.isPending ? t.salvando : t.salvar}
        </Button>
      </div>
    </div>
  );
}

function CampoValor({
  parametro,
  valor,
  onChange,
}: {
  parametro: ParametroAutomacao;
  valor: string;
  onChange: (valor: string) => void;
}) {
  const t = useTextos().automacao;

  if (parametro.tipo === "BOOLEAN") {
    return (
      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={valor === "true"}
          onChange={(e) => onChange(e.target.checked ? "true" : "false")}
        />
        {valor === "true" ? t.ativado : t.desativado}
      </label>
    );
  }

  if (parametro.tipo === "TEXT") {
    return (
      <Textarea
        value={valor}
        onChange={(e) => onChange(e.target.value)}
        rows={2}
        className="min-w-64 flex-1"
      />
    );
  }

  return (
    <Input
      type="number"
      inputMode="decimal"
      step={parametro.tipo === "DECIMAL" ? "any" : "1"}
      min={parametro.valorMin ?? undefined}
      max={parametro.valorMax ?? undefined}
      value={valor}
      onChange={(e) => onChange(e.target.value)}
      className="w-32"
    />
  );
}

function valorForaDaFaixa(parametro: ParametroAutomacao, valor: string): boolean {
  if (parametro.tipo !== "INT" && parametro.tipo !== "DECIMAL") return false;
  const numero = Number(valor);
  if (Number.isNaN(numero)) return true;
  if (parametro.valorMin != null && numero < parametro.valorMin) return true;
  if (parametro.valorMax != null && numero > parametro.valorMax) return true;
  return false;
}
