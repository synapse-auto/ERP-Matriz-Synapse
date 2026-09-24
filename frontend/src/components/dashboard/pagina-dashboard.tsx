"use client";

import { useEffect, useMemo, useState } from "react";
import { ptBR } from "date-fns/locale";
import {
  Bot,
  CalendarDays,
  ChartColumn,
  CircleDollarSign,
  Clock3,
  Handshake,
  LayoutGrid,
  ListFilter,
  Lock,
  Monitor,
  Star,
  UserPlus,
  UsersRound,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Seletor } from "@/components/ui/seletor";
import { useTextos } from "@/lib/config/textos-provider";
import {
  faixaDaHora,
  resumoDoHorario,
  type ResumoDoHorario,
} from "@/lib/dashboard/horario-de-pico";
import { useVisaoGeralDashboard } from "@/lib/dashboard/use-dashboard";
import type { Comparativo, VisaoGeralDashboard } from "@/lib/dashboard/types";
import { useTelaEstreita } from "@/lib/navegacao/tela-estreita";
import { cn, iniciaisDoNome } from "@/lib/utils";

import { GraficoKpi, type CampoSerie } from "./grafico-kpi";

const ANOS_DISPONIVEIS = 7;
const HORAS_DO_DIA = Array.from({ length: 24 }, (_, hora) => hora);
type PeriodoEnxuto = "hoje" | "seteDias" | "mes" | "ano";
type ModoDashboard = "compacta" | "expandida";

/*
 * Cor por métrica vem SEMPRE de token (tema.json → CSS custom property). Nada de hex aqui: trocar
 * o tema de um filho tem de repintar o dashboard sem tocar em componente. O valor entra como
 * `--tom` no próprio cartão, e as classes Tailwind que o consomem são estáticas — nome de classe
 * montado em runtime não sobrevive ao JIT.
 */
const TOM_ATENDIMENTOS = "var(--primary)";
const TOM_CONVERSAO = "var(--cor-sucesso)";
const TOM_TEMPO = "var(--cor-info)";
const TOM_AVALIACAO = "var(--cor-atencao)";
const TOM_IA = "var(--cor-ia)";
const TOM_NOVOS_LEADS = "var(--cor-destaque-2)";
const TOM_VENDAS = "var(--cor-destaque-3)";

/*
 * Ouro / prata / bronze do pódio. Não existe token de "prata" nem de "bronze" no tema; o mais
 * próximo honesto é --texto-fraco (cinza azulado) e --cor-atencao-escura (âmbar queimado), ambos
 * já no tema.json. Trocar por tokens dedicados é mudança de tema, não de componente.
 */
const MEDALHAS = ["var(--cor-atencao)", "var(--texto-fraco)", "var(--cor-atencao-escura)"];
/** Avatar com cor cheia, como no mockup; só tokens do tema, alternados pela posição. */
const CORES_DE_AVATAR = [
  "var(--primary)",
  "var(--cor-info)",
  "var(--cor-ia)",
  "var(--cor-destaque-2)",
  "var(--cor-destaque-3)",
];

/** Abaixo disto o número não cabe legível dentro da barra e vai para fora dela. */
const PERCENTUAL_MINIMO_PARA_NUMERO_DENTRO = 14;

function isoLocal(data: Date): string {
  const ano = data.getFullYear();
  const mes = String(data.getMonth() + 1).padStart(2, "0");
  const dia = String(data.getDate()).padStart(2, "0");
  return `${ano}-${mes}-${dia}`;
}

function paraDataLocal(valor: string): Date | undefined {
  const partes = valor.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!partes) return undefined;
  return new Date(Number(partes[1]), Number(partes[2]) - 1, Number(partes[3]));
}

function dataCurta(valor: string): string {
  const data = paraDataLocal(valor);
  return data ? new Intl.DateTimeFormat("pt-BR", { dateStyle: "short" }).format(data) : "";
}

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

/**
 * Fração 0–100 blindada contra o estado vazio real desta instância: o denominador chega zerado com
 * frequência, e `0/0` viraria `NaN` no `style.width` — largura inválida, que o navegador trata
 * como "auto" e faz a barra estourar o trilho em vez de sumir.
 */
function fracaoPercentual(parte: number, total: number): number {
  if (!Number.isFinite(parte) || !Number.isFinite(total) || total <= 0) return 0;
  return Math.min(100, Math.max(0, (parte / total) * 100));
}

export function PaginaDashboard() {
  const textos = useTextos().dashboard;
  const telaEstreita = useTelaEstreita();
  const anoAtual = new Date().getFullYear();
  const [ano, setAno] = useState(anoAtual);
  const [meses, setMeses] = useState(() => textos.meses.map((_, indice) => indice + 1));
  const [origemInicio, setOrigemInicio] = useState("");
  const [origemFim, setOrigemFim] = useState("");
  const [inicio, setInicio] = useState("");
  const [fim, setFim] = useState("");
  const [periodo, setPeriodo] = useState<PeriodoEnxuto>("mes");
  const [avisoComputadorAberto, setAvisoComputadorAberto] = useState(false);
  const [modo, setModo] = useState<ModoDashboard>("compacta");

  const filtro = useMemo(
    () => ({
      ano,
      meses: [...meses].sort((a, b) => a - b),
      origemInicio,
      origemFim,
      inicio,
      fim,
    }),
    [ano, meses, origemInicio, origemFim, inicio, fim],
  );
  const consulta = useVisaoGeralDashboard(filtro);
  const opcoesDeAno = Array.from({ length: ANOS_DISPONIVEIS }, (_, indice) => {
    const valor = String(anoAtual - 5 + indice);
    return { valor, rotulo: valor };
  });
  const anoInteiroSelecionado = meses.length === textos.meses.length;

  function alternarMes(mes: number) {
    setInicio("");
    setFim("");
    setPeriodo("mes");
    setMeses((atuais) =>
      atuais.includes(mes) ? atuais.filter((item) => item !== mes) : [...atuais, mes],
    );
  }

  function alternarAnoInteiro() {
    setInicio("");
    setFim("");
    setPeriodo("mes");
    setMeses(anoInteiroSelecionado ? [] : textos.meses.map((_, indice) => indice + 1));
  }

  function aplicarPeriodo(novo: PeriodoEnxuto) {
    setPeriodo(novo);
    const agora = new Date();
    setAno(agora.getFullYear());
    setOrigemInicio("");
    setOrigemFim("");
    if (novo === "hoje") {
      const dia = isoLocal(agora);
      setInicio(dia);
      setFim(dia);
      setMeses([]);
      return;
    }
    if (novo === "seteDias") {
      const fimJanela = isoLocal(agora);
      const inicioJanela = new Date(agora);
      inicioJanela.setDate(agora.getDate() - 6);
      setInicio(isoLocal(inicioJanela));
      setFim(fimJanela);
      setMeses([]);
      return;
    }
    setInicio("");
    setFim("");
    if (novo === "ano") {
      setMeses(textos.meses.map((_, indice) => indice + 1));
      return;
    }
    setMeses([agora.getMonth() + 1]);
  }

  return (
    <div
      data-testid="dashboard-conteudo"
      className="flex min-h-full flex-col gap-5 bg-background p-6 lg:p-7 max-sm:gap-4 max-sm:p-4"
    >
      <header className="-mx-6 -mt-6 border-b bg-card px-6 pt-7 lg:-mx-7 lg:-mt-7 lg:px-7 max-sm:-mx-4 max-sm:-mt-4 max-sm:px-4">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h1 className="text-xl font-bold tracking-tight text-foreground">{textos.titulo}</h1>
            <p className="mt-1 hidden text-sm text-muted-foreground sm:block">{textos.descricao}</p>
          </div>
          {telaEstreita && (
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="rounded-full"
              onClick={() => setAvisoComputadorAberto(true)}
            >
              <Lock className="size-[calc(var(--tamanho-icone-interface)*0.875)]" aria-hidden />
              {textos.filtros.rotulo}
            </Button>
          )}
        </div>
        {/* As abas futuras continuam desabilitadas até terem dados e comportamento reais. */}
        <nav className="mt-3 flex flex-wrap items-end gap-1" aria-label={textos.abas.rotulo}>
          <Aba ativa>{textos.abas.visaoGeral}</Aba>
          {[textos.abas.operacional, textos.abas.comercial].map((aba) => (
            <Aba key={aba}>{`${aba} · ${textos.abas.depois}`}</Aba>
          ))}
          <Aba className="max-sm:hidden">{`${textos.abas.iaAutomacao} · ${textos.abas.depois}`}</Aba>
        </nav>
      </header>

      {telaEstreita && (
        <div className="flex flex-wrap gap-2" role="group" aria-label={textos.periodos.rotulo}>
          {(["hoje", "seteDias", "mes", "ano"] as const).map((item) => (
            <Button
              key={item}
              type="button"
              size="sm"
              variant="outline"
              aria-pressed={periodo === item}
              className={cn(
                "rounded-full",
                periodo === item && "border-primary bg-primary/10 text-primary hover:bg-primary/15",
              )}
              onClick={() => aplicarPeriodo(item)}
            >
              {textos.periodos[item]}
            </Button>
          ))}
        </div>
      )}

      <section
        className="hidden rounded-2xl border bg-card p-4 sm:block"
        aria-label={textos.filtros.rotulo}
      >
        <div className="flex flex-wrap items-center gap-2">
          <div className="w-20 shrink-0">
            <label
              className="sr-only"
              htmlFor="dashboard-ano"
            >
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
          <div className="min-w-0 flex flex-1 flex-wrap items-center gap-2">
            <span className="sr-only">
              {textos.filtros.meses}
            </span>
            {/*
              Pílulas suaves com contorno: só o que está selecionado ganha destaque. Doze pílulas
              azuis sólidas liam como "tudo selecionado" e viravam parede de azul.
            */}
            <div className="flex flex-wrap gap-1">
              <Button
                type="button"
                size="sm"
                variant="outline"
                aria-pressed={anoInteiroSelecionado}
                onClick={alternarAnoInteiro}
                className={cn(
                  "rounded-full",
                  anoInteiroSelecionado &&
                    "border-transparent bg-primary/10 text-primary hover:bg-primary/15",
                )}
              >
                {textos.filtros.anoInteiro}
              </Button>
              {textos.meses.map((mes, indice) => {
                const valor = indice + 1;
                const ativo = meses.includes(valor);
                return (
                  <Button
                    key={mes}
                    type="button"
                    size="sm"
                    variant="outline"
                    aria-pressed={ativo}
                    onClick={() => alternarMes(valor)}
                    className={cn(
                      "min-w-10 rounded-full",
                      ativo && "border-transparent bg-primary/10 text-primary hover:bg-primary/15",
                    )}
                  >
                    {mes}
                  </Button>
                );
              })}
            </div>
          </div>
          <div
            className="flex shrink-0 items-center rounded-xl bg-muted p-1"
            role="group"
            aria-label={textos.modos.rotulo}
          >
            <Button
              type="button"
              size="sm"
              variant={modo === "compacta" ? "outline" : "ghost"}
              aria-pressed={modo === "compacta"}
              onClick={() => setModo("compacta")}
              className={cn("h-8 gap-1.5 px-2 text-xs", modo === "compacta" && "text-primary shadow-sm")}
            >
              <LayoutGrid className="size-3.5" aria-hidden />
              {textos.modos.compacta}
            </Button>
            <Button
              type="button"
              size="sm"
              variant={modo === "expandida" ? "outline" : "ghost"}
              aria-pressed={modo === "expandida"}
              onClick={() => setModo("expandida")}
              className={cn("h-8 gap-1.5 px-2 text-xs", modo === "expandida" && "text-primary shadow-sm")}
            >
              <ChartColumn className="size-3.5" aria-hidden />
              {textos.modos.expandida}
            </Button>
          </div>
          <div className="w-48 shrink-0 text-xs">
            <span className="sr-only">
              {textos.filtros.originacao}
            </span>
            <SeletorDeOriginacao
              inicio={origemInicio}
              fim={origemFim}
              onChange={(novoInicio, novoFim) => {
                setOrigemInicio(novoInicio);
                setOrigemFim(novoFim);
              }}
            />
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
        <ErroDeCarregamento mensagem={textos.erro} onTentarNovamente={() => consulta.refetch()} />
      )}
      {consulta.data && (
        <ConteudoDashboard
          dados={consulta.data}
          modo={modo}
          telaEstreita={telaEstreita}
          atualizadoEm={consulta.dataUpdatedAt ?? 0}
        />
      )}
      <Dialog open={avisoComputadorAberto} onOpenChange={setAvisoComputadorAberto}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{textos.somenteComputador}</DialogTitle>
            <DialogDescription>{textos.avisoComputador}</DialogDescription>
          </DialogHeader>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function Aba({
  children,
  ativa = false,
  className,
}: {
  children: React.ReactNode;
  ativa?: boolean;
  className?: string;
}) {
  return (
    <Button
      type="button"
      variant="ghost"
      size="sm"
      disabled={!ativa}
      aria-current={ativa ? "page" : undefined}
      className={cn(
        "-mb-px h-auto rounded-none border-0 border-b-2 border-transparent px-3 pb-2.5 text-sm hover:bg-transparent",
        ativa ? "border-primary font-semibold text-primary" : "text-muted-foreground",
        className,
      )}
    >
      {children}
    </Button>
  );
}

/**
 * Um botão com calendário no lugar dos dois campos `De`/`Até` sempre expostos. O intervalo é
 * escolhido em `mode="range"` num popover só — evita popover dentro de popover, que em base-ui
 * fecha o de fora ao clicar no calendário de dentro.
 */
function SeletorDeOriginacao({
  inicio,
  fim,
  onChange,
}: {
  inicio: string;
  fim: string;
  onChange: (inicio: string, fim: string) => void;
}) {
  const textos = useTextos().dashboard;
  const intervaloCompleto = Boolean(inicio) && Boolean(fim);
  const rotulo = intervaloCompleto
    ? preencher(textos.filtros.intervalo, { inicio: dataCurta(inicio), fim: dataCurta(fim) })
    : textos.filtros.originacao;

  return (
    <Popover>
      <PopoverTrigger
        render={
          <Button
            type="button"
            variant="outline"
            className={cn(
              "w-full justify-between font-normal",
              !intervaloCompleto && "text-muted-foreground",
            )}
          />
        }
      >
        <span className="truncate">{rotulo}</span>
        <CalendarDays className="size-(--tamanho-icone-interface)" aria-hidden />
      </PopoverTrigger>
      <PopoverContent align="end" className="w-auto p-0">
        <Calendar
          mode="range"
          locale={ptBR}
          weekStartsOn={0}
          selected={{ from: paraDataLocal(inicio), to: paraDataLocal(fim) }}
          onSelect={(intervalo) =>
            onChange(
              intervalo?.from ? isoLocal(intervalo.from) : "",
              intervalo?.to ? isoLocal(intervalo.to) : "",
            )
          }
        />
        {(inicio || fim) && (
          <div className="border-t p-2">
            <Button type="button" variant="ghost" size="sm" onClick={() => onChange("", "")}>
              {textos.filtros.limpar}
            </Button>
          </div>
        )}
      </PopoverContent>
    </Popover>
  );
}

function ConteudoDashboard({
  dados,
  modo,
  telaEstreita,
  atualizadoEm,
}: {
  dados: VisaoGeralDashboard;
  modo: ModoDashboard;
  telaEstreita: boolean;
  atualizadoEm: number;
}) {
  const textos = useTextos().dashboard;
  const duracao = dados.tempoMedioAtendimento.segundos;
  const valorDuracao = duracao === null ? textos.semDado : formatarDuracao(duracao, textos.tempo);

  return (
    <>
      <FaixaAoVivo status={dados.statusAoVivo} atualizadoEm={atualizadoEm} />

      {/* Indicadores com fonte confirmada; NPS continua sem coleta própria. */}
      <section
        className={cn(
          "grid grid-cols-1 sm:grid-cols-2",
          modo === "compacta" ? "gap-3" : "gap-4",
          modo === "compacta" ? "lg:grid-cols-3 2xl:grid-cols-4" : "lg:grid-cols-2 2xl:grid-cols-3",
        )}
        role="group"
        aria-label={textos.kpis.rotulo}
      >
        <Kpi
          titulo={textos.kpis.atendimentos}
          valor={numero(dados.atendimentos.noPeriodo)}
          apoio={preencher(textos.kpis.atendimentosApoio, {
            total: numero(dados.atendimentos.acumulado),
          })}
          comparativo={dados.atendimentos.comparativo}
          Icone={UsersRound}
          tom={TOM_ATENDIMENTOS}
          modo={modo}
          serie={dados.seriesMensais ?? []}
          campoSerie="atendimentos"
        />
        <Kpi
          titulo={textos.kpis.novosLeads}
          valor={numero(dados.novosLeads.noPeriodo)}
          apoio={textos.kpis.novosLeadsApoio}
          comparativo={dados.novosLeads.comparativo}
          Icone={UserPlus}
          tom={TOM_NOVOS_LEADS}
          modo={modo}
          serie={dados.seriesMensais ?? []}
          campoSerie="novosLeads"
        />
        <Kpi
          titulo={textos.kpis.tempoMedio}
          valor={valorDuracao}
          apoio={textos.kpis.tempoMedioApoio}
          comparativo={dados.tempoMedioAtendimento.comparativo}
          Icone={Clock3}
          tom={TOM_TEMPO}
          modo={modo}
          quedaPositiva
          serie={dados.seriesMensais ?? []}
          campoSerie="tempoMedioSegundos"
        />
        <Kpi
          titulo={textos.kpis.vendas}
          valor={numero(dados.vendasFechadas.noPeriodo)}
          apoio={preencher(textos.kpis.vendasApoio, {
            total: numero(dados.vendasFechadas.acumulado),
          })}
          comparativo={dados.vendasFechadas.comparativo}
          Icone={CircleDollarSign}
          tom={TOM_VENDAS}
          modo={modo}
          serie={dados.seriesMensais ?? []}
          campoSerie="vendasFechadas"
        />
        <Kpi
          titulo={textos.kpis.conversao}
          valor={
            dados.taxaConversao.percentual === null
              ? textos.semDado
              : `${percentual(dados.taxaConversao.percentual)}%`
          }
          apoio={preencher(textos.kpis.conversaoApoio, {
            vendas: dados.taxaConversao.vendas,
            leads: dados.taxaConversao.leadsRecebidos,
          })}
          comparativo={dados.taxaConversao.comparativo}
          Icone={Handshake}
          tom={TOM_CONVERSAO}
          modo={modo}
          serie={dados.seriesMensais ?? []}
          campoSerie="taxaConversao"
        />
        <Kpi
          titulo={textos.kpis.csat}
          valor={
            dados.avaliacaoMedia.media === null
              ? textos.semDado
              : `${percentual(dados.avaliacaoMedia.media)}/${dados.avaliacaoMedia.escalaMaxima}`
          }
          apoio={preencher(textos.kpis.csatApoio, { total: dados.avaliacaoMedia.quantidade })}
          comparativo={dados.avaliacaoMedia.comparativo}
          Icone={Star}
          tom={TOM_AVALIACAO}
          modo={modo}
          serie={dados.seriesMensais ?? []}
          campoSerie="avaliacaoMedia"
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
          tom={TOM_IA}
          modo={modo}
          serie={dados.seriesMensais ?? []}
          campoSerie="resolucaoPorIa"
        />
      </section>

      <div className="grid min-w-0 grid-cols-1 gap-4 xl:grid-cols-12">
        <div className="min-w-0 xl:col-span-7">
          <Funil dados={dados} />
        </div>
        <div className="min-w-0 xl:col-span-5">
          <ResumoSatisfacao dados={dados.avaliacaoMedia} />
        </div>
      </div>
      <Equipe dados={dados} />
      <HorarioDePico dados={dados} />
      {telaEstreita && (
        <p className="flex items-start gap-2 rounded-xl border border-dashed border-primary/30 bg-primary/5 px-3 py-3 text-xs text-muted-foreground">
          <Monitor className="mt-0.5 size-(--tamanho-icone-interface) shrink-0 text-primary" aria-hidden />
          {textos.avisoComputador}
        </p>
      )}
    </>
  );
}

interface KpiProps {
  titulo: string;
  valor: string;
  apoio: string;
  comparativo: Comparativo | null;
  Icone: React.ComponentType<{ className?: string }>;
  tom: string;
  quedaPositiva?: boolean;
  modo: ModoDashboard;
  serie: NonNullable<VisaoGeralDashboard["seriesMensais"]>;
  campoSerie: CampoSerie;
}

function Kpi({
  titulo,
  valor,
  apoio,
  comparativo,
  Icone,
  tom,
  quedaPositiva = false,
  modo,
  serie,
  campoSerie,
}: KpiProps) {
  const textos = useTextos().dashboard;
  const formatarValor = (valorSerie: number) => {
    if (campoSerie === "tempoMedioSegundos") return formatarDuracao(valorSerie, textos.tempo);
    if (campoSerie === "taxaConversao" || campoSerie === "resolucaoPorIa") return `${percentual(valorSerie)}%`;
    if (campoSerie === "avaliacaoMedia") return percentual(valorSerie);
    return numero(valorSerie);
  };
  const amostras = serie
    .filter((ponto) => ponto.disponivel && ponto[campoSerie] !== null)
    .map((ponto) => ({ mes: ponto.mes, valor: Number(ponto[campoSerie]) }));
  const resumoSerie = amostras.length === 0 ? textos.semDado : (() => {
    const minimo = amostras.reduce((atual, ponto) => ponto.valor < atual.valor ? ponto : atual);
    const maximo = amostras.reduce((atual, ponto) => ponto.valor > atual.valor ? ponto : atual);
    const media = amostras.reduce((total, ponto) => total + ponto.valor, 0) / amostras.length;
    const rotuloMes = (mes: string) => textos.meses[Number(mes.slice(-2)) - 1] ?? mes;
    return preencher(textos.kpis.resumoSerie, {
      minMes: rotuloMes(minimo.mes), min: formatarValor(minimo.valor),
      maxMes: rotuloMes(maximo.mes), max: formatarValor(maximo.valor),
      media: formatarValor(media),
    });
  })();

  // O mesmo agregado mensal alimenta a sparkline compacta e o gráfico detalhado expandido.
  return (
    <Card
      className={cn(modo === "compacta" ? "min-h-22 gap-1 py-2" : "min-h-60 gap-1.5 py-5")}
      style={{ "--tom": tom } as React.CSSProperties}
    >
      <CardHeader className="flex items-center gap-3 px-4">
        <span
          className={cn(
            "flex shrink-0 items-center justify-center text-[var(--tom)]",
            modo === "expandida" &&
              "size-10 rounded-xl bg-[color-mix(in_oklab,var(--tom)_10%,transparent)]",
          )}
        >
          <Icone className="size-(--tamanho-icone-interface)" />
        </span>
        <div className="min-w-0 flex-1">
          <CardTitle className="truncate text-[11px] font-semibold tracking-wider text-muted-foreground uppercase">
            {titulo}
          </CardTitle>
          {modo === "expandida" && (
            <div className="mt-0.5 flex items-center gap-2">
              <p className="text-2xl font-bold tracking-tight" data-testid={`kpi-${titulo}`}>
                {valor}
              </p>
              {comparativo && (
                <SeloDeTendencia
                  comparativo={comparativo}
                  quedaPositiva={quedaPositiva}
                  sufixo={textos.kpis.periodoAnterior}
                />
              )}
            </div>
          )}
        </div>
        {/*
          Sem comparativo, sem selo. A API só devolve variação quando existe período anterior
          comparável; calcular no cliente daria selo inventado em painel executivo.
        */}
        {modo === "compacta" && comparativo && (
          <SeloDeTendencia
            comparativo={comparativo}
            quedaPositiva={quedaPositiva}
            sufixo={textos.kpis.periodoAnterior}
          />
        )}
      </CardHeader>
      <CardContent className="px-4">
        {modo === "compacta" && (
          <div className="float-right mt-0.5 ml-2">
            <GraficoKpi pontos={serie} campo={campoSerie} compacto titulo={titulo}
              meses={textos.meses} semDado={textos.semDado} formatar={formatarValor} />
          </div>
        )}
        {modo === "compacta" && (
          <p className="text-2xl font-bold tracking-tight" data-testid={`kpi-${titulo}`}>
            {valor}
          </p>
        )}
        <p className="mt-0.5 text-[11px] text-muted-foreground">
          {modo === "compacta" ? apoio : resumoSerie}
        </p>
        {modo === "expandida" && (
          <GraficoKpi pontos={serie} campo={campoSerie} compacto={false} titulo={titulo}
            meses={textos.meses} semDado={textos.semDado} formatar={formatarValor} />
        )}
      </CardContent>
    </Card>
  );
}

function SeloDeTendencia({
  comparativo,
  quedaPositiva,
  sufixo,
}: {
  comparativo: Comparativo;
  quedaPositiva: boolean;
  sufixo: string;
}) {
  const subiu = comparativo.valor >= 0;
  const positivo = quedaPositiva ? !subiu : subiu;
  const texto = formatarComparativo(comparativo);

  // Selo curto como no mockup ("+14%"); o "vs. período anterior" continua para leitor de tela e
  // na dica ao passar o mouse, em vez de ocupar o canto do card.
  return (
    <span
      className={cn(
        "shrink-0 rounded-md px-1.5 py-0.5 text-[10px] font-semibold tabular-nums",
        positivo
          ? "bg-[color-mix(in_oklab,var(--cor-sucesso)_12%,transparent)] text-[var(--cor-sucesso)]"
          : "bg-destructive/10 text-destructive",
      )}
      title={`${texto} ${sufixo}`}
      data-testid="selo-tendencia"
    >
      {texto}
      <span className="sr-only"> {sufixo}</span>
    </span>
  );
}

/**
 * Faixa "AGORA": só os quatro itens do mockup com critério real hoje (ver Javadoc de
 * StatusAoVivo). "Atualizado há Ns" ticka a cada segundo via efeito — `Date.now()` só é chamado
 * dentro do `setInterval`, nunca durante o render, que precisa ficar puro.
 */
function FaixaAoVivo({
  status,
  atualizadoEm,
}: {
  status: VisaoGeralDashboard["statusAoVivo"];
  atualizadoEm: number;
}) {
  const textos = useTextos().dashboard;
  const [agora, setAgora] = useState(atualizadoEm);
  useEffect(() => {
    const id = setInterval(() => setAgora(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);
  const segundos = Math.max(0, Math.round((agora - atualizadoEm) / 1000));
  const rotuloAtualizado =
    segundos < 5
      ? textos.agora.atualizadoAgora
      : segundos < 60
        ? preencher(textos.agora.atualizadoSegundos, { segundos })
        : preencher(textos.agora.atualizadoMinutos, { minutos: Math.round(segundos / 60) });

  // Mesmo desenho do mockup: rótulo pequeno em cima, número grande embaixo, itens distribuídos
  // em colunas iguais pela largura. São quatro colunas, não sete, pelo motivo do Javadoc acima.
  return (
    <section
      aria-label={textos.agora.rotulo}
      className="flex flex-wrap items-center gap-x-5 gap-y-3 rounded-2xl bg-sidebar px-5 py-4 text-sidebar-foreground"
    >
      <span className="inline-flex shrink-0 items-center gap-2 border-sidebar-foreground/15 pr-5 text-xs font-bold tracking-wider uppercase sm:border-r">
        <span className="size-2 rounded-full bg-[var(--cor-sucesso)]" aria-hidden />
        {textos.agora.rotulo}
      </span>
      <div className="grid min-w-0 flex-1 grid-cols-2 gap-x-6 gap-y-3 sm:grid-cols-3 xl:grid-cols-5">
        <ItemAoVivo rotulo={textos.agora.emIa} valor={status.emIa} />
        <ItemAoVivo rotulo={textos.agora.emAtendimento} valor={status.emAtendimento} />
        <ItemAoVivo rotulo={textos.agora.leadsNovosHoje} valor={status.leadsNovosHoje} />
        <ItemAoVivo rotulo={textos.agora.vendasHoje} valor={status.vendasHoje} />
        <ItemAoVivo
          rotulo={textos.agora.atendentesOnline}
          valor={`${status.atendentesOnline.online}/${status.atendentesOnline.total}`}
        />
      </div>
      <span className="shrink-0 text-[11px] text-sidebar-foreground/70">{rotuloAtualizado}</span>
    </section>
  );
}

function ItemAoVivo({ rotulo, valor }: { rotulo: string; valor: number | string }) {
  return (
    <div className="min-w-0">
      <p className="truncate text-[11px] font-medium text-sidebar-foreground/75">{rotulo}</p>
      <p className="text-lg leading-tight font-bold tabular-nums">
        {typeof valor === "number" ? numero(valor) : valor}
      </p>
    </div>
  );
}

function ResumoSatisfacao({
  dados,
}: {
  dados: VisaoGeralDashboard["avaliacaoMedia"];
}) {
  const textos = useTextos().dashboard;
  const { otimo, bom, ruim } = dados.distribuicao;
  const total = otimo + bom + ruim;
  const percentualOtimo = total === 0 ? 0 : (otimo / total) * 100;
  const percentualBom = total === 0 ? 0 : (bom / total) * 100;
  const percentualRuim = total === 0 ? 0 : (ruim / total) * 100;
  const fimOtimo = percentualOtimo;
  const fimBom = percentualOtimo + percentualBom;
  const grafico: React.CSSProperties = {
    background: total === 0
      ? "var(--muted)"
      : `conic-gradient(var(--cor-sucesso) 0% ${fimOtimo}%, var(--cor-atencao) ${fimOtimo}% ${fimBom}%, var(--destructive) ${fimBom}% 100%)`,
  };
  const apoio = preencher(textos.satisfacao.apoio, { total: dados.quantidade });
  const media = dados.media === null
    ? textos.semDado
    : `${percentual(dados.media)}/${dados.escalaMaxima}`;

  return (
    <Card className="h-full min-w-0">
      <CardHeader>
        <CardTitle>{textos.satisfacao.titulo}</CardTitle>
        <p className="text-xs font-normal text-muted-foreground">{apoio}</p>
      </CardHeader>
      <CardContent className="grid grid-cols-[minmax(8rem,auto)_minmax(0,1fr)] items-center gap-5 max-sm:grid-cols-1">
        <div
          className="mx-auto grid size-36 place-items-center rounded-full"
          style={grafico}
          role="img"
          aria-label={`${textos.satisfacao.titulo}: ${apoio}`}
          data-testid="grafico-distribuicao-avaliacoes"
        >
          <div className="grid size-24 place-content-center rounded-full bg-card text-center">
            <span className="text-2xl font-bold tabular-nums">{media}</span>
            <span className="text-[10px] uppercase tracking-wide text-muted-foreground">
              {textos.satisfacao.media}
            </span>
          </div>
        </div>
        <ul className="space-y-3 text-xs">
          <FaixaSatisfacao rotulo={textos.satisfacao.otimo} quantidade={otimo} percentual={percentualOtimo} tom="bg-[var(--cor-sucesso)]" />
          <FaixaSatisfacao rotulo={textos.satisfacao.bom} quantidade={bom} percentual={percentualBom} tom="bg-[var(--cor-atencao)]" />
          <FaixaSatisfacao rotulo={textos.satisfacao.ruim} quantidade={ruim} percentual={percentualRuim} tom="bg-destructive" />
          {dados.quantidade === 0 && (
            <li className="text-muted-foreground">{textos.satisfacao.vazio}</li>
          )}
        </ul>
      </CardContent>
    </Card>
  );
}

function FaixaSatisfacao({
  rotulo,
  quantidade,
  percentual: valorPercentual,
  tom,
}: {
  rotulo: string;
  quantidade: number;
  percentual: number;
  tom: string;
}) {
  return (
    <li className="flex min-w-0 items-center gap-2">
      <span className={cn("size-2.5 shrink-0 rounded-sm", tom)} aria-hidden />
      <span className="min-w-0 flex-1 truncate">{rotulo}</span>
      <span className="shrink-0 tabular-nums text-muted-foreground">
        {quantidade} · {percentual(valorPercentual)}%
      </span>
    </li>
  );
}

/**
 * Tabela "Equipe · desempenho": junta atendimentos, vendas e nota já lidos pelo backend
 * (`equipeDesempenho`), ordenados por vendas conforme o mockup. Conversão e 1ª resposta por
 * atendente ficam fora — ver relatório da E197.
 */
function Equipe({ dados }: { dados: VisaoGeralDashboard }) {
  const textos = useTextos().dashboard;
  const linhas = dados.equipeDesempenho;
  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
          <div>
            <CardTitle className="flex items-center gap-2 font-semibold">
              <UsersRound className="size-(--tamanho-icone-interface) text-primary" aria-hidden />
              {textos.secoes.equipe}
            </CardTitle>
            <p className="text-xs font-normal text-muted-foreground">{textos.secoes.equipeApoio}</p>
          </div>
          <span className="text-[11px] font-medium text-muted-foreground">
            {textos.secoes.equipeOrdenadoPor}
          </span>
        </div>
      </CardHeader>
      <CardContent>
        {linhas.length === 0 ? (
          <p className="text-sm text-muted-foreground">{textos.equipe.vazio}</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[480px] text-sm">
              <thead>
                <tr className="border-b text-left text-[11px] font-semibold tracking-wide text-muted-foreground uppercase">
                  <th className="py-2 pr-3 font-semibold">{textos.equipe.colunaAtendente}</th>
                  <th className="px-3 py-2 text-right font-semibold">
                    {textos.equipe.colunaAtendimentos}
                  </th>
                  <th className="px-3 py-2 text-right font-semibold">{textos.equipe.colunaVendas}</th>
                  <th className="py-2 pl-3 text-right font-semibold">{textos.equipe.colunaNota}</th>
                </tr>
              </thead>
              <tbody>
                {linhas.map((atendente, indice) => {
                  const medalha = MEDALHAS[indice];
                  return (
                    <tr key={atendente.id} className="border-b last:border-0">
                      <td className="py-3 pr-3">
                        <div className="flex items-center gap-2.5">
                          {/* Pódio do mockup: 1º a 3º em cor cheia; do 4º em diante, neutro. */}
                          <span
                            className={cn(
                              "flex size-6 shrink-0 items-center justify-center rounded-[6px] text-[11px] font-bold",
                              medalha ? "bg-[var(--medalha)] text-white" : "bg-muted text-muted-foreground",
                            )}
                            style={medalha ? ({ "--medalha": medalha } as React.CSSProperties) : undefined}
                            data-testid={`posicao-${indice + 1}`}
                          >
                            {indice + 1}
                          </span>
                          <span
                            className="flex size-8 shrink-0 items-center justify-center rounded-[8px] bg-[var(--avatar)] text-[11px] font-bold text-white"
                            style={{ "--avatar": CORES_DE_AVATAR[indice % CORES_DE_AVATAR.length] } as React.CSSProperties}
                          >
                            {iniciaisDoNome(atendente.nome)}
                          </span>
                          <span className="min-w-0 truncate font-semibold">{atendente.nome}</span>
                        </div>
                      </td>
                      <td className="px-3 py-3 text-right tabular-nums">
                        {numero(atendente.atendimentos)}
                      </td>
                      <td className="px-3 py-3 text-right font-bold tabular-nums">
                        {numero(atendente.vendas)}
                      </td>
                      <td className="py-3 pl-3 text-right font-semibold text-[var(--cor-atencao-escura)] tabular-nums">
                        {atendente.nota === null ? textos.equipe.semNota : percentual(atendente.nota)}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        {dados.rankingDeVendas.semResponsavel > 0 && (
          <p className="mt-3 text-xs text-muted-foreground">
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
  const maximo = Math.max(...dados.funil.map((etapa) => etapa.quantidade), 0);
  // Desenho do mockup: etapa à esquerda, barra no meio, "Passa" à direita. A coluna "Parado"
  // (tempo médio parado na etapa) fica de fora: o DTO não traz tempo por etapa.
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 font-semibold">
          <ListFilter className="size-(--tamanho-icone-interface) text-primary" aria-hidden />
          {textos.secoes.funil}
        </CardTitle>
        <p className="text-xs font-normal text-muted-foreground">{textos.funilApoio}</p>
      </CardHeader>
      <CardContent>
        {dados.funil.length === 0 && (
          <p className="text-sm text-muted-foreground">{textos.funil.vazio}</p>
        )}
        <div className="grid grid-cols-[minmax(7rem,11rem)_1fr_4rem] items-center gap-x-4 border-b pb-2 text-[10px] font-semibold tracking-wider text-muted-foreground uppercase">
          <span>{textos.funil.colunaEtapa}</span>
          <span aria-hidden />
          <span className="text-right">{textos.funil.colunaPassa}</span>
        </div>
        {dados.funil.map((etapa) => {
          const largura = fracaoPercentual(etapa.quantidade, maximo);
          const numeroDentro = largura >= PERCENTUAL_MINIMO_PARA_NUMERO_DENTRO;
          return (
            <div
              key={etapa.id}
              className="grid grid-cols-[minmax(7rem,11rem)_1fr_4rem] items-center gap-x-4 py-2"
            >
              <p className="truncate text-sm font-medium">{etapa.nome}</p>
              {/*
                O trilho é o elemento visível — com o funil inteiro zerado (o estado real desta
                instância) a barra preenchida some, mas a linha continua ali, com o número ao
                lado. Barra invisível seria indistinguível de etapa que não carregou.
              */}
              <div className="relative h-7 min-w-0 overflow-hidden rounded-md bg-muted">
                <div
                  className="absolute inset-y-0 left-0 rounded-md bg-gradient-to-r from-primary to-[color-mix(in_oklab,var(--primary)_55%,white)]"
                  style={{ width: `${largura}%` }}
                  data-testid="barra-funil"
                />
                <span
                  className={cn(
                    "absolute inset-y-0 flex items-center px-2.5 text-xs font-bold tabular-nums",
                    numeroDentro ? "left-0 text-primary-foreground" : "text-foreground",
                  )}
                  style={numeroDentro ? undefined : { left: `${largura}%` }}
                >
                  {numero(etapa.quantidade)}
                </span>
              </div>
              <span className="text-right text-xs font-semibold text-primary tabular-nums">
                {etapa.percentualDePassagem === null
                  ? textos.funil.semPassagem
                  : `${percentual(etapa.percentualDePassagem)}%`}
              </span>
            </div>
          );
        })}
        {/*
          "Perdido" não entra na sequência ordenada acima: uma perda pode vir de qualquer etapa em
          andamento, então misturá-la na ordem por `ordem` distorceria o % de passagem da etapa
          seguinte (ver relatório da E197). Fica como agregado à parte, sem barra nem passagem —
          mesmo tratamento do mockup.
        */}
        <div className="grid grid-cols-[minmax(7rem,11rem)_1fr_4rem] items-center gap-x-4 border-t pt-2">
          <p className="truncate text-sm font-medium">{textos.funil.perdido}</p>
          <div className="flex h-7 min-w-0 items-center rounded-md bg-destructive/10 px-2.5">
            <span
              className="text-xs font-bold tabular-nums text-destructive"
              data-testid="quantidade-perdidos"
            >
              {numero(dados.leadsPerdidos)}
            </span>
          </div>
          <span className="text-right text-xs font-semibold text-muted-foreground tabular-nums">
            {textos.funil.semPassagem}
          </span>
        </div>
      </CardContent>
    </Card>
  );
}

function HorarioDePico({ dados }: { dados: VisaoGeralDashboard }) {
  const textos = useTextos().dashboard;
  const porHora = new Map(dados.horarioDePico.map((item) => [item.hora, item.quantidade]));
  const maximo = Math.max(...dados.horarioDePico.map((item) => item.quantidade), 0);
  const resumo = textoDoResumo(resumoDoHorario(porHora), textos.horario);
  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
          <div>
            <CardTitle className="flex items-center gap-2 font-semibold">
              <Clock3 className="size-(--tamanho-icone-interface) text-primary" aria-hidden />
              {textos.secoes.horarioPico}
            </CardTitle>
            <p className="text-xs font-normal text-muted-foreground">{textos.horario.apoio}</p>
          </div>
          {resumo && (
            <span className="text-[11px] font-medium text-muted-foreground" data-testid="resumo-horario">
              {resumo}
            </span>
          )}
        </div>
      </CardHeader>
      <CardContent>
        {dados.horarioDePico.length === 0 ? (
          <p className="text-sm text-muted-foreground">{textos.horario.vazio}</p>
        ) : (
          <div className="overflow-x-auto pb-1">
            <div className="flex h-56 min-w-[760px] items-end gap-2 px-1">
              {HORAS_DO_DIA.map((hora) => {
                const quantidade = porHora.get(hora) ?? 0;
                const faixa = faixaDaHora(quantidade, maximo);
                return (
                  <div
                    key={hora}
                    className="flex h-full flex-1 flex-col justify-end gap-1 text-center"
                  >
                    <span
                      className={cn(
                        "text-[10px] tabular-nums",
                        faixa === "pico" ? "font-semibold text-primary" : "text-muted-foreground",
                      )}
                    >
                      {quantidade ? numero(quantidade) : ""}
                    </span>
                    {/* Mesma leitura do mockup: pico em azul cheio, o resto em tons claros. */}
                    <div
                      className={cn(
                        "min-h-1 w-full rounded-t-md",
                        faixa === "pico" &&
                          "bg-gradient-to-b from-primary to-[color-mix(in_oklab,var(--primary)_60%,white)]",
                        faixa === "intermediaria" && "bg-[color-mix(in_oklab,var(--primary)_28%,white)]",
                        faixa === "baixa" && "bg-[color-mix(in_oklab,var(--primary)_16%,white)]",
                      )}
                      style={{ height: `${fracaoPercentual(quantidade, maximo) * 0.82}%` }}
                      data-testid="barra-horario"
                      data-faixa={faixa}
                    />
                    <span className="pt-1 text-[10px] text-muted-foreground">
                      {preencher(textos.horario.hora, { hora })}
                    </span>
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

function textoDoResumo(
  resumo: ResumoDoHorario | null,
  textos: {
    hora: string;
    picoUnico: string;
    picos: string;
    picosEValeUnico: string;
    picosEVale: string;
  },
): string | null {
  if (!resumo) return null;
  const hora = (valor: number) => preencher(textos.hora, { hora: valor });
  if (resumo.tipo === "picoUnico") return preencher(textos.picoUnico, { hora: hora(resumo.hora) });
  const picos = { manha: hora(resumo.manha), tarde: hora(resumo.tarde) };
  if (resumo.tipo === "picos") return preencher(textos.picos, picos);
  if (resumo.inicio === resumo.fim) {
    return preencher(textos.picosEValeUnico, { ...picos, vale: hora(resumo.inicio) });
  }
  return preencher(textos.picosEVale, {
    ...picos,
    inicio: hora(resumo.inicio),
    fim: hora(resumo.fim),
  });
}

function formatarComparativo(comparativo: Comparativo): string {
  const sinal = comparativo.valor > 0 ? "+" : "";
  const sufixo =
    comparativo.unidade === "PONTOS_PERCENTUAIS"
      ? "pp"
      : comparativo.unidade === "PERCENTUAL"
        ? "%"
        : "";
  return `${sinal}${percentual(comparativo.valor)}${sufixo}`;
}

function formatarDuracao(
  segundos: number,
  textos: { minutos: string; horasMinutos: string },
): string {
  const minutos = Math.round(segundos / 60);
  if (minutos < 60) return preencher(textos.minutos, { minutos });
  return preencher(textos.horasMinutos, { horas: Math.floor(minutos / 60), minutos: minutos % 60 });
}
