"use client";

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  Bell,
  CalendarClock,
  ChevronDown,
  ChevronUp,
  Mail,
  MapPin,
  Pencil,
  PanelRightClose,
  Phone,
  Plus,
  Sparkles,
  StickyNote,
  Trash2,
  UserRound,
} from "lucide-react";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { tomDoAvatar } from "@/components/ui/avatar-iniciais";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { useTextos } from "@/lib/config/textos-provider";
import { useEtapas, useLead } from "@/lib/lead/use-painel-lead";
import {
  cancelarMensagemProgramada,
  removerLembrete,
} from "@/lib/suporte/api";
import {
  useLembretesDoLead,
  useMensagensProgramadasDoLead,
} from "@/lib/suporte/use-suporte";
import type {
  Lembrete,
  MensagemProgramada,
  PaginaLembretes,
  PaginaMensagensProgramadas,
} from "@/lib/suporte/types";
import { iniciaisDoNome, urlSegura } from "@/lib/utils";

import { AtalhoTags } from "./atalho-tags";
import { FormularioLembrete } from "../lembretes/formulario-lembrete";
import { FormularioMensagemProgramada } from "../mensagens-programadas/formulario-mensagem-programada";

type Props = {
  leadId: string;
  responsavelNome: string | null;
  onRetrair: () => void;
};

/**
 * Painel de detalhes da conversa aberta — E17 §Bloco 2. No protótipo (`Atendimentos.html`) é uma
 * 4ª coluna sempre visível, não um overlay; aqui vira o mesmo, ao lado do chat. `PainelLateralLead`
 * (a versão em overlay) continua existindo para os fluxos da Agenda.
 *
 * <p>Seção "Arquivos compartilhados" do protótipo fica de fora: Banco de Arquivos está fora da
 * primeira entrega (docs/09) — não há endpoint dos dois lados.
 */
export function PainelDaConversa({ leadId, responsavelNome, onRetrair }: Props) {
  const textos = useTextos().atendimentos.painel;
  const textosLead = useTextos().painelLead;
  const lead = useLead(leadId);
  const etapas = useEtapas();

  if (!lead.data) return null;

  const etapaAtual = (etapas.data ?? []).find(
    (etapa) => etapa.id === lead.data!.etapaAtendimentoId,
  );
  const etapasOrdenadas = [...(etapas.data ?? [])].sort(
    (a, b) => a.ordem - b.ordem,
  );
  const posicaoEtapa = etapaAtual
    ? etapasOrdenadas.findIndex((etapa) => etapa.id === etapaAtual.id) + 1
    : 0;

  return (
    <aside
      id="painel-detalhes-lead"
      className="flex h-full w-[344px] shrink-0 flex-col border-l border-border bg-background"
    >
      <div className="flex flex-none items-center justify-between gap-2 p-4">
        <p className="text-sm font-bold text-foreground">{textos.titulo}</p>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          onClick={onRetrair}
          aria-expanded="true"
          aria-controls="painel-detalhes-lead"
          aria-label={textos.retrair}
          title={textos.retrair}
        >
          <PanelRightClose className="size-4" aria-hidden />
        </Button>
      </div>

      <div className="min-h-0 flex-1 space-y-5 overflow-y-auto p-4 pt-0">
        <div className="flex flex-col items-center gap-2 text-center">
          <Avatar className="size-16">
            {urlSegura(lead.data.fotoUrl) && (
              <AvatarImage
                src={urlSegura(lead.data.fotoUrl)}
                alt={lead.data.nome}
              />
            )}
            <AvatarFallback
              className="text-white"
              style={{ backgroundColor: tomDoAvatar(lead.data.id) }}
            >
              {iniciaisDoNome(lead.data.nome)}
            </AvatarFallback>
          </Avatar>
          <div>
            <p className="text-base font-extrabold text-foreground">
              {lead.data.nome}
            </p>
            {lead.data.empresa && (
              <p className="text-xs text-muted-foreground">
                {lead.data.empresa}
              </p>
            )}
          </div>
        </div>

        <div className="grid grid-cols-2 gap-2.5">
          <ContadorDoPainel
            valor={lead.data.numAtendimentos}
            rotulo={textosLead.contadores.atendimentos}
          />
          <ContadorDoPainel
            valor={lead.data.numMensagens}
            rotulo={textosLead.contadores.mensagens}
          />
        </div>

        <div>
          <p className="mb-3 px-0.5 text-xs font-bold tracking-wide text-muted-foreground uppercase">
            {textos.informacoesGerais}
          </p>
          <div className="space-y-3">
            <InformacaoDoPainel
              icone={<Phone className="size-4" aria-hidden />}
              rotulo={textosLead.dados.telefone}
              valor={lead.data.telefone}
            />
            <InformacaoDoPainel
              icone={<Mail className="size-4" aria-hidden />}
              rotulo={textosLead.dados.email}
              valor={lead.data.email}
            />
            <InformacaoDoPainel
              icone={<MapPin className="size-4" aria-hidden />}
              rotulo={textosLead.dados.localizacao}
              valor={lead.data.localizacao}
            />
            <InformacaoDoPainel
              icone={<UserRound className="size-4" aria-hidden />}
              rotulo={textosLead.dados.responsavel}
              valor={responsavelNome}
            />
          </div>
        </div>

        {etapaAtual && (
          <div>
            <div className="flex items-center justify-between gap-2">
              <p className="text-xs font-bold tracking-wide text-muted-foreground uppercase">
                {textosLead.etapa.titulo}
              </p>
              <span className="text-xs font-semibold text-muted-foreground">
                {textosLead.etapa.posicao
                  .replace("{atual}", String(posicaoEtapa))
                  .replace("{total}", String(etapasOrdenadas.length))}
              </span>
            </div>
            <div className="mt-2 flex gap-1" aria-hidden>
              {etapasOrdenadas.map((etapa, indice) => (
                <span
                  key={etapa.id}
                  className="h-1.5 flex-1 rounded-full bg-muted"
                  style={
                    indice < posicaoEtapa
                      ? {
                          backgroundColor:
                            etapaAtual.corVisual ?? "var(--primary)",
                        }
                      : undefined
                  }
                />
              ))}
            </div>
            <div className="mt-1 flex justify-between gap-2 text-[0.65rem] text-muted-foreground">
              <span>{etapasOrdenadas[0]?.nome ?? textosLead.etapa.semEtapa}</span>
              {etapasOrdenadas.length > 1 && (
                <span>{etapasOrdenadas.at(-1)?.nome}</span>
              )}
            </div>
            <span
              className="mt-2 inline-flex rounded-full bg-muted px-2 py-0.5 text-xs font-semibold text-foreground"
              style={
                etapaAtual.corVisual
                  ? {
                      backgroundColor: `${etapaAtual.corVisual}22`,
                      color: etapaAtual.corVisual,
                    }
                  : undefined
              }
            >
              {etapaAtual.nome}
            </span>
          </div>
        )}

        <div>
          <p className="mb-2 px-0.5 text-xs font-bold tracking-wide text-muted-foreground uppercase">
            {textosLead.tags.titulo}
          </p>
          <AtalhoTags leadId={leadId} modo="painel" />
        </div>

        <SecaoColapsavel
          icone={<Sparkles className="size-4 text-primary" />}
          titulo={textos.secoes.resumo}
          abertaPorPadrao
        >
          {lead.data.resumoIa && (
            <div className="mb-2 flex items-start gap-2 rounded-lg border border-primary/25 bg-primary/5 p-3">
              <Sparkles className="mt-0.5 size-4 shrink-0 text-primary" />
              <p className="text-sm text-foreground">{lead.data.resumoIa}</p>
            </div>
          )}
          <p className="mb-1 text-[0.7rem] font-bold tracking-wide text-muted-foreground uppercase">
            {textos.notasInternas}
          </p>
          <div className="flex items-start gap-2 rounded-lg border border-border bg-muted/40 p-3">
            <StickyNote className="mt-0.5 size-4 shrink-0 text-cor-atencao" />
            <p className="text-sm text-foreground">
              {lead.data.notas || textosLead.resumoIa.vazio}
            </p>
          </div>
        </SecaoColapsavel>

        <SecaoDeProgramadas
          leadId={leadId}
          leadNome={lead.data.nome}
          titulo={textos.secoes.programadas}
          vazio={textos.vazioProgramadas}
        />
        <SecaoDeLembretes
          leadId={leadId}
          leadNome={lead.data.nome}
          titulo={textos.secoes.lembretes}
          vazio={textos.vazioLembretes}
        />
      </div>
    </aside>
  );
}

function ContadorDoPainel({
  valor,
  rotulo,
}: {
  valor: number;
  rotulo: string;
}) {
  return (
    <div className="rounded-xl bg-muted p-3">
      <p className="text-[0.7rem] font-semibold text-muted-foreground">
        {rotulo}
      </p>
      <p className="mt-1 text-xl font-extrabold text-foreground">{valor}</p>
    </div>
  );
}

function InformacaoDoPainel({
  icone,
  rotulo,
  valor,
}: {
  icone: React.ReactNode;
  rotulo: string;
  valor: string | null;
}) {
  if (!valor) return null;
  return (
    <div className="flex items-center gap-2.5 text-primary" title={rotulo}>
      {icone}
      <p className="text-sm text-foreground">{valor}</p>
    </div>
  );
}

function SecaoColapsavel({
  icone,
  titulo,
  contagem,
  abertaPorPadrao,
  children,
}: {
  icone: React.ReactNode;
  titulo: string;
  contagem?: number;
  abertaPorPadrao?: boolean;
  children: React.ReactNode;
}) {
  const [aberta, setAberta] = useState(Boolean(abertaPorPadrao));
  return (
    <div>
      <button
        type="button"
        onClick={() => setAberta((atual) => !atual)}
        className="flex w-full items-center gap-2.5 rounded-lg border border-border p-2.5 text-left hover:bg-muted"
      >
        {icone}
        <span className="flex-1 text-sm font-semibold text-foreground">
          {titulo}
        </span>
        {contagem !== undefined && (
          <span className="rounded-full bg-muted px-2 py-0.5 text-xs font-bold text-muted-foreground">
            {contagem}
          </span>
        )}
        {aberta ? (
          <ChevronUp className="size-4 text-muted-foreground" />
        ) : (
          <ChevronDown className="size-4 text-muted-foreground" />
        )}
      </button>
      {aberta && <div className="mt-2">{children}</div>}
    </div>
  );
}

function SecaoDeProgramadas({
  leadId,
  leadNome,
  titulo,
  vazio,
}: {
  leadId: string;
  leadNome: string;
  titulo: string;
  vazio: string;
}) {
  const textos = useTextos();
  const cache = useQueryClient();
  const programadas = useMensagensProgramadasDoLead(leadId);
  const [formulario, setFormulario] = useState<"novo" | MensagemProgramada | null>(null);
  const [itemParaRemover, setItemParaRemover] = useState<MensagemProgramada | null>(null);
  const [erro, setErro] = useState(false);
  const chave = ["mensagens-programadas", "lead", leadId] as const;
  const remover = useMutation({
    mutationFn: cancelarMensagemProgramada,
    onMutate: async (id) => {
      await cache.cancelQueries({ queryKey: chave });
      const anterior = cache.getQueryData<PaginaMensagensProgramadas>(chave);
      cache.setQueryData<PaginaMensagensProgramadas>(chave, (atual) =>
        atual
          ? { ...atual, mensagens: atual.mensagens.filter((item) => item.id !== id) }
          : atual,
      );
      return { anterior };
    },
    onError: (_erro, _id, contexto) => {
      if (contexto?.anterior) cache.setQueryData(chave, contexto.anterior);
      setErro(true);
    },
    onSuccess: () => {
      setErro(false);
      setItemParaRemover(null);
    },
    onSettled: () => cache.invalidateQueries({ queryKey: chave }),
  });
  const itens = (programadas.data?.mensagens ?? []).filter(
    (item) => item.status === "AGENDADA",
  );
  return (
    <SecaoColapsavel
      icone={<CalendarClock className="size-4 text-primary" />}
      titulo={titulo}
      contagem={itens.length}
    >
      <div className="space-y-2">
        <Button
          type="button"
          size="sm"
          variant="outline"
          className="w-full"
          onClick={() => setFormulario("novo")}
        >
          <Plus className="size-3.5" aria-hidden />
          {textos.atendimentos.painel.adicionar}
        </Button>
        {itens.length === 0 ? (
          <p className="p-2 text-center text-xs text-muted-foreground">{vazio}</p>
        ) : (
          <div className="space-y-1.5">
            {itens.map((item) => (
              <div
                key={item.id}
                data-slot="mensagem-programada"
                className="rounded-lg border border-primary/30 bg-primary/5 p-2.5 shadow-sm"
              >
                <p className="text-xs font-medium text-foreground">{item.conteudo}</p>
                <p className="mt-1 text-[0.7rem] text-muted-foreground">
                  {new Intl.DateTimeFormat("pt-BR", {
                    dateStyle: "short",
                    timeStyle: "short",
                  }).format(new Date(item.dataEnvio))}
                </p>
                <div className="mt-2 flex justify-end gap-1">
                  <Button
                    type="button"
                    size="icon-sm"
                    variant="ghost"
                    aria-label={`${textos.atendimentos.painel.editar} ${item.conteudo}`}
                    onClick={() => setFormulario(item)}
                  >
                    <Pencil className="size-3.5" aria-hidden />
                  </Button>
                  <Button
                    type="button"
                    size="icon-sm"
                    variant="ghost"
                    className="text-destructive hover:text-destructive"
                    aria-label={`${textos.atendimentos.painel.remover} ${item.conteudo}`}
                    onClick={() => {
                      setErro(false);
                      setItemParaRemover(item);
                    }}
                  >
                    <Trash2 className="size-3.5" aria-hidden />
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
      {formulario !== null && (
        <FormularioMensagemProgramada
          key={formulario === "novo" ? "nova" : formulario.id}
          aberto
          leadId={leadId}
          leadNome={leadNome}
          existente={formulario === "novo" ? undefined : formulario}
          onFechar={() => setFormulario(null)}
        />
      )}
      <DialogConfirmarRemocao
        aberto={Boolean(itemParaRemover)}
        item={itemParaRemover?.conteudo ?? ""}
        textos={textos.atendimentos.painel}
        processando={remover.isPending}
        erro={erro}
        onFechar={() => setItemParaRemover(null)}
        onConfirmar={() => itemParaRemover && remover.mutate(itemParaRemover.id)}
      />
    </SecaoColapsavel>
  );
}

function SecaoDeLembretes({
  leadId,
  leadNome,
  titulo,
  vazio,
}: {
  leadId: string;
  leadNome: string;
  titulo: string;
  vazio: string;
}) {
  const textos = useTextos();
  const cache = useQueryClient();
  const lembretes = useLembretesDoLead(leadId);
  const itens = lembretes.data?.lembretes ?? [];
  const [formulario, setFormulario] = useState<"novo" | Lembrete | null>(null);
  const [itemParaRemover, setItemParaRemover] = useState<Lembrete | null>(null);
  const [erro, setErro] = useState(false);
  const chave = ["lembretes", "lead", leadId] as const;
  const remover = useMutation({
    mutationFn: removerLembrete,
    onMutate: async (id) => {
      await cache.cancelQueries({ queryKey: chave });
      const anterior = cache.getQueryData<PaginaLembretes>(chave);
      cache.setQueryData<PaginaLembretes>(chave, (atual) =>
        atual
          ? { ...atual, lembretes: atual.lembretes.filter((item) => item.id !== id) }
          : atual,
      );
      return { anterior };
    },
    onError: (_erro, _id, contexto) => {
      if (contexto?.anterior) cache.setQueryData(chave, contexto.anterior);
      setErro(true);
    },
    onSuccess: () => {
      setErro(false);
      setItemParaRemover(null);
    },
    onSettled: () => cache.invalidateQueries({ queryKey: chave }),
  });
  return (
    <SecaoColapsavel
      icone={<Bell className="size-4 text-cor-atencao" />}
      titulo={titulo}
      contagem={itens.length}
    >
      <div className="space-y-2">
        <Button
          type="button"
          size="sm"
          variant="outline"
          className="w-full"
          onClick={() => setFormulario("novo")}
        >
          <Plus className="size-3.5" aria-hidden />
          {textos.atendimentos.painel.adicionar}
        </Button>
        {itens.length === 0 ? (
          <p className="p-2 text-center text-xs text-muted-foreground">{vazio}</p>
        ) : (
          <div className="space-y-1.5">
            {itens.map((item) => (
              <div
                key={item.id}
                className="rounded-lg border border-border bg-muted/30 p-2.5"
              >
                <p className="text-xs font-medium text-foreground">{item.texto}</p>
                <p className="mt-1 text-[0.7rem] text-muted-foreground">
                  {new Intl.DateTimeFormat("pt-BR", {
                    dateStyle: "short",
                    timeStyle: "short",
                  }).format(new Date(item.dataHora))}
                </p>
                <div className="mt-2 flex justify-end gap-1">
                  <Button
                    type="button"
                    size="icon-sm"
                    variant="ghost"
                    aria-label={`${textos.atendimentos.painel.editar} ${item.texto}`}
                    onClick={() => setFormulario(item)}
                  >
                    <Pencil className="size-3.5" aria-hidden />
                  </Button>
                  <Button
                    type="button"
                    size="icon-sm"
                    variant="ghost"
                    className="text-destructive hover:text-destructive"
                    aria-label={`${textos.atendimentos.painel.remover} ${item.texto}`}
                    onClick={() => {
                      setErro(false);
                      setItemParaRemover(item);
                    }}
                  >
                    <Trash2 className="size-3.5" aria-hidden />
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
      {formulario !== null && (
        <FormularioLembrete
          key={formulario === "novo" ? "novo" : formulario.id}
          aberto
          leadId={leadId}
          leadNome={leadNome}
          existente={formulario === "novo" ? undefined : formulario}
          onFechar={() => setFormulario(null)}
        />
      )}
      <DialogConfirmarRemocao
        aberto={Boolean(itemParaRemover)}
        item={itemParaRemover?.texto ?? ""}
        textos={textos.atendimentos.painel}
        processando={remover.isPending}
        erro={erro}
        onFechar={() => setItemParaRemover(null)}
        onConfirmar={() => itemParaRemover && remover.mutate(itemParaRemover.id)}
      />
    </SecaoColapsavel>
  );
}

function DialogConfirmarRemocao({
  aberto,
  item,
  textos,
  processando,
  erro,
  onFechar,
  onConfirmar,
}: {
  aberto: boolean;
  item: string;
  textos: ReturnType<typeof useTextos>["atendimentos"]["painel"];
  processando: boolean;
  erro: boolean;
  onFechar: () => void;
  onConfirmar: () => void;
}) {
  return (
    <Dialog open={aberto} onOpenChange={(novo) => !novo && onFechar()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{textos.remover}</DialogTitle>
          <DialogDescription>
            {textos.confirmarRemocao.replace("{item}", item)}
          </DialogDescription>
        </DialogHeader>
        {erro && <p role="alert" className="text-sm text-destructive">{textos.erroOperacao}</p>}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onFechar} disabled={processando}>
            {textos.cancelarRemocao}
          </Button>
          <Button type="button" variant="destructive" onClick={onConfirmar} disabled={processando}>
            {textos.remover}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
