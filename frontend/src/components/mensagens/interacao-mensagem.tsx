"use client";

import { useState } from "react";
import dynamic from "next/dynamic";
import { ChevronDown, Copy, Forward, Plus, Reply, X } from "lucide-react";

import { Button, buttonVariants } from "@/components/ui/button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import type { ResumoReacao } from "@/lib/atendimento/types";
import type { Textos } from "@/lib/config/schema";
import { copiarTexto } from "@/lib/mensagens/copiar-texto";
import { cn } from "@/lib/utils";

const SeletorEmojiCompleto = dynamic(
  () => import("./seletor-emoji-completo").then((modulo) => modulo.SeletorEmojiCompleto),
  { ssr: false },
);

type TextosAcoes = Textos["atendimentos"]["mensagem"]["acoes"];

type Props = {
  alinhadaADireita: boolean;
  textoCopiavel: string | null;
  reacoes: ResumoReacao[];
  textos: TextosAcoes;
  onDefinirReacao: (emoji: string) => Promise<void>;
  onRemoverReacao: () => Promise<void>;
  onResponder?: () => void;
  onEncaminhar?: () => void;
  children: React.ReactNode;
};

function rotuloDaReacao(textos: TextosAcoes, reacao: ResumoReacao): string {
  const modelo = reacao.reagi ? textos.reacaoMinha : textos.reacaoQuantidade;
  return modelo.replace("{emoji}", reacao.emoji).replace("{quantidade}", String(reacao.quantidade));
}

export function InteracaoMensagem({
  alinhadaADireita,
  textoCopiavel,
  reacoes,
  textos,
  onDefinirReacao,
  onRemoverReacao,
  onResponder,
  onEncaminhar,
  children,
}: Props) {
  const [menuAberto, setMenuAberto] = useState(false);
  const [seletorAberto, setSeletorAberto] = useState(false);
  const [aviso, setAviso] = useState<string | null>(null);
  const [pendente, setPendente] = useState(false);

  async function escolher(emoji: string) {
    const minha = reacoes.find((item) => item.reagi);
    setPendente(true);
    setAviso(null);
    try {
      if (minha?.emoji === emoji) {
        await onRemoverReacao();
      } else {
        await onDefinirReacao(emoji);
      }
      setMenuAberto(false);
      setSeletorAberto(false);
    } catch {
      setAviso(textos.reacaoErro);
    } finally {
      setPendente(false);
    }
  }

  async function copiar() {
    if (!textoCopiavel) return;
    const ok = await copiarTexto(textoCopiavel);
    setAviso(ok ? textos.copiada : textos.copiarErro);
    if (ok) setMenuAberto(false);
  }

  const chevron = (
    <PopoverTrigger
      className={cn(
        buttonVariants({ variant: "ghost", size: "icon-xs" }),
        "absolute top-2 z-10 bg-background text-foreground shadow-sm ring-1 ring-foreground/10",
        "pointer-events-none opacity-0 transition-opacity group-hover:pointer-events-auto group-hover:opacity-100 group-focus-within:pointer-events-auto group-focus-within:opacity-100",
        "[@media(hover:none)]:pointer-events-auto [@media(hover:none)]:opacity-100",
        alinhadaADireita ? "right-full mr-1" : "left-full ml-1",
        (menuAberto || seletorAberto) && "pointer-events-auto opacity-100",
      )}
      aria-label={textos.abrir}
      disabled={pendente}
    >
      <ChevronDown className="size-3.5" aria-hidden />
    </PopoverTrigger>
  );

  return (
    <>
      <div className={cn("group flex", alinhadaADireita ? "justify-end" : "justify-start")}>
        <div
          className="relative w-fit max-w-[calc(100%-2.75rem)] sm:max-w-[70%]"
          data-slot="interacao-mensagem"
        >
          <Popover open={menuAberto} onOpenChange={setMenuAberto}>
            <div className="min-w-0 max-w-full">
              {children}
              {reacoes.length > 0 && (
                <div className={cn("mt-1 flex flex-wrap gap-1", alinhadaADireita && "justify-end")}>
                  {reacoes.map((reacao) => (
                    <button
                      key={reacao.emoji}
                      type="button"
                      aria-pressed={reacao.reagi}
                      aria-label={rotuloDaReacao(textos, reacao)}
                      disabled={pendente}
                      onClick={() => void escolher(reacao.emoji)}
                      className={cn(
                        "inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs",
                        reacao.reagi
                          ? "border-primary bg-primary/10 text-foreground"
                          : "border-border bg-background text-foreground",
                      )}
                    >
                      <span aria-hidden>{reacao.emoji}</span>
                      <span>{reacao.quantidade}</span>
                    </button>
                  ))}
                </div>
              )}
            </div>
            {chevron}
            <PopoverContent
              side="top"
              align={alinhadaADireita ? "end" : "start"}
              sideOffset={6}
              className="w-auto min-w-0 max-w-[min(20rem,calc(100vw-1.5rem))] p-2"
            >
              <p className="sr-only">{textos.titulo}</p>
              <div className="flex items-center gap-1">
                {textos.rapidas.map((emoji) => (
                  <Button
                    key={emoji}
                    type="button"
                    variant="ghost"
                    size="icon-xs"
                    aria-label={textos.reagir.replace("{emoji}", emoji)}
                    disabled={pendente}
                    onClick={() => void escolher(emoji)}
                  >
                    <span aria-hidden>{emoji}</span>
                  </Button>
                ))}
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-xs"
                  aria-label={textos.maisEmojis}
                  disabled={pendente}
                  onClick={() => {
                    setMenuAberto(false);
                    setSeletorAberto(true);
                  }}
                >
                  <Plus className="size-3.5" aria-hidden />
                </Button>
              </div>
              {textoCopiavel && (
                <Button
                  type="button"
                  variant="ghost"
                  className="mt-1 w-full justify-start"
                  disabled={pendente}
                  onClick={() => void copiar()}
                >
                  <Copy className="size-3.5" aria-hidden />
                  {textos.copiar}
                </Button>
              )}
              {onResponder && (
                <Button
                  type="button"
                  variant="ghost"
                  className="mt-1 w-full justify-start"
                  disabled={pendente}
                  onClick={() => {
                    setMenuAberto(false);
                    onResponder();
                  }}
                >
                  <Reply className="size-3.5" aria-hidden />
                  {textos.responder}
                </Button>
              )}
              {onEncaminhar && (
                <Button
                  type="button"
                  variant="ghost"
                  className="w-full justify-start"
                  disabled={pendente}
                  onClick={() => {
                    setMenuAberto(false);
                    onEncaminhar();
                  }}
                >
                  <Forward className="size-3.5" aria-hidden />
                  {textos.encaminhar}
                </Button>
              )}
            </PopoverContent>
          </Popover>
        </div>
      </div>
      <Dialog open={seletorAberto} onOpenChange={setSeletorAberto}>
        <DialogContent showCloseButton={false} className="sm:max-w-[22rem] p-3">
          <DialogHeader className="flex-row items-center justify-between gap-2">
            <DialogTitle>{textos.seletorTitulo}</DialogTitle>
            <DialogClose
              render={
                <Button type="button" variant="ghost" size="icon-xs" aria-label={textos.seletorFechar} />
              }
            >
              <X className="size-3.5" aria-hidden />
            </DialogClose>
          </DialogHeader>
          {seletorAberto && (
            <SeletorEmojiCompleto i18n={textos.seletor} onEscolher={(emoji) => void escolher(emoji)} />
          )}
        </DialogContent>
      </Dialog>
      {aviso && (
        <span className="sr-only" role="status">
          {aviso}
        </span>
      )}
    </>
  );
}
