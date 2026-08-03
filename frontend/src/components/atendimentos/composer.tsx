"use client";

import { type KeyboardEvent, useState } from "react";

import { Paperclip, Send, Smile } from "lucide-react";

import { Button, buttonVariants } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Textarea } from "@/components/ui/textarea";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { janelaTextoLivreAberta } from "@/lib/atendimento/janela-24h";
import { useEnviarMensagem } from "@/lib/atendimento/use-enviar-mensagem";
import type { CartaoAtendimento } from "@/lib/atendimento/types";
import { useTextos } from "@/lib/config/textos-provider";

const EMOJIS = ["😀", "😂", "😍", "👍", "🙏", "🎉", "😢", "😡", "👀", "✅"];

type Props = {
  conversa: CartaoAtendimento;
};

/**
 * Três pontos do prompt E11: estado real de entrega (delegado a `useEnviarMensagem`), aviso de
 * janela de 24h ANTES de digitar, e anexo como estado "não disponível" real em vez de botão
 * fantasma (sem endpoint de upload no backend).
 */
export function Composer({ conversa }: Props) {
  const textosAtendimentos = useTextos().atendimentos;
  const textos = textosAtendimentos.composer;
  const [texto, setTexto] = useState("");
  const [avisoAnexo, setAvisoAnexo] = useState(false);
  const enviar = useEnviarMensagem();

  if (conversa.status === "FINALIZADO") {
    return (
      <div className="border-t border-border p-3 text-center text-sm text-muted-foreground">
        {textosAtendimentos.finalizar.sucesso}
      </div>
    );
  }

  const janelaAberta = janelaTextoLivreAberta(conversa.ultimaMensagemDoLeadEm);

  function enviarConteudo() {
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

  function aoPressionarTecla(evento: KeyboardEvent<HTMLTextAreaElement>) {
    if (evento.key === "Enter" && !evento.shiftKey) {
      evento.preventDefault();
      enviarConteudo();
    }
  }

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

        <Tooltip>
          <TooltipTrigger
            className={buttonVariants({ variant: "ghost", size: "icon" })}
            aria-label={textos.anexo}
            onClick={() => setAvisoAnexo(true)}
          >
            <Paperclip className="size-4" />
          </TooltipTrigger>
          <TooltipContent>{textos.anexoIndisponivel}</TooltipContent>
        </Tooltip>

        <Textarea
          value={texto}
          onChange={(evento) => setTexto(evento.target.value)}
          onKeyDown={aoPressionarTecla}
          placeholder={textos.placeholder}
          rows={1}
          className="max-h-32 flex-1 resize-none"
        />

        <Button
          type="button"
          size="icon"
          onClick={enviarConteudo}
          disabled={!texto.trim() || enviar.isPending}
          aria-label={textos.enviar}
        >
          <Send className="size-4" />
        </Button>
      </div>
      {avisoAnexo && (
        <p className="mt-1 text-xs text-muted-foreground">{textos.anexoIndisponivel}</p>
      )}
    </div>
  );
}
