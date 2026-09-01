"use client";

import { type ChangeEvent, type ClipboardEvent, type KeyboardEvent, type Ref, useEffect, useImperativeHandle, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import {
  Clock,
  File,
  LayoutTemplate,
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
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
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
import { estadoDaJanelaTextoLivre } from "@/lib/atendimento/janela-24h";
import { listarTemplatesWhatsApp } from "@/lib/atendimento/api";
import { arquivosDaAreaDeTransferencia, filtrarArquivos, TIPOS_DE_ANEXO_ACEITOS } from "@/lib/atendimento/arquivos-do-composer";
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
import { ListaTemplatesWhatsApp } from "./lista-templates-whatsapp";
import { useGravadorAudio } from "./use-gravador-audio";

type Props = {
  conversa: CartaoAtendimento;
  resposta?: MensagemResposta | null;
  onCancelarResposta?: () => void;
  ref?: Ref<ComposerHandle>;
};

export type ComposerHandle = {
  adicionarArquivos: (novos: File[]) => void;
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
export function Composer({
  conversa,
  resposta = null,
  onCancelarResposta,
  ref,
}: Props) {
  const catalogo = useTextos();
  const textosAtendimentos = catalogo.atendimentos;
  const textos = textosAtendimentos.composer;
  const [texto, setTexto] = useState("");
  const [arquivos, setArquivos] = useState<File[]>([]);
  const [avisoTipo, setAvisoTipo] = useState(false);
  const [progresso, setProgresso] = useState<number | null>(null);
  const [indiceEnvio, setIndiceEnvio] = useState<number | null>(null);
  const [agendamentoAberto, setAgendamentoAberto] = useState(false);
  const [painelTemplateAberto, setPainelTemplateAberto] = useState(false);
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
  const estadoDaJanela = estadoDaJanelaTextoLivre(conversa.ultimaMensagemDoLeadEm);
  const janelaAberta = estadoDaJanela === "aberta";
  const templates = useQuery({
    queryKey: ["whatsapp-templates"],
    queryFn: listarTemplatesWhatsApp,
    enabled: conversa.status !== "FINALIZADO" && (!janelaAberta || painelTemplateAberto),
  });
  const [parametros, setParametros] = useState<Record<string, string[]>>({});
  const citacaoResposta = resposta ? citacaoDeResposta(resposta) : null;

  function adicionarArquivos(novos: File[]) {
    if (!janelaAberta || gravador.fase !== "INATIVO" || enviarMidia.isPending) return;
    const { aceitos, rejeitados } = filtrarArquivos(novos, TIPOS_DE_ANEXO_ACEITOS);
    if (aceitos.length > 0) {
      setArquivos((atual) => [...atual, ...aceitos]);
    }
    setAvisoTipo(rejeitados.length > 0);
  }

  useImperativeHandle(ref, () => ({ adicionarArquivos }));

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
    setArquivos([]);
    setTexto("");
    setProgresso(null);
    setIndiceEnvio(null);
    setAvisoTipo(false);
    onCancelarResposta?.();
  }

  async function enviarConteudo() {
    if (variaveisPendentes.length > 0) return;
    if (arquivos.length > 0) {
      const fila = arquivos;
      const legenda = texto.trim() || undefined;
      const respostaAlvo = alvoDeResposta();
      let indice = 0;
      try {
        for (; indice < fila.length; indice++) {
          setIndiceEnvio(indice);
          setProgresso(0);
          await enviarMidia.mutateAsync({
            atendimentoId: conversa.atendimentoId,
            leadId: conversa.leadId,
            arquivo: fila[indice],
            legenda: indice === 0 ? legenda : undefined,
            onProgresso: setProgresso,
            resposta: indice === 0 ? respostaAlvo : undefined,
            citacao: indice === 0 ? citacaoResposta : undefined,
          });
          if (indice === 0) {
            setTexto("");
            onCancelarResposta?.();
          }
        }
        limparAposEnvio();
      } catch {
        setArquivos((atual) => atual.slice(indice));
        setProgresso(null);
        setIndiceEnvio(null);
      }
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
    adicionarArquivos(Array.from(evento.target.files ?? []));
    evento.target.value = "";
  }

  function aoColar(evento: ClipboardEvent<HTMLTextAreaElement>) {
    const arquivosColados = arquivosDaAreaDeTransferencia(evento.clipboardData);
    if (arquivosColados.length === 0) return;
    evento.preventDefault();
    adicionarArquivos(arquivosColados);
  }

  function removerArquivo(indice: number) {
    setArquivos((atual) => atual.filter((_, item) => item !== indice));
    setProgresso(null);
    setIndiceEnvio(null);
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
  const mensagemDeErro =
    erroDeTexto ?? erroDeMidia ?? erroDeGravacao ?? (avisoTipo ? textos.anexoTipoNaoPermitido : null);
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
    const semJanela = estadoDaJanela === "inexistente";
    return (
      <div className="min-h-0 overflow-y-auto bg-background px-4 pb-4 pt-3">
        <div className="mx-auto max-w-[780px] space-y-4 rounded-xl border border-border bg-card p-4 shadow-md">
          <div className="space-y-1">
            <p className="text-sm font-medium text-foreground">
              {semJanela ? textos.janelaInexistenteTitulo : textos.janelaFechadaTitulo}
            </p>
            <p className="text-sm text-muted-foreground">
              {semJanela ? textos.janelaInexistenteDescricao : textos.janelaFechadaDescricao}
            </p>
          </div>
          <ListaTemplatesWhatsApp
            textos={textos}
            rotulosDeCategoria={catalogo.templatesWhatsApp.categorias}
            templates={templates}
            parametros={parametros}
            onParametros={(chave, valores) =>
              setParametros((atual) => ({ ...atual, [chave]: valores }))
            }
            enviando={enviar.isPending}
            onEnviar={(template, valores) =>
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
          />
        </div>
      </div>
    );
  }

  return (
    <div className="shrink-0 bg-background px-4 pb-4 pt-3">
      <div className="relative mx-auto max-w-[780px]">
        <p
          className="mb-1.5 flex items-center gap-1.5 px-1 text-xs text-muted-foreground"
        >
          <span className="size-1.5 shrink-0 rounded-full bg-muted-foreground/50" aria-hidden />
          {textos.janelaAberta}
        </p>
        <div className="rounded-xl border border-input bg-card p-3 shadow-md">
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
              <X className="size-[calc(var(--tamanho-icone-interface)*0.875)]" />
            </button>
          </div>
        )}

        {arquivos.length > 0 && (
          <div className="mb-2 space-y-1">
            {indiceEnvio !== null && arquivos.length > 1 && (
              <p className="text-xs text-muted-foreground" role="status">
                {textos.anexoEnviandoLote
                  .replace("{atual}", String(indiceEnvio + 1))
                  .replace("{total}", String(arquivos.length))}
              </p>
            )}
            {arquivos.map((item, indice) => (
              <div
                key={`${item.name}-${item.size}-${indice}`}
                className="flex items-center gap-2 rounded-md border border-border bg-muted/50 px-2 py-1 text-sm"
              >
                <Paperclip
                  className="size-(--tamanho-icone-interface) shrink-0 text-muted-foreground"
                  aria-hidden
                />
                <span className="flex-1 truncate">{item.name}</span>
                <span className="shrink-0 text-xs text-muted-foreground">
                  {tamanhoLegivel(item.size)}
                </span>
                {enviarMidia.isPending && indiceEnvio === indice && progresso !== null ? (
                  <span className="shrink-0 text-xs text-muted-foreground">
                    {progresso}%
                  </span>
                ) : (
                  <button
                    type="button"
                    className="shrink-0 rounded p-0.5 hover:bg-destructive/10 hover:text-destructive focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-destructive"
                    aria-label={textos.anexoRemover}
                    disabled={enviarMidia.isPending}
                    onClick={() => removerArquivo(indice)}
                  >
                    <X className="size-[calc(var(--tamanho-icone-interface)*0.875)]" />
                  </button>
                )}
              </div>
            ))}
          </div>
        )}

        {gravador.fase === "GRAVANDO" && (
          <div className="mb-2 flex items-center gap-2 rounded-md border border-border bg-muted/50 p-2">
            <Mic
              className="size-(--tamanho-icone-interface) animate-pulse text-destructive"
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
              <Trash2 className="size-(--tamanho-icone-interface)" aria-hidden />
            </Button>
            <Button
              type="button"
              size="icon-sm"
              onClick={gravador.parar}
              aria-label={textos.audioParar}
            >
              <Square className="size-[calc(var(--tamanho-icone-interface)*0.875)] fill-current" aria-hidden />
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
              <Trash2 className="size-(--tamanho-icone-interface)" aria-hidden />
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
              multiple
              className="hidden"
              onChange={aoSelecionarArquivo}
              disabled={gravador.fase !== "INATIVO" || enviarMidia.isPending}
            />
            <DropdownMenu>
              <DropdownMenuTrigger
                className={buttonVariants({ variant: "ghost", size: "icon" })}
                aria-label={textos.anexo}
                disabled={gravador.fase !== "INATIVO"}
              >
                <Paperclip className="size-(--tamanho-icone-interface)" />
              </DropdownMenuTrigger>
              <DropdownMenuContent side="top" align="start" className="min-w-40 w-auto">
                <DropdownMenuItem
                  onClick={() => {
                    requestAnimationFrame(() => inputArquivoRef.current?.click());
                  }}
                >
                  <File className="size-(--tamanho-icone-interface)" aria-hidden />
                  {textos.anexoMenuArquivos}
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => setPainelTemplateAberto(true)}>
                  <LayoutTemplate className="size-(--tamanho-icone-interface)" aria-hidden />
                  {textos.anexoMenuTemplates}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>

            {rapidas.data && rapidas.data.length > 0 && (
              <Popover>
                <PopoverTrigger
                  className={buttonVariants({ variant: "ghost", size: "icon" })}
                  aria-label={textos.mensagensRapidas}
                  disabled={gravador.fase !== "INATIVO"}
                >
                  <Zap className="size-(--tamanho-icone-interface)" />
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
                <Clock className="size-(--tamanho-icone-interface)" />
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

          {gravador.disponivel && gravador.fase === "INATIVO" && arquivos.length === 0 && (
            <div className="order-last shrink-0">
              <Tooltip>
                <TooltipTrigger
                  className={buttonVariants({ variant: "ghost", size: "icon" })}
                  aria-label={textos.audioGravar}
                  onClick={gravador.iniciar}
                  disabled={enviarMidia.isPending}
                >
                  <Mic className="size-(--tamanho-icone-interface)" />
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
              onPaste={aoColar}
              onKeyDown={aoPressionarTecla}
              placeholder={
                arquivos.length > 0 ? textos.anexoLegendaPlaceholder : textos.placeholder
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
                  : gravador.fase !== "INATIVO" || (!texto.trim() && arquivos.length === 0) || variaveisPendentes.length > 0)
              }
              aria-label={textos.enviar}
            >
              <Send className="size-(--tamanho-icone-interface)" />
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
        <Dialog open={painelTemplateAberto} onOpenChange={setPainelTemplateAberto}>
          <DialogContent className="sm:max-w-lg">
            <DialogHeader>
              <DialogTitle>{textos.escolherTemplate}</DialogTitle>
            </DialogHeader>
            <ListaTemplatesWhatsApp
              textos={textos}
              rotulosDeCategoria={catalogo.templatesWhatsApp.categorias}
              templates={templates}
              parametros={parametros}
              onParametros={(chave, valores) =>
                setParametros((atual) => ({ ...atual, [chave]: valores }))
              }
              enviando={enviar.isPending}
              onEnviar={(template, valores) => {
                enviar.mutate({
                  atendimentoId: conversa.atendimentoId,
                  leadId: conversa.leadId,
                  conteudo: template.corpo,
                  template: {
                    nome: template.nome,
                    idioma: template.idioma,
                    parametros: valores,
                  },
                });
                setPainelTemplateAberto(false);
              }}
            />
          </DialogContent>
        </Dialog>
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
    </div>
  );
}
