"use client";

import { useMemo, useState } from "react";
import {
  BadgeCheck,
  Bot,
  CalendarRange,
  Clock3,
  Handshake,
  TrendingDown,
  TrendingUp,
  UsersRound,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { Seletor } from "@/components/ui/seletor";
import { SeletorData } from "@/components/ui/seletor-data";
import { useTextos } from "@/lib/config/textos-provider";
import { useVisaoGeralDashboard } from "@/lib/dashboard/use-dashboard";
import type { Comparativo, VisaoGeralDashboard } from "@/lib/dashboard/types";
import { cn, iniciaisDoNome } from "@/lib/utils";

const ANOS_DISPONIVEIS = 7;
const HORAS_DO_DIA = Array.from({ length: 24 }, (_, hora) => hora);

function preencher(modelo: string, valores: Record<string, string | number>): string {
  return Object.entries(valores).reduce(
    (texto, [chave, valor]) => texto.replaceAll(`{${chave}}`, String(valor)),
    modelo,
  );
}

function numero(valor: number): string {
  return new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 }).format(valor);
}

function percentual(valor: number): string {
  return new Intl.NumberFormat("pt-BR", {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  }).format(valor);
}

export function PaginaDashboard() {
  const textos = useTextos().dashboard;
  const anoAtual = new Date().getFullYear();
  const [ano, setAno] = useState(anoAtual);
  const [meses, setMeses] = useState(() => textos.meses.map((_, indice) => indice + 1));
  const [origemInicio, setOrigemInicio] = useState("");
  const [origemFim, setOrigemFim] = useState("");

  const filtro = useMemo(
    () => ({ ano, meses: [...meses].sort((a, b) => a - b), origemInicio, origemFim }),
    [ano, meses, origemInicio, origemFim],
  );
  const consulta = useVisaoGeralDashboard(filtro);
  const opcoesDeAno = Array.from({ length: ANOS_DISPONIVEIS }, (_, indice) => {
    const valor = String(anoAtual - 5 + indice);
    return { valor, rotulo: valor };
  });

  function alternarMes(mes: number) {
    setMeses((atuais) =>
      atuais.includes(mes) ? atuais.filter((item) => item !== mes) : [...atuais, mes],
    );
  }

  return (
    <div
      data-testid="dashboard-conteudo"
      className="flex min-h-full flex-col gap-6 bg-background p-6 lg:p-8"
    >
      <header>
        <h1 className="text-2xl font-bold tracking-tight text-foreground">{textos.titulo}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{textos.descricao}</p>
      </header>

      <nav className="flex flex-wrap gap-2 border-b pb-3" aria-label={textos.abas.rotulo}>
        <Button variant="default" size="sm" aria-current="page">
          {textos.abas.visaoGeral}
        </Button>
        {[textos.abas.operacional, textos.abas.comercial, textos.abas.iaAutomacao].map((aba) => (
          <Button key={aba} variant="ghost" size="sm" disabled>
            {aba} · {textos.abas.depois}
          </Button>
        ))}
      </nav>

      <section className="rounded-xl border bg-card/75 p-4" aria-label={textos.filtros.rotulo}>
        <div className="flex flex-wrap items-end gap-3">
          <div className="w-28">
            <label className="mb-1.5 block text-xs font-semibold text-muted-foreground" htmlFor="dashboard-ano">
              {textos.filtros.ano}
            </label>
            <Seletor
              id="dashboard-ano"
              valor={String(ano)}
              opcoes={opcoesDeAno}
              onChange={(valor) => setAno(Number(valor))}
              placeholder={textos.filtros.ano}
            />
          </div>
          <div className="min-w-0 flex-1">
            <span className="mb-1.5 block text-xs font-semibold text-muted-foreground">
              {textos.filtros.meses}
            </span>
            <div className="flex flex-wrap gap-1.5">
              {textos.meses.map((mes, indice) => {
                const valor = indice + 1;
                const ativo = meses.includes(valor);
                return (
                  <Button
                    key={mes}
                    type="button"
                    size="sm"
                    variant={ativo ? "default" : "outline"}
                    aria-pressed={ativo}
                    onClick={() => alternarMes(valor)}
                    className="min-w-11"
                  >
                    {mes}
                  </Button>
                );
              })}
            </div>
          </div>
          <div className="w-[250px] shrink-0">
            <span className="mb-1.5 block text-xs font-semibold text-muted-foreground">
              {textos.filtros.originacao}
            </span>
            <div className="flex items-center gap-2">
              <SeletorData
                valor={origemInicio}
                onChange={setOrigemInicio}
                placeholder={textos.filtros.de}
                className="w-[110px]"
              />
              <SeletorData
                valor={origemFim}
                onChange={setOrigemFim}
                placeholder={textos.filtros.ate}
                className="w-[110px]"
              />
              {(origemInicio || origemFim) && (
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    setOrigemInicio("");
                    setOrigemFim("");
                  }}
                >
                  {textos.filtros.limpar}
                </Button>
              )}
            </div>
          </div>
        </div>
        {meses.length === 0 && (
          <p role="alert" className="mt-3 text-sm text-destructive">
            {textos.filtros.selecioneMes}
          </p>
        )}
        {Boolean(origemInicio) !== Boolean(origemFim) && (
          <p role="alert" className="mt-3 text-sm text-destructive">
            {textos.filtros.origemCompleta}
          </p>
        )}
      </section>

      {consulta.isLoading && <p className="text-sm text-muted-foreground">{textos.carregando}</p>}
      {consulta.isError && (
        <ErroDeCarregamento
          mensagem={textos.erro}
          onTentarNovamente={() => consulta.refetch()}
        />
      )}
      {consulta.data && <ConteudoDashboard dados={consulta.data} />}
    </div>
  );
}

function ConteudoDashboard({ dados }: { dados: VisaoGeralDashboard }) {
  const textos = useTextos().dashboard;
  const duracao = dados.tempoMedioAtendimento.segundos;
  const valorDuracao = duracao === null ? textos.semDado : formatarDuracao(duracao, textos.tempo);

  return (
    <>
      <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-6" aria-label={textos.kpis.rotulo}>
        <Kpi
          titulo={textos.kpis.atendimentos}
          valor={numero(dados.atendimentos.noPeriodo)}
          apoio={preencher(textos.kpis.atendimentosApoio, { total: numero(dados.atendimentos.acumulado) })}
          comparativo={dados.atendimentos.comparativo}
          Icone={UsersRound}
        />
        <Kpi
          titulo={textos.kpis.conversao}
          valor={dados.taxaConversao.percentual === null ? textos.semDado : `${percentual(dados.taxaConversao.percentual)}%`}
          apoio={preencher(textos.kpis.conversaoApoio, {
            vendas: dados.taxaConversao.vendas,
            leads: dados.taxaConversao.leadsRecebidos,
          })}
          comparativo={dados.taxaConversao.comparativo}
          Icone={Handshake}
        />
        <Kpi
          titulo={textos.kpis.tempoMedio}
          valor={valorDuracao}
          apoio={textos.kpis.tempoMedioApoio}
          comparativo={dados.tempoMedioAtendimento.comparativo}
          Icone={Clock3}
          quedaPositiva
        />
        <Kpi
          titulo={textos.kpis.vendas}
          valor={numero(dados.vendasFechadas.noPeriodo)}
          apoio={preencher(textos.kpis.vendasApoio, { total: numero(dados.vendasFechadas.acumulado) })}
          comparativo={dados.vendasFechadas.comparativo}
          Icone={BadgeCheck}
        />
        <Kpi
          titulo={textos.kpis.csat}
          valor={dados.avaliacaoMedia.media === null ? textos.semDado : `${percentual(dados.avaliacaoMedia.media)}/${dados.avaliacaoMedia.escalaMaxima}`}
          apoio={preencher(textos.kpis.csatApoio, { total: dados.avaliacaoMedia.quantidade })}
          comparativo={dados.avaliacaoMedia.comparativo}
          Icone={CalendarRange}
        />
        <Kpi
          titulo={textos.kpis.resolucaoIa}
          valor={
            dados.resolucaoPorIa.percentual === null
              ? textos.semDado
              : `${percentual(dados.resolucaoPorIa.percentual)}%`
          }
          apoio={textos.kpis.resolucaoIaApoio}
          comparativo={dados.resolucaoPorIa.comparativo}
          Icone={Bot}
        />
      </section>

      <section className="grid gap-4 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
        <Ranking dados={dados} />
        <Funil dados={dados} />
      </section>
      <HorarioDePico dados={dados} />
    </>
  );
}

interface KpiProps {
  titulo: string;
  valor: string;
  apoio: string;
  comparativo: Comparativo | null;
  Icone: React.ComponentType<{ className?: string }>;
  quedaPositiva?: boolean;
}

function Kpi({ titulo, valor, apoio, comparativo, Icone, quedaPositiva = false }: KpiProps) {
  const textos = useTextos().dashboard;
  const subiu = (comparativo?.valor ?? 0) >= 0;
  const positivo = quedaPositiva ? !subiu : subiu;
  const IconeTendencia = subiu ? TrendingUp : TrendingDown;

  return (
    <Card>
      <CardHeader className="grid grid-cols-[1fr_auto] items-start">
        <CardTitle className="text-sm text-muted-foreground">{titulo}</CardTitle>
        <span className="rounded-lg bg-primary/10 p-2 text-primary"><Icone className="size-4" /></span>
      </CardHeader>
      <CardContent>
        <p className="text-2xl font-bold tracking-tight" data-testid={`kpi-${titulo}`}>{valor}</p>
        <p className="mt-1 text-xs text-muted-foreground">{apoio}</p>
        {comparativo && (
          <p className={cn("mt-3 flex items-center gap-1 text-xs font-semibold", positivo ? "text-[var(--cor-sucesso)]" : "text-destructive")}>
            <IconeTendencia className="size-3.5" />
            {formatarComparativo(comparativo)} {textos.kpis.periodoAnterior}
          </p>
        )}
      </CardContent>
    </Card>
  );
}

function Ranking({ dados }: { dados: VisaoGeralDashboard }) {
  const textos = useTextos().dashboard;
  return (
    <Card>
      <CardHeader><CardTitle>{textos.secoes.ranking}</CardTitle></CardHeader>
      <CardContent className="space-y-3">
        {dados.rankingDeVendas.atendentes.length === 0 && <p className="text-sm text-muted-foreground">{textos.ranking.vazio}</p>}
        {dados.rankingDeVendas.atendentes.map((atendente, indice) => (
          <div key={atendente.id} className="flex items-center gap-3 rounded-lg bg-muted/45 px-3 py-2.5">
            <span className="w-5 text-center text-sm font-bold text-muted-foreground">{indice + 1}</span>
            <span className="flex size-9 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">
              {iniciaisDoNome(atendente.nome)}
            </span>
            <span className="min-w-0 flex-1 truncate font-medium">{atendente.nome}</span>
            <span className="font-bold">{atendente.vendas}</span>
          </div>
        ))}
        {dados.rankingDeVendas.semResponsavel > 0 && (
          <p className="border-t pt-3 text-xs text-muted-foreground">
            {preencher(
              dados.rankingDeVendas.semResponsavel === 1
                ? textos.ranking.semResponsavelSingular
                : textos.ranking.semResponsavelPlural,
              { total: dados.rankingDeVendas.semResponsavel },
            )}
          </p>
        )}
      </CardContent>
    </Card>
  );
}

function Funil({ dados }: { dados: VisaoGeralDashboard }) {
  const textos = useTextos().dashboard;
  const maximo = Math.max(...dados.funil.map((etapa) => etapa.quantidade), 1);
  return (
    <Card>
      <CardHeader><CardTitle>{textos.secoes.funil}</CardTitle></CardHeader>
      <CardContent className="space-y-4">
        {dados.funil.length === 0 && <p className="text-sm text-muted-foreground">{textos.funil.vazio}</p>}
        {dados.funil.map((etapa) => (
          <div key={etapa.id}>
            <div className="mb-1.5 flex items-center justify-between gap-3 text-sm">
              <span className="truncate font-medium">{etapa.nome}</span>
              <span className="shrink-0 text-muted-foreground">
                {etapa.quantidade}
                {etapa.percentualDePassagem !== null && ` · ${percentual(etapa.percentualDePassagem)}%`}
              </span>
            </div>
            <div className="h-2.5 overflow-hidden rounded-full bg-muted">
              <div
                className="h-full min-w-1 rounded-full bg-primary"
                style={{ width: `${(etapa.quantidade / maximo) * 100}%` }}
                data-testid="barra-funil"
              />
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

function HorarioDePico({ dados }: { dados: VisaoGeralDashboard }) {
  const textos = useTextos().dashboard;
  const porHora = new Map(dados.horarioDePico.map((item) => [item.hora, item.quantidade]));
  const maximo = Math.max(...dados.horarioDePico.map((item) => item.quantidade), 1);
  return (
    <Card>
      <CardHeader><CardTitle>{textos.secoes.horarioPico}</CardTitle></CardHeader>
      <CardContent>
        {dados.horarioDePico.length === 0 ? (
          <p className="text-sm text-muted-foreground">{textos.horario.vazio}</p>
        ) : (
          <div className="overflow-x-auto pb-1">
            <div className="flex h-52 min-w-[760px] items-end gap-2 border-b px-1">
              {HORAS_DO_DIA.map((hora) => {
                const quantidade = porHora.get(hora) ?? 0;
                return (
                  <div key={hora} className="flex h-full flex-1 flex-col justify-end gap-1 text-center">
                    <span className="text-[10px] text-muted-foreground">{quantidade || ""}</span>
                    <div
                      className="min-h-px w-full rounded-t bg-primary/80"
                      style={{ height: `${(quantidade / maximo) * 82}%` }}
                      data-testid="barra-horario"
                    />
                    <span className="pb-1 text-[10px] text-muted-foreground">{preencher(textos.horario.hora, { hora })}</span>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function formatarComparativo(comparativo: Comparativo): string {
  const sinal = comparativo.valor > 0 ? "+" : "";
  const sufixo = comparativo.unidade === "PONTOS_PERCENTUAIS" ? "pp" : comparativo.unidade === "PERCENTUAL" ? "%" : "";
  return `${sinal}${percentual(comparativo.valor)}${sufixo}`;
}

function formatarDuracao(segundos: number, textos: { minutos: string; horasMinutos: string }): string {
  const minutos = Math.round(segundos / 60);
  if (minutos < 60) return preencher(textos.minutos, { minutos });
  return preencher(textos.horasMinutos, { horas: Math.floor(minutos / 60), minutos: minutos % 60 });
}
