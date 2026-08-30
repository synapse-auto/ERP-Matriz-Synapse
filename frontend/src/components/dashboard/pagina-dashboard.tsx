"use client";

import { useMemo, useState } from "react";
import { ptBR } from "date-fns/locale";
import {
  Bot,
  CalendarDays,
  Clock3,
  Handshake,
  Lock,
  Monitor,
  Star,
  TrendingDown,
  TrendingUp,
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
import { useVisaoGeralDashboard } from "@/lib/dashboard/use-dashboard";
import type { Comparativo, VisaoGeralDashboard } from "@/lib/dashboard/types";
import { useTelaEstreita } from "@/lib/navegacao/tela-estreita";
import { cn, iniciaisDoNome } from "@/lib/utils";

const ANOS_DISPONIVEIS = 7;
const HORAS_DO_DIA = Array.from({ length: 24 }, (_, hora) => hora);
type PeriodoEnxuto = "hoje" | "seteDias" | "mes" | "ano";

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

/*
 * Ouro / prata / bronze do pódio. Não existe token de "prata" nem de "bronze" no tema; o mais
 * próximo honesto é --texto-fraco (cinza azulado) e --cor-atencao-escura (âmbar queimado), ambos
 * já no tema.json. Trocar por tokens dedicados é mudança de tema, não de componente.
 */
const MEDALHAS = ["var(--cor-atencao)", "var(--texto-fraco)", "var(--cor-atencao-escura)"];

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
      className="flex min-h-full flex-col gap-6 bg-background p-6 lg:p-8 max-sm:gap-4 max-sm:p-4"
    >
      <header className="flex items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-foreground">{textos.titulo}</h1>
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
            <Lock className="size-3.5" aria-hidden />
            {textos.filtros.rotulo}
          </Button>
        )}
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

      {/*
        Abas por sublinhado, não por pílula preenchida. As três abas futuras continuam com o sufixo
        "Em breve" e `disabled`: o modelo não tem o sufixo porque é mock com tudo pronto — aqui,
        aba que parece clicável e não abre nada é pior que aba feia.
      */}
      <nav className="flex flex-wrap items-end gap-1 border-b" aria-label={textos.abas.rotulo}>
        <Aba ativa>{textos.abas.visaoGeral}</Aba>
        {[textos.abas.operacional, textos.abas.comercial].map((aba) => (
          <Aba key={aba}>{`${aba} · ${textos.abas.depois}`}</Aba>
        ))}
        <Aba className="max-sm:hidden">{`${textos.abas.iaAutomacao} · ${textos.abas.depois}`}</Aba>
      </nav>

      <section
        className="hidden rounded-xl border bg-card/75 p-4 sm:block"
        aria-label={textos.filtros.rotulo}
      >
        <div className="flex flex-wrap items-end gap-3">
          <div className="w-28">
            <label
              className="mb-1.5 block text-xs font-semibold text-muted-foreground"
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
          <div className="min-w-0 flex-1">
            <span className="mb-1.5 block text-xs font-semibold text-muted-foreground">
              {textos.filtros.meses}
            </span>
            {/*
              Pílulas suaves com contorno: só o que está selecionado ganha destaque. Doze pílulas
              azuis sólidas liam como "tudo selecionado" e viravam parede de azul.
            */}
            <div className="flex flex-wrap gap-1.5">
              <Button
                type="button"
                size="sm"
                variant="outline"
                aria-pressed={anoInteiroSelecionado}
                onClick={alternarAnoInteiro}
                className={cn(
                  "rounded-full",
                  anoInteiroSelecionado &&
                    "border-primary bg-primary/10 text-primary hover:bg-primary/15",
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
                      ativo && "border-primary bg-primary/10 text-primary hover:bg-primary/15",
                    )}
                  >
                    {mes}
                  </Button>
                );
              })}
            </div>
          </div>
          <div className="w-[214px] shrink-0">
            <span className="mb-1.5 block text-xs font-semibold text-muted-foreground">
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
      {consulta.data && <ConteudoDashboard dados={consulta.data} telaEstreita={telaEstreita} />}
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
        <CalendarDays className="size-4" aria-hidden />
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
  telaEstreita,
}: {
  dados: VisaoGeralDashboard;
  telaEstreita: boolean;
}) {
  const textos = useTextos().dashboard;
  const duracao = dados.tempoMedioAtendimento.segundos;
  const valorDuracao = duracao === null ? textos.semDado : formatarDuracao(duracao, textos.tempo);

  return (
    <>
      {/*
        Grade do modelo: indicadores em 3×2 à esquerda, "Top atendentes" numa coluna própria à
        direita. São cinco indicadores porque cinco é o que a tela tem hoje — a sexta célula fica
        vazia de propósito, em vez de inventar métrica para preencher a grade.
      */}
      <section className="grid gap-4 xl:grid-cols-[minmax(0,3fr)_minmax(0,1.15fr)]">
        <div
          className="grid grid-cols-2 gap-3 sm:grid-cols-3"
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
          />
          <Kpi
            titulo={textos.kpis.tempoMedio}
            valor={valorDuracao}
            apoio={textos.kpis.tempoMedioApoio}
            comparativo={dados.tempoMedioAtendimento.comparativo}
            Icone={Clock3}
            tom={TOM_TEMPO}
            quedaPositiva
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
          />
        </div>
        <Ranking dados={dados} />
      </section>

      <Funil dados={dados} />
      <HorarioDePico dados={dados} />
      {telaEstreita && (
        <p className="flex items-start gap-2 rounded-xl border border-dashed border-primary/30 bg-primary/5 px-3 py-3 text-xs text-muted-foreground">
          <Monitor className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden />
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
}

function Kpi({ titulo, valor, apoio, comparativo, Icone, tom, quedaPositiva = false }: KpiProps) {
  const textos = useTextos().dashboard;

  return (
    <Card className="gap-3" style={{ "--tom": tom } as React.CSSProperties}>
      <CardHeader className="grid grid-cols-[auto_1fr] items-center gap-2.5">
        <span className="flex size-9 items-center justify-center rounded-lg bg-[color-mix(in_oklab,var(--tom)_14%,transparent)] text-[var(--tom)]">
          <Icone className="size-4" />
        </span>
        <CardTitle className="truncate text-xs font-semibold tracking-wide text-muted-foreground uppercase">
          {titulo}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <p className="text-3xl font-bold tracking-tight" data-testid={`kpi-${titulo}`}>
          {valor}
        </p>
        <p className="mt-1 text-xs text-muted-foreground">{apoio}</p>
        {/*
          Sem comparativo, sem selo. A API só devolve variação quando existe período anterior
          comparável; calcular no cliente daria selo inventado em painel executivo.
        */}
        {comparativo && (
          <SeloDeTendencia
            comparativo={comparativo}
            quedaPositiva={quedaPositiva}
            sufixo={textos.kpis.periodoAnterior}
          />
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
  const IconeTendencia = subiu ? TrendingUp : TrendingDown;

  return (
    <p
      className={cn(
        "mt-3 inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold",
        positivo
          ? "bg-[color-mix(in_oklab,var(--cor-sucesso)_12%,transparent)] text-[var(--cor-sucesso)]"
          : "bg-destructive/10 text-destructive",
      )}
    >
      <IconeTendencia className="size-3.5" aria-hidden />
      {formatarComparativo(comparativo)} {sufixo}
    </p>
  );
}

function Ranking({ dados }: { dados: VisaoGeralDashboard }) {
  const textos = useTextos().dashboard;
  return (
    <Card>
      <CardHeader>
        <CardTitle>{textos.secoes.ranking}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        {dados.rankingDeAvaliacoes.atendentes.length === 0 && (
          <p className="text-sm text-muted-foreground">{textos.ranking.vazio}</p>
        )}
        {dados.rankingDeAvaliacoes.atendentes.map((atendente, indice) => {
          const medalha = MEDALHAS[indice];
          return (
            <div
              key={atendente.id}
              className="flex items-center gap-2.5 rounded-lg bg-muted/45 px-2.5 py-2"
            >
              <span
                className={cn(
                  "flex size-6 shrink-0 items-center justify-center rounded-full text-[11px] font-bold",
                  medalha
                    ? "bg-[color-mix(in_oklab,var(--medalha)_20%,transparent)] text-[var(--medalha)]"
                    : "bg-muted text-muted-foreground",
                )}
                style={medalha ? ({ "--medalha": medalha } as React.CSSProperties) : undefined}
                data-testid={`posicao-${indice + 1}`}
              >
                {indice + 1}
              </span>
              <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary/10 text-[11px] font-bold text-primary">
                {iniciaisDoNome(atendente.nome)}
              </span>
              <span className="min-w-0 flex-1 truncate text-sm font-medium">{atendente.nome}</span>
              <span className="shrink-0 text-right">
                <span className="text-sm font-bold">
                  {preencher(textos.ranking.media, { media: percentual(atendente.media) })}
                </span>
                <span className="mt-0.5 block text-[10px] text-muted-foreground">
                  {preencher(
                    atendente.quantidade === 1
                      ? textos.ranking.quantidadeSingular
                      : textos.ranking.quantidadePlural,
                    { total: atendente.quantidade },
                  )}
                </span>
              </span>
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}

function Funil({ dados }: { dados: VisaoGeralDashboard }) {
  const textos = useTextos().dashboard;
  const maximo = Math.max(...dados.funil.map((etapa) => etapa.quantidade), 0);
  return (
    <Card>
      <CardHeader>
        <CardTitle>{textos.secoes.funil}</CardTitle>
        <p className="text-xs font-normal text-muted-foreground">{textos.funilApoio}</p>
      </CardHeader>
      <CardContent className="space-y-3">
        {dados.funil.length === 0 && (
          <p className="text-sm text-muted-foreground">{textos.funil.vazio}</p>
        )}
        {dados.funil.map((etapa) => {
          const largura = fracaoPercentual(etapa.quantidade, maximo);
          const numeroDentro = largura >= PERCENTUAL_MINIMO_PARA_NUMERO_DENTRO;
          return (
            <div key={etapa.id}>
              <p className="mb-1 truncate text-sm font-medium">{etapa.nome}</p>
              <div className="flex items-center gap-3">
                {/*
                  O trilho é o elemento visível — com o funil inteiro zerado (o estado real desta
                  instância) a barra preenchida some, mas a linha continua ali, com o número ao
                  lado. Barra invisível seria indistinguível de etapa que não carregou.
                */}
                <div className="relative h-7 min-w-0 flex-1 overflow-hidden rounded-md bg-muted">
                  <div
                    className="absolute inset-y-0 left-0 rounded-md bg-primary"
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
                    {etapa.quantidade}
                  </span>
                </div>
                <span className="w-14 shrink-0 text-right text-xs font-semibold text-muted-foreground tabular-nums">
                  {etapa.percentualDePassagem === null
                    ? textos.funil.semPassagem
                    : `${percentual(etapa.percentualDePassagem)}%`}
                </span>
              </div>
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}

function HorarioDePico({ dados }: { dados: VisaoGeralDashboard }) {
  const textos = useTextos().dashboard;
  const porHora = new Map(dados.horarioDePico.map((item) => [item.hora, item.quantidade]));
  const maximo = Math.max(...dados.horarioDePico.map((item) => item.quantidade), 0);
  return (
    <Card>
      <CardHeader>
        <CardTitle>{textos.secoes.horarioPico}</CardTitle>
      </CardHeader>
      <CardContent>
        {dados.horarioDePico.length === 0 ? (
          <p className="text-sm text-muted-foreground">{textos.horario.vazio}</p>
        ) : (
          <div className="overflow-x-auto pb-1">
            <div className="flex h-52 min-w-[760px] items-end gap-2 border-b px-1">
              {HORAS_DO_DIA.map((hora) => {
                const quantidade = porHora.get(hora) ?? 0;
                return (
                  <div
                    key={hora}
                    className="flex h-full flex-1 flex-col justify-end gap-1 text-center"
                  >
                    <span className="text-[10px] text-muted-foreground">{quantidade || ""}</span>
                    <div
                      className="min-h-px w-full rounded-t bg-primary/80"
                      style={{ height: `${fracaoPercentual(quantidade, maximo) * 0.82}%` }}
                      data-testid="barra-horario"
                    />
                    <span className="pb-1 text-[10px] text-muted-foreground">
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
