"use client";

import { type ChangeEvent, type KeyboardEvent, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import {
  Clock,
  Mic,
  Paperclip,
  Send,
  Smile,
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
import { useConfiguracaoComposer } from "@/lib/atendimento/use-configuracao-composer";
import { useEnviarMensagem } from "@/lib/atendimento/use-enviar-mensagem";
import { useEnviarMidia } from "@/lib/atendimento/use-enviar-midia";
import type { CartaoAtendimento } from "@/lib/atendimento/types";
import { useTextos } from "@/lib/config/textos-provider";
import { listarMensagensRapidas } from "@/lib/suporte/api";
import { FormularioMensagemProgramada } from "@/components/mensagens-programadas/formulario-mensagem-programada";

import { useGravadorAudio } from "./use-gravador-audio";

const EMOJIS = ["😀", "😂", "😍", "👍", "🙏", "🎉", "😢", "😡", "👀", "✅"];

const TIPOS_DE_ANEXO_ACEITOS =
  "image/jpeg,image/png,image/webp,audio/*,.pdf,.doc,.docx,.xls,.xlsx,.txt";

type Props = {
  conversa: CartaoAtendimento;
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
export function Composer({ conversa }: Props) {
  const textosAtendimentos = useTextos().atendimentos;
  const textos = textosAtendimentos.composer;
  const [texto, setTexto] = useState("");
  const [arquivo, setArquivo] = useState<File | null>(null);
  const [progresso, setProgresso] = useState<number | null>(null);
  const [agendamentoAberto, setAgendamentoAberto] = useState(false);
  const [atalhoSelecionado, setAtalhoSelecionado] = useState(0);
  const inputArquivoRef = useRef<HTMLInputElement>(null);
  const enviar = useEnviarMensagem();
  const enviarMidia = useEnviarMidia();
  const configuracaoComposer = useConfiguracaoComposer();
  const gravador = useGravadorAudio(configuracaoComposer.data);
  const rapidas = useQuery({
    queryKey: ["mensagens-rapidas", "minhas"],
    queryFn: () => listarMensagensRapidas(true),
  });

  if (conversa.status === "FINALIZADO") {
    return (
      <div className="bg-background px-4 pb-4 pt-3">
        <div className="mx-auto max-w-[780px] rounded-xl border border-input bg-card p-3 text-center text-sm text-muted-foreground shadow-md">
          {textosAtendimentos.finalizar.sucesso}
        </div>
      </div>
    );
  }

  const janelaAberta = janelaTextoLivreAberta(conversa.ultimaMensagemDoLeadEm);

  function enviarConteudo() {
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
        },
        {
          onSuccess: () => {
            setArquivo(null);
            setTexto("");
            setProgresso(null);
          },
          onError: () => setProgresso(null),
        },
      );
      return;
    }
    const conteudo = texto.trim();
    if (!conteudo) {
      return;
    }
    setTexto("");
    enviar.mutate({
      atendimentoId: conversa.atendimentoId,
      leadId: conversa.leadId,
      conteudo,
    });
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
      setTexto(sugestoes[atalhoSelecionado]?.conteudo ?? texto);
      setAtalhoSelecionado(0);
      return;
    }
    if (evento.key === "Enter" && !evento.shiftKey) {
      evento.preventDefault();
      enviarConteudo();
    }
  }

  const erroDeTexto =
    enviar.error instanceof ErroDeApi
      ? enviar.error.message
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
    return (
      <div className="bg-background px-4 pb-4 pt-3">
        <div className="mx-auto max-w-[780px] space-y-1 rounded-xl border border-input bg-card p-3 shadow-md">
          <p className="text-sm font-medium text-foreground">
            {textos.janelaFechadaTitulo}
          </p>
          <p className="text-sm text-muted-foreground">
            {textos.janelaFechadaDescricao}
          </p>
          <p className="text-xs text-muted-foreground">{textos.semTemplates}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-background px-4 pb-4 pt-3">
      <div className="relative mx-auto max-w-[780px] rounded-xl border border-input bg-card p-3 shadow-md">
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
                className="shrink-0 rounded p-0.5 hover:bg-muted"
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
              onClick={gravador.descartar}
              disabled={enviarMidia.isPending}
              aria-label={textos.audioDescartar}
            >
              <Trash2 className="size-4" aria-hidden />
            </Button>
            <Button
              type="button"
              size="icon-sm"
              onClick={enviarGravacao}
              disabled={Boolean(gravador.erro) || enviarMidia.isPending}
              aria-label={textos.audioEnviar}
            >
              <Send className="size-4" aria-hidden />
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
                          onClick={() => setTexto(mensagem.conteudo)}
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

            <Popover>
              <PopoverTrigger
                className={buttonVariants({ variant: "ghost", size: "icon" })}
                aria-label={textos.emoji}
                disabled={gravador.fase !== "INATIVO"}
              >
                <Smile className="size-4" />
              </PopoverTrigger>
              <PopoverContent
                side="top"
                align="start"
                className="grid w-auto grid-cols-5 gap-1"
              >
                {EMOJIS.map((emoji) => (
                  <button
                    type="button"
                    key={emoji}
                    className="rounded p-1 text-lg hover:bg-muted"
                    onClick={() => setTexto((atual) => atual + emoji)}
                  >
                    {emoji}
                  </button>
                ))}
              </PopoverContent>
            </Popover>
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
              value={texto}
              onChange={(evento) => {
                setTexto(evento.target.value);
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
                        setTexto(m.conteudo);
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
              onClick={enviarConteudo}
              disabled={
                gravador.fase !== "INATIVO" ||
                (!texto.trim() && !arquivo) ||
                enviar.isPending ||
                enviarMidia.isPending
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
