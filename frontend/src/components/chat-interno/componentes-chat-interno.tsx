"use client";

import { useState } from "react";
import { Send, Users } from "lucide-react";

import type { Textos } from "@/lib/config/schema";
import type { ChatConversa, ChatMensagem } from "@/lib/chat-interno/types";
import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";

type TextosChat = Textos["chatInterno"];

export function CabecalhoChatInterno({ conversa, textos }: { conversa?: ChatConversa; textos: TextosChat }) {
  const nome = conversa?.participantes?.trim() || textos.titulo;
  return (
    <header className="flex h-[72px] items-center gap-3 border-b border-border bg-background px-5">
      <AvatarIniciais id={conversa?.id ?? "chat-interno"} nome={nome} className="flex size-10 shrink-0 items-center justify-center rounded-xl text-xs font-bold text-white" />
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

export function ListaMensagensChatInterno({ mensagens, usuarioAtual, textos }: { mensagens: ChatMensagem[]; usuarioAtual: string | null; textos: TextosChat }) {
  if (!mensagens.length) return <p className="flex flex-1 items-center justify-center text-sm text-muted-foreground">{textos.semMensagens}</p>;
  return (
    <div className="flex-1 space-y-3 overflow-y-auto bg-muted/20 p-5">
      {mensagens.map((mensagem) => {
        const propria = mensagem.remetenteId === usuarioAtual;
        return (
          <div key={mensagem.id} className={cn("flex", propria ? "justify-end" : "justify-start")}>
            <div className={cn("max-w-[75%] rounded-xl px-3 py-2 text-sm shadow-sm", propria ? "bg-primary text-primary-foreground" : "bg-background text-foreground")}>
              {!propria && <p className="mb-1 text-xs font-semibold text-muted-foreground">{mensagem.remetenteNome}</p>}
              <p className="whitespace-pre-wrap break-words">{mensagem.conteudo}</p>
              <time className={cn("mt-1 block text-[10px]", propria ? "text-primary-foreground/70" : "text-muted-foreground")} dateTime={mensagem.enviadoEm}>
                {new Date(mensagem.enviadoEm).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}
              </time>
            </div>
          </div>
        );
      })}
    </div>
  );
}

export function ComposerChatInterno({ textos, onEnviar, enviando = false, erro = false }: { textos: TextosChat; onEnviar: (conteudo: string) => Promise<unknown>; enviando?: boolean; erro?: boolean }) {
  const [texto, setTexto] = useState("");
  const [enviandoLocal, setEnviandoLocal] = useState(false);
  const pendente = enviando || enviandoLocal;

  async function enviarTexto() {
    const conteudo = texto.trim();
    if (!conteudo || pendente) return;
    setEnviandoLocal(true);
    try {
      await onEnviar(conteudo);
      setTexto("");
    } catch {
      // O erro fica visível pelo estado da mutation; o texto permanece para nova tentativa.
    } finally {
      setEnviandoLocal(false);
    }
  }

  return (
    <div className="border-t border-border bg-background p-4">
      {erro && <p role="alert" className="mb-2 text-sm text-destructive">{textos.erroEnviar}</p>}
      <div className="mx-auto flex max-w-[780px] items-end gap-2 rounded-xl border border-input bg-card p-2 shadow-sm">
        <Textarea
          value={texto}
          onChange={(evento) => setTexto(evento.target.value)}
          placeholder={textos.placeholder}
          rows={2}
          disabled={pendente}
          onKeyDown={(evento) => {
            if (evento.key === "Enter" && !evento.shiftKey) {
              evento.preventDefault();
              void enviarTexto();
            }
          }}
        />
        <Button type="button" onClick={() => void enviarTexto()} disabled={!texto.trim() || pendente} aria-label={textos.enviar}>
          <Send className="size-4" aria-hidden />
          <span className="sr-only">{textos.enviar}</span>
        </Button>
      </div>
    </div>
  );
}
