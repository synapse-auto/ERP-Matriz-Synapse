"use client";

import Link from "next/link";
import { type ChangeEvent, type KeyboardEvent, useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import {
  Clock,
  Mic,
  Paperclip,
  Send,
  Square,
  Trash2,
  X,
  Zap,
} from "lucide-react";

import { Button, buttonVariants } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Textarea } from "@/components/ui/textarea";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { ErroDeApi } from "@/lib/api/errors";
import { janelaTextoLivreAberta } from "@/lib/atendimento/janela-24h";
import { listarTemplatesWhatsApp } from "@/lib/atendimento/api";
import { citacaoDeResposta } from "@/lib/atendimento/citacao";
import { useConfiguracaoComposer } from "@/lib/atendimento/use-configuracao-composer";
import { useEnviarMensagem } from "@/lib/atendimento/use-enviar-mensagem";
import { useEnviarMidia } from "@/lib/atendimento/use-enviar-midia";
import type { CartaoAtendimento, MensagemResposta } from "@/lib/atendimento/types";
import { useTextos } from "@/lib/config/textos-provider";
import { listarMensagensRapidas } from "@/lib/suporte/api";
import { useLead } from "@/lib/lead/use-painel-lead";
import { resolverMensagemRapida } from "@/lib/suporte/resolver-mensagem-rapida";
import { PainelEmojiComposer } from "@/components/mensagens/painel-emoji-composer";
import { FormularioMensagemProgramada } from "@/components/mensagens-programadas/formulario-mensagem-programada";
import { inserirNoCursor, posicionarCursor } from "@/lib/mensagens/inserir-no-cursor";

import { CitacaoMensagemVisual } from "./citacao-mensagem";
import { useGravadorAudio } from "./use-gravador-audio";

const TIPOS_DE_ANEXO_ACEITOS =
  "image/jpeg,image/png,image/webp,audio/*,.pdf,.doc,.docx,.xls,.xlsx,.txt";

type Props = {
  conversa: CartaoAtendimento;
  resposta?: MensagemResposta | null;
  onCancelarResposta?: () => void;
};

function tamanhoLegivel(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function duracaoLegivel(segundos: number): string {
  const minutos = Math.floor(segundos / 60);
  return `${String(minutos).padStart(2, "0")}:${String(segundos % 60).padStart(2, "0")}`;
}

/**
 * Três pontos do prompt E11/E11b: estado real de entrega (delegado a `useEnviarMensagem`/
 * `useEnviarMidia`), aviso de janela de 24h ANTES de digitar, e anexo — imagem, áudio ou
 * documento — com seleção, preview, progresso de upload e erro acionável.
 */
export function Composer({ conversa, resposta = null, onCancelarResposta }: Props) {
  const catalogo = useTextos();
  const textosAtendimentos = catalogo.atendimentos;
  const textos = textosAtendimentos.composer;
  const [texto, setTexto] = useState("");
  const [arquivo, setArquivo] = useState<File | null>(null);
  const [progresso, setProgresso] = useState<number | null>(null);
  const [agendamentoAberto, setAgendamentoAberto] = useState(false);
  const [atalhoSelecionado, setAtalhoSelecionado] = useState(0);
  const inputArquivoRef = useRef<HTMLInputElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const enviar = useEnviarMensagem();
  const enviarMidia = useEnviarMidia();
  const configuracaoComposer = useConfiguracaoComposer();
  const gravador = useGravadorAudio(configuracaoComposer.data);
  const rapidas = useQuery({
    queryKey: ["mensagens-rapidas", "minhas"],
    queryFn: () => listarMensagensRapidas(true),
  });
  const lead = useLead(conversa.leadId);
  const [variaveisPendentes, setVariaveisPendentes] = useState<string[]>([]);
  const janelaAberta = janelaTextoLivreAberta(conversa.ultimaMensagemDoLeadEm);
  const templates = useQuery({
    queryKey: ["whatsapp-templates"],
    queryFn: listarTemplatesWhatsApp,
    enabled: conversa.status !== "FINALIZADO" && !janelaAberta,
  });
  const [parametros, setParametros] = useState<Record<string, string[]>>({});
  const citacaoResposta = resposta ? citacaoDeResposta(resposta) : null;

  useEffect(() => {
    if (resposta) textareaRef.current?.focus();
  }, [resposta]);

  if (conversa.status === "FINALIZADO") {
    return (
      <div className="shrink-0 bg-background px-4 pb-4 pt-3">
        <div className="mx-auto max-w-[780px] rounded-xl border border-input bg-card p-3 text-center text-sm text-muted-foreground shadow-md">
          {textosAtendimentos.finalizar.sucesso}
        </div>
      </div>
    );
  }

  function alvoDeResposta() {
    return resposta
      ? { mensagemId: resposta.id, enviadoEm: resposta.enviadoEm }
      : undefined;
  }

  function limparAposEnvio() {
    setArquivo(null);
    setTexto("");
    setProgresso(null);
    onCancelarResposta?.();
  }

  function enviarConteudo() {
    if (variaveisPendentes.length > 0) return;
    if (arquivo) {
      const legenda = texto.trim() || undefined;
      setProgresso(0);
      enviarMidia.mutate(
        {
          atendimentoId: conversa.atendimentoId,
          leadId: conversa.leadId,
          arquivo,
          legenda,
          onProgresso: setProgresso,
          resposta: alvoDeResposta(),
          citacao: citacaoResposta,
        },
        {
          onSuccess: limparAposEnvio,
          onError: () => setProgresso(null),
        },
      );
      return;
    }
    const conteudo = texto.trim();
    if (!conteudo) {
      return;
    }
    if (!resposta) {
      setTexto("");
    }
    enviar.mutate(
      {
        atendimentoId: conversa.atendimentoId,
        leadId: conversa.leadId,
        conteudo,
        resposta: alvoDeResposta(),
        citacao: citacaoResposta,
      },
      { onSuccess: limparAposEnvio },
    );
  }

  function aoSelecionarArquivo(evento: ChangeEvent<HTMLInputElement>) {
    const selecionado = evento.target.files?.[0] ?? null;
    setArquivo(selecionado);
    evento.target.value = "";
  }

  function removerArquivo() {
    setArquivo(null);
    setProgresso(null);
  }

  function enviarGravacao() {
    if (!gravador.arquivo || gravador.erro) return;
    setProgresso(0);
    enviarMidia.mutate(
      {
        atendimentoId: conversa.atendimentoId,
        leadId: conversa.leadId,
        arquivo: gravador.arquivo,
        onProgresso: setProgresso,
      },
      {
        onSuccess: () => {
          gravador.descartar();
          setProgresso(null);
        },
        onError: () => {
          gravador.descartar();
          setProgresso(null);
        },
      },
    );
  }

  function aoPressionarTecla(evento: KeyboardEvent<HTMLTextAreaElement>) {
    if (
      sugestoes.length > 0 &&
      (evento.key === "ArrowDown" || evento.key === "ArrowUp")
    ) {
      evento.preventDefault();
      setAtalhoSelecionado((atual) =>
        evento.key === "ArrowDown"
          ? (atual + 1) % sugestoes.length
          : (atual - 1 + sugestoes.length) % sugestoes.length,
      );
      return;
    }
    if (
      sugestoes.length > 0 &&
      (evento.key === "Enter" || evento.key === "Tab")
    ) {
      evento.preventDefault();
      const escolhida = sugestoes[atalhoSelecionado];
      if (escolhida) {
        const resolvida = resolverMensagemRapida(escolhida.conteudo, { nome: lead.data?.nome ?? "", empresa: lead.data?.empresa });
        setTexto(resolvida.texto);
        setVariaveisPendentes(resolvida.pendentes);
      }
      setAtalhoSelecionado(0);
      return;
    }
    if (evento.key === "Escape" && resposta) {
      evento.preventDefault();
      onCancelarResposta?.();
      return;
    }
    if (evento.key === "Enter" && !evento.shiftKey) {
      evento.preventDefault();
      enviarConteudo();
    }
  }

  const erroDeTexto =
    enviar.error instanceof ErroDeApi
      ? resposta
        ? (enviar.error.problema?.detail ?? textos.respostaErro)
        : enviar.error.message
      : enviar.isError
        ? textosAtendimentos.mensagem.status.falhou
        : null;
  const erroDeMidia =
    enviarMidia.error instanceof ErroDeApi
      ? enviarMidia.error.message
      : enviarMidia.isError
        ? textos.anexoErro
        : null;
  const erroDeGravacao =
    gravador.erro === "SEM_MICROFONE"
      ? textos.audioSemMicrofone
      : gravador.erro === "PERMISSAO"
        ? textos.audioPermissaoNegada
        : gravador.erro === "EM_USO"
          ? textos.audioMicrofoneEmUso
          : gravador.erro === "CAPTURA"
            ? textos.audioErroCaptura
            : gravador.erro === "TAMANHO"
              ? textos.audioExcedeuLimite
              : null;
  const mensagemDeErro = erroDeTexto ?? erroDeMidia ?? erroDeGravacao;
  const termoAtalho =
    texto.startsWith("/") && !texto.includes(" ")
      ? texto.slice(1).toLowerCase()
      : null;
  const sugestoes =
    termoAtalho === null
      ? []
      : (rapidas.data ?? []).filter((m) =>
          m.palavraChave.toLowerCase().includes(termoAtalho),
        );

  if (!janelaAberta) {
    const aprovados = (templates.data ?? []).filter((item) => item.status === "APROVADO");
    return (
      <div className="min-h-0 overflow-y-auto bg-background px-4 pb-4 pt-3">
        <div className="mx-auto max-w-[780px] space-y-3 rounded-xl border border-input bg-card p-3 shadow-md">
          <p className="text-sm font-medium text-foreground">
            {textos.janelaFechadaTitulo}
          </p>
          <p className="text-sm text-muted-foreground">
            {textos.janelaFechadaDescricao}
          </p>
          {templates.isError ? (
            <p className="text-xs text-destructive">{textos.templatesErro}</p>
          ) : templates.isLoading ? (
            <p className="text-xs text-muted-foreground">{textos.semTemplates}</p>
          ) : aprovados.length === 0 ? (
            <p className="text-xs text-muted-foreground">{textos.semTemplates}</p>
          ) : (
            <ul className="max-h-48 space-y-2 overflow-y-auto">
              {aprovados.map((template) => {
                const chave = `${template.nome}:${template.idioma}`;
                const valores = parametros[chave] ?? Array(template.quantidadeDeParametros).fill("");
                return (
                  <li key={chave} className="rounded-lg border border-border p-2">
                    <p className="text-sm font-medium">{template.nome}</p>
                    <p className="mt-1 text-xs text-muted-foreground">{template.corpo}</p>
                    {template.quantidadeDeParametros > 0 && (
                      <div className="mt-2 space-y-1">
                        {valores.map((valor, indice) => (
                          <input
                            key={`${chave}-${indice}`}
                            className="w-full rounded-md border border-input bg-background px-2 py-1 text-sm"
                            value={valor}
                            placeholder={textos.parametroTemplate.replace(
                              "{indice}",
                              String(indice + 1),
                            )}
                            onChange={(evento) => {
                              const proximo = [...valores];
                              proximo[indice] = evento.target.value;
                              setParametros((atual) => ({ ...atual, [chave]: proximo }));
                            }}
                          />
                        ))}
                      </div>
                    )}
                    <Button
                      type="button"
                      size="sm"
                      className="mt-2"
                      disabled={
                        enviar.isPending
                        || (template.quantidadeDeParametros > 0
                          && valores.some((valor) => valor.trim() === ""))
                      }
                      onClick={() =>
                        enviar.mutate({
                          atendimentoId: conversa.atendimentoId,
                          leadId: conversa.leadId,
                          conteudo: template.corpo,
                          template: {
                            nome: template.nome,
                            idioma: template.idioma,
                            parametros: valores,
                          },
                        })
                      }
                    >
                      {textos.enviarTemplate}
                    </Button>
                  </li>
                );
              })}
            </ul>
          )}
          <Link
            href="/templates-whatsapp"
            className="inline-flex text-xs font-medium text-primary underline-offset-4 hover:underline"
          >
            {textos.criarTemplate}
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="shrink-0 bg-background px-4 pb-4 pt-3">
      <div className="relative mx-auto max-w-[780px] rounded-xl border border-input bg-card p-3 shadow-md">
        {citacaoResposta && (
          <div className="mb-2 flex items-start gap-2 rounded-md border border-border bg-muted/50 px-2 py-1.5">
            <div className="min-w-0 flex-1 text-muted-foreground">
              <CitacaoMensagemVisual citacao={citacaoResposta} textos={textosAtendimentos.mensagem.citacao} />
            </div>
            <button
              type="button"
              className="shrink-0 rounded p-0.5 text-muted-foreground hover:bg-destructive/10 hover:text-destructive focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-destructive"
              aria-label={textosAtendimentos.mensagem.citacao.cancelar}
              onClick={() => onCancelarResposta?.()}
            >
              <X className="size-3.5" />
            </button>
          </div>
        )}

        {arquivo && (
          <div className="mb-2 flex items-center gap-2 rounded-md border border-border bg-muted/50 px-2 py-1 text-sm">
            <Paperclip
              className="size-4 shrink-0 text-muted-foreground"
              aria-hidden
            />
            <span className="flex-1 truncate">{arquivo.name}</span>
            <span className="shrink-0 text-xs text-muted-foreground">
              {tamanhoLegivel(arquivo.size)}
            </span>
            {progresso !== null ? (
              <span className="shrink-0 text-xs text-muted-foreground">
                {progresso}%
              </span>
            ) : (
              <button
                type="button"
                className="shrink-0 rounded p-0.5 hover:bg-destructive/10 hover:text-destructive focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-destructive"
                aria-label={textos.anexoRemover}
                onClick={removerArquivo}
              >
                <X className="size-3.5" />
              </button>
            )}
          </div>
        )}

        {gravador.fase === "GRAVANDO" && (
          <div className="mb-2 flex items-center gap-2 rounded-md border border-border bg-muted/50 p-2">
            <Mic
              className="size-4 animate-pulse text-destructive"
              aria-hidden
            />
            <span className="flex-1 text-sm">
              {textos.audioGravando} · {duracaoLegivel(gravador.segundos)}
            </span>
            <Button
              type="button"
              size="icon-sm"
              variant="ghost"
              className="hover:bg-destructive/10 hover:text-destructive focus-visible:ring-destructive"
              onClick={gravador.descartar}
              aria-label={textos.audioDescartar}
            >
              <Trash2 className="size-4" aria-hidden />
            </Button>
            <Button
              type="button"
              size="icon-sm"
              onClick={gravador.parar}
              aria-label={textos.audioParar}
            >
              <Square className="size-3.5 fill-current" aria-hidden />
            </Button>
          </div>
        )}

        {gravador.fase === "PREVISUALIZACAO" && gravador.previewUrl && (
          <div className="mb-2 flex flex-wrap items-center gap-2 rounded-md border border-border bg-muted/50 p-2">
            <audio
              className="h-9 min-w-0 flex-1"
              controls
              src={gravador.previewUrl}
              aria-label={textos.audioPreview}
            />
            {progresso !== null && (
              <span className="text-xs text-muted-foreground">
                {progresso}%
              </span>
            )}
            <Button
              type="button"
              size="icon-sm"
              variant="ghost"
              className="hover:bg-destructive/10 hover:text-destructive focus-visible:ring-destructive"
              onClick={gravador.descartar}
              disabled={enviarMidia.isPending}
              aria-label={textos.audioDescartar}
            >
              <Trash2 className="size-4" aria-hidden />
            </Button>
          </div>
        )}

        {gravador.limiteAtingido && gravador.fase === "PREVISUALIZACAO" && (
          <p className="mb-2 text-xs text-muted-foreground" role="status">
            {textos.audioLimiteDuracao}
          </p>
        )}

        <div className="flex items-end gap-2">
          <div className="flex shrink-0 items-center gap-1">
            <input
              ref={inputArquivoRef}
              type="file"
              accept={TIPOS_DE_ANEXO_ACEITOS}
              className="hidden"
              onChange={aoSelecionarArquivo}
              disabled={gravador.fase !== "INATIVO"}
            />
            <Tooltip>
              <TooltipTrigger
                className={buttonVariants({ variant: "ghost", size: "icon" })}
                aria-label={textos.anexo}
                onClick={() => inputArquivoRef.current?.click()}
                disabled={gravador.fase !== "INATIVO"}
              >
                <Paperclip className="size-4" />
              </TooltipTrigger>
              <TooltipContent>{textos.anexoSelecionar}</TooltipContent>
            </Tooltip>

            {rapidas.data && rapidas.data.length > 0 && (
              <Popover>
                <PopoverTrigger
                  className={buttonVariants({ variant: "ghost", size: "icon" })}
                  aria-label={textos.mensagensRapidas}
                  disabled={gravador.fase !== "INATIVO"}
                >
                  <Zap className="size-4" />
                </PopoverTrigger>
                <PopoverContent
                  side="top"
                  align="start"
                  className="max-h-60 w-72 overflow-y-auto p-1"
                >
                  <ul role="listbox" aria-label={textos.mensagensRapidas}>
                    {rapidas.data.map((mensagem) => (
                      <li key={mensagem.id}>
                        <button
                          type="button"
                          className="w-full rounded-md p-2 text-left outline-none hover:bg-accent focus-visible:bg-accent"
                        onClick={() => {
                          const resolvida = resolverMensagemRapida(mensagem.conteudo, { nome: lead.data?.nome ?? "", empresa: lead.data?.empresa });
                          setTexto(resolvida.texto);
                          setVariaveisPendentes(resolvida.pendentes);
                        }}
                        >
                          <span className="block truncate font-mono text-xs text-primary">
                            /{mensagem.palavraChave}
                          </span>
                          <span className="block truncate text-sm">
                            {mensagem.conteudo}
                          </span>
                        </button>
                      </li>
                    ))}
                  </ul>
                </PopoverContent>
              </Popover>
            )}

            <Tooltip>
              <TooltipTrigger
                className={buttonVariants({ variant: "ghost", size: "icon" })}
                aria-label={textos.agendar}
                onClick={() => setAgendamentoAberto(true)}
                disabled={gravador.fase !== "INATIVO"}
              >
                <Clock className="size-4" />
              </TooltipTrigger>
              <TooltipContent>{textos.agendar}</TooltipContent>
            </Tooltip>

            <PainelEmojiComposer
              rotulo={textos.emoji}
              i18n={textosAtendimentos.mensagem.acoes.seletor}
              disabled={gravador.fase !== "INATIVO"}
              onEscolher={(emoji) => {
                const campo = textareaRef.current;
                setTexto((atual) => {
                  const { texto, cursor } = inserirNoCursor(atual, emoji, campo);
                  requestAnimationFrame(() => posicionarCursor(textareaRef.current, cursor));
                  return texto;
                });
                setVariaveisPendentes([]);
              }}
            />
          </div>

          {gravador.disponivel && gravador.fase === "INATIVO" && !arquivo && (
            <div className="order-last shrink-0">
              <Tooltip>
                <TooltipTrigger
                  className={buttonVariants({ variant: "ghost", size: "icon" })}
                  aria-label={textos.audioGravar}
                  onClick={gravador.iniciar}
                  disabled={enviarMidia.isPending}
                >
                  <Mic className="size-4" />
                </TooltipTrigger>
                <TooltipContent>{textos.audioGravar}</TooltipContent>
              </Tooltip>
            </div>
          )}

          <div className="relative min-w-0 flex-1">
            <Textarea
              ref={textareaRef}
              value={texto}
              onChange={(evento) => {
                setTexto(evento.target.value);
                setVariaveisPendentes([]);
                setAtalhoSelecionado(0);
              }}
              onKeyDown={aoPressionarTecla}
              placeholder={
                arquivo ? textos.anexoLegendaPlaceholder : textos.placeholder
              }
              rows={1}
              className="min-h-11 max-h-32 w-full min-w-0 resize-none break-words border-0 bg-transparent px-2 py-2 shadow-none focus-visible:border-0 focus-visible:ring-2"
              disabled={gravador.fase !== "INATIVO"}
            />
            {sugestoes.length > 0 && (
              <ul
                role="listbox"
                aria-label={textos.mensagensRapidas}
                className="absolute bottom-full z-40 mb-2 max-h-48 w-full overflow-y-auto rounded-lg border border-border bg-popover p-1 shadow-lg"
              >
                {sugestoes.map((m, indice) => (
                  <li key={m.id}>
                    <button
                      type="button"
                      className={
                        indice === atalhoSelecionado
                          ? "w-full rounded bg-accent p-2 text-left"
                          : "w-full rounded p-2 text-left hover:bg-accent"
                      }
                      onMouseDown={(e) => e.preventDefault()}
                      onClick={() => {
                        const resolvida = resolverMensagemRapida(m.conteudo, { nome: lead.data?.nome ?? "", empresa: lead.data?.empresa });
                        setTexto(resolvida.texto);
                        setVariaveisPendentes(resolvida.pendentes);
                        setAtalhoSelecionado(0);
                      }}
                    >
                      <span className="font-mono text-xs text-primary">
                        /{m.palavraChave}
                      </span>
                      <span className="block truncate text-sm">
                        {m.conteudo}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="order-last flex shrink-0 items-center gap-1">
            <Button
              type="button"
              size="icon"
              onClick={() => {
                if (gravador.fase === "PREVISUALIZACAO") enviarGravacao();
                else enviarConteudo();
              }}
              disabled={
                enviar.isPending
                || enviarMidia.isPending
                || (gravador.fase === "PREVISUALIZACAO"
                  ? Boolean(gravador.erro) || !gravador.arquivo
                  : gravador.fase !== "INATIVO" || (!texto.trim() && !arquivo) || variaveisPendentes.length > 0)
              }
              aria-label={textos.enviar}
            >
              <Send className="size-4" />
            </Button>
          </div>
        </div>

        {mensagemDeErro && (
          <p className="mt-1 text-xs text-destructive" role="alert">
            {mensagemDeErro}
          </p>
        )}
        {variaveisPendentes.length > 0 && (
          <p className="mt-1 text-xs text-destructive" role="alert">
            {catalogo.mensagensRapidas.variaveisPendentes.replace("{variaveis}", variaveisPendentes.map((item) => `{${item}}`).join(", "))}
          </p>
        )}
        <FormularioMensagemProgramada
          key={agendamentoAberto ? "agendamento-aberto" : "agendamento-fechado"}
          aberto={agendamentoAberto}
          leadId={conversa.leadId}
          leadNome={conversa.leadNome}
          conteudoInicial={texto}
          onFechar={() => setAgendamentoAberto(false)}
          onSalvo={() => setTexto("")}
        />
      </div>
    </div>
  );
}
