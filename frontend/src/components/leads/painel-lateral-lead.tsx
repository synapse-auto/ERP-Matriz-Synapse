"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import {
  AlertTriangle,
  Building2,
  Calculator,
  LifeBuoy,
  Bell,
  Clock,
  Repeat2,
  Tag as TagIcon,
  X,
  type LucideIcon,
} from "lucide-react";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Seletor } from "@/components/ui/seletor";
import { SeletorData } from "@/components/ui/seletor-data";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { FormularioLembrete } from "@/components/lembretes/formulario-lembrete";
import { FormularioMensagemProgramada } from "@/components/mensagens-programadas/formulario-mensagem-programada";
import { ErroDeApi } from "@/lib/api/errors";
import { useTextos } from "@/lib/config/textos-provider";
import {
  useCanais,
  useCamposCustomizados,
  useDesvincularTag,
  useEtapas,
  useLead,
  useSalvarFicha,
  useTagsDoLead,
  useTimelineDoLead,
  useTodasAsTags,
  useVincularTag,
} from "@/lib/lead/use-painel-lead";
import type {
  CampoCustomizado,
  EtapaAtendimento,
  LeadFicha,
  OrigemEvento,
  TagDoLead,
} from "@/lib/lead/types";
import { iniciaisDoNome, urlSegura } from "@/lib/utils";

interface Props {
  leadId: string | null;
  onFechar: () => void;
}

const ICONES_DE_TAG: Record<string, LucideIcon> = {
  tag: TagIcon,
  calculator: Calculator,
  "alert-triangle": AlertTriangle,
  "building-2": Building2,
  repeat: Repeat2,
  "life-buoy": LifeBuoy,
};

export function primeiroCampoObrigatorioAusente(
  campos: CampoCustomizado[],
  valores: Record<string, unknown>,
): CampoCustomizado | undefined {
  return campos.find((campo) => {
    if (!campo.obrigatorio) return false;
    const valor = valores[campo.chave];
    return valor === null || valor === undefined || (typeof valor === "string" && !valor.trim());
  });
}

function valoresIniciais(lead: LeadFicha, campos: CampoCustomizado[]): Record<string, unknown> {
  const valores = { ...lead.dadosCustomizados };
  for (const campo of campos) {
    const valor = valores[campo.chave];
    if (campo.tipo === "DATA" && typeof valor === "string") {
      valores[campo.chave] = valor.slice(0, 10);
    }
  }
  return valores;
}

function paraPayload(campos: CampoCustomizado[], valores: Record<string, unknown>) {
  return Object.fromEntries(
    campos.map((campo) => {
      const valor = valores[campo.chave];
      if (campo.tipo === "NUMERO") {
        return [campo.chave, valor === "" || valor == null ? null : Number(valor)];
      }
      if (campo.tipo === "BOOLEANO") {
        return [campo.chave, Boolean(valor)];
      }
      return [campo.chave, valor === "" || valor === undefined ? null : valor];
    }),
  );
}

export function PainelLateralLead({ leadId, onFechar }: Props) {
  const textosGerais = useTextos();
  const textos = textosGerais.painelLead;
  const lead = useLead(leadId);
  const etapas = useEtapas();
  const campos = useCamposCustomizados();
  const canais = useCanais();
  const todasAsTags = useTodasAsTags();
  const tagsDoLead = useTagsDoLead(leadId);
  const timeline = useTimelineDoLead(leadId);
  const salvar = useSalvarFicha(leadId ?? "");
  const vincular = useVincularTag(leadId ?? "");
  const desvincular = useDesvincularTag(leadId ?? "");
  const [notas, setNotas] = useState("");
  const [valores, setValores] = useState<Record<string, unknown>>({});
  const [tagSelecionada, setTagSelecionada] = useState("");
  const [avisoEdicao, setAvisoEdicao] = useState<"salvo" | "erro" | "obrigatorio" | null>(null);
  const [avisoTags, setAvisoTags] = useState(false);
  const [lembreteAberto, setLembreteAberto] = useState(false);
  const [programadaAberta, setProgramadaAberta] = useState(false);
  const inicializado = useRef<string | null>(null);

  useEffect(() => {
    if (!leadId) {
      inicializado.current = null;
      return;
    }
    if (lead.data && campos.data && inicializado.current !== lead.data.id) {
      setNotas(lead.data.notas ?? "");
      setValores(valoresIniciais(lead.data, campos.data));
      setAvisoEdicao(null);
      setAvisoTags(false);
      inicializado.current = lead.data.id;
    }
  }, [campos.data, lead.data, leadId]);

  const tagsDisponiveis = useMemo(() => {
    const vinculadas = new Set((tagsDoLead.data ?? []).map((tag) => tag.id));
    return (todasAsTags.data ?? []).filter((tag) => !vinculadas.has(tag.id));
  }, [tagsDoLead.data, todasAsTags.data]);

  if (!leadId) return null;

  const indisponivel = lead.error instanceof ErroDeApi && lead.error.status === 404;

  function salvarFicha(evento: React.FormEvent) {
    evento.preventDefault();
    if (!lead.data || !campos.data) return;
    const ausente = primeiroCampoObrigatorioAusente(campos.data, valores);
    if (ausente) {
      setAvisoEdicao("obrigatorio");
      document.getElementById(`campo-${ausente.chave}`)?.focus();
      return;
    }

    const anterior = lead.data;
    const payload = { notas, dadosCustomizados: paraPayload(campos.data, valores) };
    setAvisoEdicao(null);
    salvar.mutate(payload, {
      onError: () => {
        setNotas(anterior.notas ?? "");
        setValores(valoresIniciais(anterior, campos.data ?? []));
        setAvisoEdicao("erro");
      },
      onSuccess: (salvo) => {
        setNotas(salvo.notas ?? "");
        setValores(valoresIniciais(salvo, campos.data ?? []));
        setAvisoEdicao("salvo");
      },
    });
  }

  function adicionarTag() {
    const tag = tagsDisponiveis.find((item) => item.id === tagSelecionada);
    if (!tag) return;
    setAvisoTags(false);
    vincular.mutate(
      { tag },
      {
        onError: () => setAvisoTags(true),
        onSuccess: () => setTagSelecionada(""),
      },
    );
  }

  function removerTag(tag: TagDoLead) {
    setAvisoTags(false);
    desvincular.mutate({ tag }, { onError: () => setAvisoTags(true) });
  }

  return (
    <aside
      role="dialog"
      aria-modal="false"
      aria-label={textos.titulo}
      className="fixed inset-y-0 right-0 z-50 flex w-[min(30rem,100vw)] flex-col border-l border-border bg-background shadow-xl"
    >
      <div className="flex items-center justify-between border-b border-border px-5 py-3">
        <h2 className="text-lg font-semibold text-foreground">{textos.titulo}</h2>
        <Button type="button" variant="ghost" size="icon" aria-label={textos.fechar} onClick={onFechar}>
          <X />
        </Button>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">
        {lead.isLoading ? (
          <Carregando />
        ) : indisponivel ? (
          <p role="alert" className="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
            {textos.indisponivel}
          </p>
        ) : lead.isError || !lead.data ? (
          <p role="alert" className="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
            {textosGerais.estados.erroGenerico}
          </p>
        ) : (
          <div className="space-y-5">
            <CabecalhoDaFicha lead={lead.data} />

            <dl className="grid grid-cols-2 gap-x-4 gap-y-3">
              <Informacao rotulo={textos.dados.telefone} valor={lead.data.telefone} vazio={textos.dados.naoInformado} />
              <Informacao rotulo={textos.dados.email} valor={lead.data.email} vazio={textos.dados.naoInformado} />
              <Informacao rotulo={textos.dados.cpf} valor={lead.data.cpf} vazio={textos.dados.naoInformado} />
              <Informacao rotulo={textos.dados.empresa} valor={lead.data.empresa} vazio={textos.dados.naoInformado} />
              <Informacao rotulo={textos.dados.localizacao} valor={lead.data.localizacao} vazio={textos.dados.naoInformado} />
              <Informacao
                rotulo={textos.dados.canalOrigem}
                valor={canais.data?.find((canal) => canal.id === lead.data.canalOrigemId)?.nome}
                vazio={textos.dados.naoInformado}
              />
            </dl>

            <Separator />
            <Stepper etapas={etapas.data ?? []} etapaAtualId={lead.data.etapaAtendimentoId} />

            <div className="grid grid-cols-2 gap-3">
              <Contador valor={lead.data.numAtendimentos} rotulo={textos.contadores.atendimentos} />
              <Contador valor={lead.data.numMensagens} rotulo={textos.contadores.mensagens} />
            </div>

            <Button type="button" variant="outline" onClick={() => setLembreteAberto(true)}>
              <Bell className="size-4" /> {textos.acoes.lembrete}
            </Button>
            <Button type="button" variant="outline" onClick={() => setProgramadaAberta(true)}>
              <Clock className="size-4" /> {textos.acoes.mensagemProgramada}
            </Button>

            <TagsDaFicha
              tags={tagsDoLead.data ?? []}
              disponiveis={tagsDisponiveis}
              selecionada={tagSelecionada}
              pendente={vincular.isPending || desvincular.isPending}
              aviso={avisoTags}
              onSelecionar={setTagSelecionada}
              onAdicionar={adicionarTag}
              onRemover={removerTag}
            />

            <section className="space-y-2">
              <h3 className="text-sm font-semibold text-foreground">{textos.resumoIa.titulo}</h3>
              <p className="whitespace-pre-wrap rounded-md bg-muted p-3 text-sm text-muted-foreground">
                {lead.data.resumoIa || textos.resumoIa.vazio}
              </p>
            </section>

            <form className="space-y-4" onSubmit={salvarFicha} noValidate>
              <div className="space-y-2">
                <label htmlFor="notas-lead" className="text-sm font-semibold text-foreground">
                  {textos.edicao.notas}
                </label>
                <Textarea
                  id="notas-lead"
                  value={notas}
                  placeholder={textos.edicao.notasPlaceholder}
                  onChange={(evento) => setNotas(evento.target.value)}
                />
              </div>

              {(campos.data?.length ?? 0) > 0 && (
                <section className="space-y-3">
                  <h3 className="text-sm font-semibold text-foreground">
                    {textos.edicao.camposCustomizados}
                  </h3>
                  {campos.data?.map((campo) => (
                    <CampoDaFicha
                      key={campo.chave}
                      campo={campo}
                      valor={valores[campo.chave]}
                      onChange={(valor) => setValores((atuais) => ({ ...atuais, [campo.chave]: valor }))}
                    />
                  ))}
                </section>
              )}

              {avisoEdicao && (
                <p
                  role="alert"
                  className={
                    avisoEdicao === "salvo"
                      ? "text-sm text-cor-sucesso"
                      : "text-sm text-destructive"
                  }
                >
                  {avisoEdicao === "salvo"
                    ? textos.edicao.salvo
                    : avisoEdicao === "obrigatorio"
                      ? textos.edicao.campoObrigatorio
                      : textos.edicao.erroReversao}
                </p>
              )}
              <Button type="submit" disabled={salvar.isPending}>
                {salvar.isPending ? textos.edicao.salvando : textos.edicao.salvar}
              </Button>
            </form>

            <Separator />
            <TimelineDaFicha consulta={timeline} />
          </div>
        )}
      </div>
      {lead.data && <FormularioLembrete aberto={lembreteAberto} leadId={lead.data.id} leadNome={lead.data.nome} onFechar={() => setLembreteAberto(false)} />}
      {lead.data && <FormularioMensagemProgramada aberto={programadaAberta} leadId={lead.data.id} leadNome={lead.data.nome} onFechar={() => setProgramadaAberta(false)} />}
    </aside>
  );
}

function Carregando() {
  return (
    <div className="space-y-4">
      <Skeleton className="h-16 w-full" />
      <Skeleton className="h-28 w-full" />
      <Skeleton className="h-20 w-full" />
    </div>
  );
}

function CabecalhoDaFicha({ lead }: { lead: LeadFicha }) {
  const foto = urlSegura(lead.fotoUrl);
  return (
    <div className="flex items-center gap-3">
      <Avatar className="size-14">
        {foto && <AvatarImage src={foto} alt={lead.nome} />}
        <AvatarFallback>{iniciaisDoNome(lead.nome)}</AvatarFallback>
      </Avatar>
      <div className="min-w-0">
        <p className="truncate text-lg font-semibold text-foreground">{lead.nome}</p>
        {lead.empresa && <p className="truncate text-sm text-muted-foreground">{lead.empresa}</p>}
      </div>
    </div>
  );
}

function Informacao({ rotulo, valor, vazio }: { rotulo: string; valor?: string | null; vazio: string }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs font-medium text-muted-foreground">{rotulo}</dt>
      <dd className="truncate text-sm text-foreground">{valor || vazio}</dd>
    </div>
  );
}

function Stepper({ etapas, etapaAtualId }: { etapas: EtapaAtendimento[]; etapaAtualId: string | null }) {
  const textos = useTextos().painelLead.etapa;
  const ordenadas = [...etapas].sort((a, b) => a.ordem - b.ordem);
  const indiceAtual = ordenadas.findIndex((etapa) => etapa.id === etapaAtualId);
  if (ordenadas.length === 0 || indiceAtual < 0) {
    return (
      <section className="space-y-1">
        <h3 className="text-sm font-semibold text-foreground">{textos.titulo}</h3>
        <p className="text-sm text-muted-foreground">{textos.semEtapa}</p>
      </section>
    );
  }

  return (
    <section className="space-y-2">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-foreground">{textos.titulo}</h3>
        <span className="text-xs font-medium text-primary">
          {textos.posicao
            .replace("{atual}", String(indiceAtual + 1))
            .replace("{total}", String(ordenadas.length))}
        </span>
      </div>
      <div className="flex items-center" aria-label={ordenadas[indiceAtual].nome}>
        {ordenadas.map((etapa, indice) => (
          <div key={etapa.id} className="flex flex-1 items-center last:flex-none">
            <span
              aria-current={indice === indiceAtual ? "step" : undefined}
              className={
                indice <= indiceAtual
                  ? "size-3 shrink-0 rounded-full bg-primary"
                  : "size-3 shrink-0 rounded-full border border-border bg-background"
              }
            />
            {indice < ordenadas.length - 1 && (
              <span className={indice < indiceAtual ? "h-px flex-1 bg-primary" : "h-px flex-1 bg-border"} />
            )}
          </div>
        ))}
      </div>
      <div className="flex justify-between gap-3 text-xs text-muted-foreground">
        <span>{ordenadas[0].nome}</span>
        <span className="text-right">{ordenadas.at(-1)?.nome}</span>
      </div>
    </section>
  );
}

function Contador({ valor, rotulo }: { valor: number; rotulo: string }) {
  return (
    <div className="rounded-md border border-border p-3 text-center">
      <p className="text-xl font-semibold text-foreground">{valor}</p>
      <p className="text-xs text-muted-foreground">{rotulo}</p>
    </div>
  );
}

interface TagsProps {
  tags: TagDoLead[];
  disponiveis: TagDoLead[];
  selecionada: string;
  pendente: boolean;
  aviso: boolean;
  onSelecionar: (id: string) => void;
  onAdicionar: () => void;
  onRemover: (tag: TagDoLead) => void;
}

function TagsDaFicha({
  tags,
  disponiveis,
  selecionada,
  pendente,
  aviso,
  onSelecionar,
  onAdicionar,
  onRemover,
}: TagsProps) {
  const textos = useTextos().painelLead.tags;
  return (
    <section className="space-y-2">
      <h3 className="text-sm font-semibold text-foreground">{textos.titulo}</h3>
      <div className="flex flex-wrap gap-2">
        {tags.map((tag) => {
          const Icone = (tag.icone && ICONES_DE_TAG[tag.icone]) || TagIcon;
          return (
            <span
              key={tag.id}
              className="inline-flex items-center gap-1 rounded-full border px-2 py-1 text-xs font-medium"
              style={{ borderColor: tag.cor, color: tag.cor }}
            >
              <Icone className="size-3" />
              {tag.nome}
              <button
                type="button"
                aria-label={textos.remover.replace("{nome}", tag.nome)}
                disabled={pendente}
                onClick={() => onRemover(tag)}
              >
                <X className="size-3" />
              </button>
            </span>
          );
        })}
      </div>
      {disponiveis.length > 0 && (
        <div className="flex gap-2">
          <Seletor
            className="min-w-0 flex-1"
            valor={selecionada}
            ariaLabel={textos.selecionar}
            placeholder={textos.selecionar}
            opcoes={disponiveis.map((tag) => ({ valor: tag.id, rotulo: tag.nome }))}
            onChange={onSelecionar}
          />
          <Button type="button" variant="outline" disabled={!selecionada || pendente} onClick={onAdicionar}>
            {textos.adicionar}
          </Button>
        </div>
      )}
      {aviso && (
        <p role="alert" className="text-sm text-destructive">
          {textos.erroReversao}
        </p>
      )}
    </section>
  );
}

function CampoDaFicha({
  campo,
  valor,
  onChange,
}: {
  campo: CampoCustomizado;
  valor: unknown;
  onChange: (valor: unknown) => void;
}) {
  const textos = useTextos().painelLead.edicao;
  const id = `campo-${campo.chave}`;
  const rotuloObrigatoriedade = campo.obrigatorio ? textos.obrigatorio : textos.opcional;

  return (
    <div className="space-y-1">
      <label htmlFor={id} className="flex items-center justify-between gap-2 text-sm text-foreground">
        <span>{campo.rotulo}</span>
        <span className="text-xs text-muted-foreground">{rotuloObrigatoriedade}</span>
      </label>
      {campo.tipo === "LISTA" ? (
        <Seletor
          id={id}
          obrigatorio={campo.obrigatorio}
          valor={typeof valor === "string" ? valor : ""}
          placeholder={textos.opcional}
          opcoes={campo.opcoes.map((opcao) => ({ valor: opcao, rotulo: opcao }))}
          onChange={onChange}
        />
      ) : campo.tipo === "BOOLEANO" ? (
        <input
          id={id}
          type="checkbox"
          checked={Boolean(valor)}
          className="size-4 accent-primary"
          onChange={(evento) => onChange(evento.target.checked)}
        />
      ) : campo.tipo === "DATA" ? (
        <SeletorData
          id={id}
          obrigatorio={campo.obrigatorio}
          valor={typeof valor === "string" ? valor : ""}
          placeholder={campo.rotulo}
          onChange={onChange}
        />
      ) : (
        <Input
          id={id}
          required={campo.obrigatorio}
          type={campo.tipo === "NUMERO" ? "number" : "text"}
          step={campo.tipo === "NUMERO" ? 1 : undefined}
          value={typeof valor === "string" || typeof valor === "number" ? String(valor) : ""}
          onChange={(evento) => onChange(evento.target.value)}
        />
      )}
    </div>
  );
}

function TimelineDaFicha({ consulta }: { consulta: ReturnType<typeof useTimelineDoLead> }) {
  const textos = useTextos().painelLead.timeline;
  const eventos = consulta.data?.pages.flatMap((pagina) => pagina.eventos) ?? [];
  const rotulos: Record<OrigemEvento, string> = {
    SISTEMA: textos.origens.sistema,
    AUTOMACAO: textos.origens.automacao,
    USUARIO: textos.origens.usuario,
  };

  return (
    <section className="space-y-3">
      <h3 className="text-sm font-semibold text-foreground">{textos.titulo}</h3>
      {consulta.isLoading ? (
        <Skeleton className="h-20 w-full" />
      ) : consulta.isError ? (
        <p role="alert" className="text-sm text-destructive">
          {textos.erro}
        </p>
      ) : eventos.length === 0 ? (
        <p className="text-sm text-muted-foreground">{textos.vazia}</p>
      ) : (
        <ol className="space-y-3 border-l border-border pl-4">
          {eventos.map((evento) => (
            <li key={evento.id} className="relative space-y-1">
              <span className="absolute -left-[1.2rem] top-1.5 size-2 rounded-full bg-primary" />
              <div className="flex items-center justify-between gap-2">
                <span className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                  {rotulos[evento.origem]}
                </span>
                <time className="text-xs text-muted-foreground" dateTime={evento.criadoEm}>
                  {new Intl.DateTimeFormat(undefined, {
                    dateStyle: "short",
                    timeStyle: "short",
                  }).format(new Date(evento.criadoEm))}
                </time>
              </div>
              <p className="text-sm text-foreground">{evento.descricao}</p>
            </li>
          ))}
        </ol>
      )}
      {consulta.hasNextPage && (
        <Button
          type="button"
          variant="outline"
          disabled={consulta.isFetchingNextPage}
          onClick={() => void consulta.fetchNextPage()}
        >
          {consulta.isFetchingNextPage ? textos.carregandoMais : textos.carregarMais}
        </Button>
      )}
    </section>
  );
}
