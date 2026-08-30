"use client";

import { useState } from "react";
import { useRef, type ChangeEvent, type KeyboardEvent } from "react";
import { Mic, Paperclip, Send, Square, Trash2, Users, X, Download, FileText } from "lucide-react";
import { PainelEmojiComposer } from "@/components/mensagens/painel-emoji-composer";
import { inserirNoCursor, posicionarCursor } from "@/lib/mensagens/inserir-no-cursor";
import { urlSegura, cn } from "@/lib/utils";
import { useTextos } from "@/lib/config/textos-provider";
import { useConfiguracaoComposer } from "@/lib/atendimento/use-configuracao-composer";
import { useGravadorAudio } from "@/components/atendimentos/use-gravador-audio";
import { PlayerAudio } from "@/components/atendimentos/player-audio";


import type { Textos } from "@/lib/config/schema";
import type { ChatConversa, ChatMensagem } from "@/lib/chat-interno/types";
import { InteracaoMensagem } from "@/components/mensagens/interacao-mensagem";
import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";

type TextosChat = Textos["chatInterno"];
export const TIPOS_DE_ANEXO_ACEITOS = "image/jpeg,image/png,image/webp,audio/*,.pdf,.doc,.docx,.xls,.xlsx,.txt";

export function tamanhoLegivel(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function duracaoLegivel(segundos: number): string {
  const minutos = Math.floor(segundos / 60);
  return `${String(minutos).padStart(2, "0")}:${String(segundos % 60).padStart(2, "0")}`;
}


export function CabecalhoChatInterno({ conversa, textos }: { conversa?: ChatConversa; textos: TextosChat }) {
  const nome = conversa?.participantes?.trim() || textos.titulo;
  return (
    <header className="flex h-[72px] shrink-0 items-center gap-3 border-b border-border bg-background px-5">
      <AvatarIniciais id={conversa?.id ?? "chat-interno"} nome={nome} fotoUrl={conversa?.fotoUrl} className="flex size-10 shrink-0 items-center justify-center rounded-xl text-xs font-bold text-white" />
      <div className="min-w-0">
        <h2 className="flex items-center gap-2 truncate font-semibold text-foreground">
          <Users className="size-4 shrink-0 text-muted-foreground" aria-hidden />
          <span className="truncate">{nome}</span>
        </h2>
        <p className="text-xs text-muted-foreground">{textos.titulo}</p>
      </div>
    </header>
  );
}

export function ListaMensagensChatInterno({
  mensagens,
  usuarioAtual,
  textos,
  onDefinirReacao,
  onRemoverReacao,
}: {
  mensagens: ChatMensagem[];
  usuarioAtual: string | null;
  textos: TextosChat;
  onDefinirReacao: (mensagem: ChatMensagem, emoji: string) => Promise<void>;
  onRemoverReacao: (mensagem: ChatMensagem) => Promise<void>;
}) {
  const catalogoAtendimentos = useTextos().atendimentos;
  const textosAtendimentos = catalogoAtendimentos.media;
  const acoes = catalogoAtendimentos.mensagem.acoes;
  if (!mensagens.length) return <p className="flex flex-1 items-center justify-center text-sm text-muted-foreground">{textos.semMensagens}</p>;
  return (
    <div
      className="min-h-0 flex-1 space-y-3 overflow-y-auto overscroll-contain bg-muted/20 p-5"
      data-slot="historico-chat-interno"
    >
      {mensagens.map((mensagem) => {
        const propria = mensagem.remetenteId === usuarioAtual;
        const tipo = mensagem.tipo ?? "TEXTO";
        const midiaUrl = urlSegura(mensagem.midiaUrl ?? null);
        const metadados = (typeof mensagem.midiaMetadados === "string" ? (() => { try { return JSON.parse(mensagem.midiaMetadados as string); } catch { return {}; } })() : (mensagem.midiaMetadados ?? {})) as { legenda?: string; nome?: string; tamanho?: number };
        const textoCopiavel = mensagem.conteudo?.trim()
          ? mensagem.conteudo
          : typeof metadados.legenda === "string" && metadados.legenda.trim()
            ? metadados.legenda
            : null;

        return (
          <InteracaoMensagem
            key={mensagem.id}
            alinhadaADireita={propria}
            textoCopiavel={textoCopiavel}
            reacoes={mensagem.reacoes ?? []}
            textos={acoes}
            onDefinirReacao={(emoji) => onDefinirReacao(mensagem, emoji)}
            onRemoverReacao={() => onRemoverReacao(mensagem)}
          >
            <div
              className={cn(
                "w-fit max-w-full rounded-2xl px-3 py-2 text-sm font-normal shadow-sm",
                propria
                  ? "rounded-tr-md bg-primary text-primary-foreground"
                  : "rounded-tl-md border border-border bg-background text-foreground",
              )}
            >
              {!propria && <p className="mb-1 text-xs font-semibold text-muted-foreground">{mensagem.remetenteNome}</p>}

              {tipo === "IMAGEM" && (
                <div className="space-y-1.5 rounded-lg border border-border bg-background/50 p-1.5 shadow-sm">
                  {midiaUrl && (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={midiaUrl} alt={metadados.legenda ?? textosAtendimentos.imagem} className="max-h-64 w-full rounded-md object-cover" />
                  )}
                  {metadados.legenda && <p>{metadados.legenda}</p>}
                </div>
              )}

              {tipo === "AUDIO" && (
                midiaUrl
                  ? (
                    <PlayerAudio
                      src={midiaUrl}
                      rotulo={textosAtendimentos.audio}
                      reproduzir={textosAtendimentos.reproduzir}
                      pausar={textosAtendimentos.pausar}
                      posicao={textosAtendimentos.posicao}
                    />
                  )
                  : <p>{textosAtendimentos.audio}</p>
              )}

              {tipo === "DOCUMENTO" && (
                <a href={midiaUrl ?? "#"} target="_blank" rel="noopener noreferrer" title={textosAtendimentos.baixar} className="flex min-w-64 items-center gap-3 rounded-lg bg-background/10 p-2.5 no-underline">
                  <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-background/15"><FileText className="size-5" /></span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-semibold">{metadados.nome ?? textosAtendimentos.documento}</span>
                    {metadados.tamanho !== undefined && <span className="block text-xs opacity-75">{tamanhoLegivel(metadados.tamanho)}</span>}
                    {metadados.legenda && <span className="mt-0.5 block text-xs opacity-85">{metadados.legenda}</span>}
                  </span>
                  <Download className="size-4 shrink-0" />
                </a>
              )}

              {tipo === "TEXTO" && <p className="whitespace-pre-wrap break-words">{mensagem.conteudo}</p>}

              <time className={cn("mt-1 block text-[10px]", propria ? "text-primary-foreground/70" : "text-muted-foreground")} dateTime={mensagem.enviadoEm}>
                {new Date(mensagem.enviadoEm).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}
              </time>
            </div>
          </InteracaoMensagem>
        );
      })}
    </div>
  );
}
export function ComposerChatInterno({ textos, onEnviar, onEnviarMidia, enviando = false, erro = false }: { textos: TextosChat; onEnviar: (conteudo: string) => Promise<unknown>; onEnviarMidia?: (arquivo: File, legenda?: string) => Promise<unknown>; enviando?: boolean; erro?: boolean }) {
  const [texto, setTexto] = useState("");
  const [enviandoLocal, setEnviandoLocal] = useState(false);
  const [arquivo, setArquivo] = useState<File | null>(null);
  const inputArquivoRef = useRef<HTMLInputElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const pendente = enviando || enviandoLocal;

  const textosAtendimentos = useTextos().atendimentos;
  const tComp = textosAtendimentos.composer;
  const configuracaoComposer = useConfiguracaoComposer();
  const gravador = useGravadorAudio(configuracaoComposer.data);

  async function enviarConteudo() {
    if (arquivo) {
      if (!onEnviarMidia) return;
      const legenda = texto.trim() || undefined;
      setEnviandoLocal(true);
      try {
        await onEnviarMidia(arquivo, legenda);
        setArquivo(null);
        setTexto("");
      } catch {
        // erro fica visivel
      } finally {
        setEnviandoLocal(false);
      }
      return;
    }
    const conteudo = texto.trim();
    if (!conteudo) return;
    setEnviandoLocal(true);
    try {
      await onEnviar(conteudo);
      setTexto("");
    } catch {
      // erro
    } finally {
      setEnviandoLocal(false);
    }
  }

  async function enviarGravacao() {
    if (!gravador.arquivo || gravador.erro || !onEnviarMidia) return;
    setEnviandoLocal(true);
    try {
      await onEnviarMidia(gravador.arquivo, undefined);
      gravador.descartar();
    } catch {
    } finally {
      setEnviandoLocal(false);
    }
  }

  function aoPressionarTecla(evento: KeyboardEvent<HTMLTextAreaElement>) {
    if (evento.key === "Enter" && !evento.shiftKey) {
      evento.preventDefault();
      void enviarConteudo();
    }
  }

  function aoSelecionarArquivo(evento: ChangeEvent<HTMLInputElement>) {
    const selecionado = evento.target.files?.[0] ?? null;
    setArquivo(selecionado);
    evento.target.value = "";
  }

  const erroDeGravacao =
    gravador.erro === "SEM_MICROFONE" ? tComp.audioSemMicrofone
      : gravador.erro === "PERMISSAO" ? tComp.audioPermissaoNegada
      : gravador.erro === "EM_USO" ? tComp.audioMicrofoneEmUso
      : gravador.erro === "CAPTURA" ? tComp.audioErroCaptura
      : gravador.erro === "TAMANHO" ? tComp.audioExcedeuLimite : null;

  return (
    <div className="shrink-0 border-t border-border bg-background p-4">
      {erro && <p role="alert" className="mb-2 text-sm text-destructive">{textos.erroEnviar}</p>}
      {erroDeGravacao && <p className="mb-2 text-sm text-destructive">{erroDeGravacao}</p>}
      <div className="mx-auto flex max-w-[780px] flex-col gap-2 rounded-xl border border-input bg-card p-2 shadow-sm">
        {arquivo && (
          <div className="flex items-center gap-2 rounded-md border border-border bg-muted/50 px-2 py-1 text-sm">
            <Paperclip className="size-4 shrink-0 text-muted-foreground" aria-hidden />
            <span className="flex-1 truncate">{arquivo.name}</span>
            <span className="shrink-0 text-xs text-muted-foreground">{tamanhoLegivel(arquivo.size)}</span>
            <button type="button" className="shrink-0 rounded p-0.5 hover:bg-destructive/10 hover:text-destructive" aria-label={tComp.anexoRemover} onClick={() => setArquivo(null)}>
              <X className="size-3.5" />
            </button>
          </div>
        )}

        {gravador.fase === "GRAVANDO" && (
          <div className="flex items-center gap-2 rounded-md border border-border bg-muted/50 p-2">
            <Mic className="size-4 animate-pulse text-destructive" aria-hidden />
            <span className="flex-1 text-sm">{tComp.audioGravando} · {duracaoLegivel(gravador.segundos)}</span>
            <Button type="button" size="icon-sm" variant="ghost" onClick={gravador.descartar} aria-label={tComp.audioDescartar}><Trash2 className="size-4" /></Button>
            <Button type="button" size="icon-sm" onClick={gravador.parar} aria-label={tComp.audioParar}><Square className="size-3.5 fill-current" /></Button>
          </div>
        )}

        {gravador.fase === "PREVISUALIZACAO" && gravador.previewUrl && (
          <div className="flex flex-wrap items-center gap-2 rounded-md border border-border bg-muted/50 p-2">
            <audio className="h-9 min-w-0 flex-1" controls src={gravador.previewUrl} aria-label={tComp.audioPreview} />
            <Button type="button" size="icon-sm" variant="ghost" onClick={gravador.descartar} disabled={pendente} aria-label={tComp.audioDescartar}><Trash2 className="size-4" /></Button>
          </div>
        )}

        <div className="flex items-end gap-2">
          <div className="flex shrink-0 items-center gap-1">
            <input ref={inputArquivoRef} type="file" accept={TIPOS_DE_ANEXO_ACEITOS} className="hidden" onChange={aoSelecionarArquivo} disabled={gravador.fase !== "INATIVO" || pendente} />
            <Button type="button" variant="ghost" size="icon" aria-label={tComp.anexo} onClick={() => inputArquivoRef.current?.click()} disabled={gravador.fase !== "INATIVO" || pendente}>
              <Paperclip className="size-4" />
            </Button>
            <PainelEmojiComposer
              rotulo={tComp.emoji}
              i18n={textosAtendimentos.mensagem.acoes.seletor}
              disabled={gravador.fase !== "INATIVO" || pendente}
              onEscolher={(emoji) => {
                const campo = textareaRef.current;
                setTexto((atual) => {
                  const { texto, cursor } = inserirNoCursor(atual, emoji, campo);
                  requestAnimationFrame(() => posicionarCursor(textareaRef.current, cursor));
                  return texto;
                });
              }}
            />
          </div>

          {gravador.disponivel && gravador.fase === "INATIVO" && !arquivo && (
            <div className="order-last shrink-0">
              <Button type="button" variant="ghost" size="icon" aria-label={tComp.audioGravar} onClick={gravador.iniciar} disabled={pendente}>
                <Mic className="size-4" />
              </Button>
            </div>
          )}

          <Textarea
            ref={textareaRef}
            value={texto}
            onChange={(evento) => setTexto(evento.target.value)}
            onKeyDown={aoPressionarTecla}
            placeholder={arquivo ? tComp.anexoLegendaPlaceholder : textos.placeholder}
            rows={1}
            disabled={gravador.fase !== "INATIVO" || pendente}
            className="min-h-11 max-h-32 min-w-0 flex-1 resize-none border-0 bg-transparent px-2 py-2 shadow-none focus-visible:ring-0"
          />

          <div className="order-last shrink-0">
            <Button
              type="button"
              size="icon"
              onClick={() => {
                if (gravador.fase === "PREVISUALIZACAO") void enviarGravacao();
                else void enviarConteudo();
              }}
              disabled={
                pendente
                || (gravador.fase === "PREVISUALIZACAO"
                  ? Boolean(gravador.erro) || !gravador.arquivo
                  : gravador.fase !== "INATIVO" || (!texto.trim() && !arquivo))
              }
              aria-label={textos.enviar}
            >
              <Send className="size-4" />
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
