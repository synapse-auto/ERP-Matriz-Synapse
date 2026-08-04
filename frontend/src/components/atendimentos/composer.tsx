"use client";

import { type ChangeEvent, type KeyboardEvent, useRef, useState } from "react";

import { Clock, Paperclip, Send, Smile, X } from "lucide-react";

import { Button, buttonVariants } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Textarea } from "@/components/ui/textarea";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { ErroDeApi } from "@/lib/api/errors";
import { janelaTextoLivreAberta } from "@/lib/atendimento/janela-24h";
import { useEnviarMensagem } from "@/lib/atendimento/use-enviar-mensagem";
import { useEnviarMidia } from "@/lib/atendimento/use-enviar-midia";
import type { CartaoAtendimento } from "@/lib/atendimento/types";
import { useTextos } from "@/lib/config/textos-provider";
import { FormularioMensagemProgramada } from "@/components/mensagens-programadas/formulario-mensagem-programada";

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
  const inputArquivoRef = useRef<HTMLInputElement>(null);
  const enviar = useEnviarMensagem();
  const enviarMidia = useEnviarMidia();

  if (conversa.status === "FINALIZADO") {
    return (
      <div className="border-t border-border p-3 text-center text-sm text-muted-foreground">
        {textosAtendimentos.finalizar.sucesso}
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

  function aoPressionarTecla(evento: KeyboardEvent<HTMLTextAreaElement>) {
    if (evento.key === "Enter" && !evento.shiftKey) {
      evento.preventDefault();
      enviarConteudo();
    }
  }

  const mensagemDeErro =
    enviarMidia.error instanceof ErroDeApi
      ? enviarMidia.error.message
      : enviarMidia.isError
        ? textos.anexoErro
        : null;

  if (!janelaAberta) {
    return (
      <div className="space-y-1 border-t border-border p-3">
        <p className="text-sm font-medium text-foreground">{textos.janelaFechadaTitulo}</p>
        <p className="text-sm text-muted-foreground">{textos.janelaFechadaDescricao}</p>
        <p className="text-xs text-muted-foreground">{textos.semTemplates}</p>
      </div>
    );
  }

  return (
    <div className="border-t border-border p-3">
      {arquivo && (
        <div className="mb-2 flex items-center gap-2 rounded-md border border-border bg-muted/50 px-2 py-1 text-sm">
          <Paperclip className="size-4 shrink-0 text-muted-foreground" aria-hidden />
          <span className="flex-1 truncate">{arquivo.name}</span>
          <span className="shrink-0 text-xs text-muted-foreground">{tamanhoLegivel(arquivo.size)}</span>
          {progresso !== null ? (
            <span className="shrink-0 text-xs text-muted-foreground">{progresso}%</span>
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

      <div className="flex items-end gap-2">
        <Popover>
          <PopoverTrigger
            className={buttonVariants({ variant: "ghost", size: "icon" })}
            aria-label={textos.emoji}
          >
            <Smile className="size-4" />
          </PopoverTrigger>
          <PopoverContent className="grid w-auto grid-cols-5 gap-1">
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

        <input
          ref={inputArquivoRef}
          type="file"
          accept={TIPOS_DE_ANEXO_ACEITOS}
          className="hidden"
          onChange={aoSelecionarArquivo}
        />
        <Tooltip>
          <TooltipTrigger
            className={buttonVariants({ variant: "ghost", size: "icon" })}
            aria-label={textos.anexo}
            onClick={() => inputArquivoRef.current?.click()}
          >
            <Paperclip className="size-4" />
          </TooltipTrigger>
          <TooltipContent>{textos.anexoSelecionar}</TooltipContent>
        </Tooltip>

        <Textarea
          value={texto}
          onChange={(evento) => setTexto(evento.target.value)}
          onKeyDown={aoPressionarTecla}
          placeholder={arquivo ? textos.anexoLegendaPlaceholder : textos.placeholder}
          rows={1}
          className="max-h-32 flex-1 resize-none"
        />

        <Tooltip>
          <TooltipTrigger className={buttonVariants({ variant: "ghost", size: "icon" })}
            aria-label={textos.agendar} onClick={() => setAgendamentoAberto(true)}>
            <Clock className="size-4" />
          </TooltipTrigger>
          <TooltipContent>{textos.agendar}</TooltipContent>
        </Tooltip>

        <Button
          type="button"
          size="icon"
          onClick={enviarConteudo}
          disabled={(!texto.trim() && !arquivo) || enviar.isPending || enviarMidia.isPending}
          aria-label={textos.enviar}
        >
          <Send className="size-4" />
        </Button>
      </div>

      {mensagemDeErro && (
        <p className="mt-1 text-xs text-destructive" role="alert">
          {mensagemDeErro}
        </p>
      )}
      <FormularioMensagemProgramada aberto={agendamentoAberto} leadId={conversa.leadId}
        leadNome={conversa.leadNome} conteudoInicial={texto} onFechar={() => setAgendamentoAberto(false)}
        onSalvo={() => setTexto("")} />
    </div>
  );
}
