"use client";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import type { CartaoAtendimento } from "@/lib/atendimento/types";
import { useTextos } from "@/lib/config/textos-provider";
import { cn, iniciaisDoNome, urlSegura } from "@/lib/utils";

type Props = {
  cartao: CartaoAtendimento;
  selecionado: boolean;
  onAbrirPainel: () => void;
  onAbrirAtendimento: () => void;
};

/** Etapa, foto, nome, atendente responsável, prévia da última mensagem — o card do prompt E11. */
export function CartaoConversa({ cartao, selecionado, onAbrirPainel, onAbrirAtendimento }: Props) {
  const textos = useTextos().atendimentos;

  return (
    <button
      type="button"
      onClick={onAbrirPainel}
      onDoubleClick={onAbrirAtendimento}
      className={cn(
        "flex w-full items-start gap-3 border-b border-border px-3 py-2.5 text-left transition-colors hover:bg-muted",
        selecionado && "bg-muted",
      )}
    >
      <Avatar>
        {urlSegura(cartao.leadFotoUrl) && (
          <AvatarImage src={urlSegura(cartao.leadFotoUrl)} alt={cartao.leadNome} />
        )}
        <AvatarFallback>{iniciaisDoNome(cartao.leadNome)}</AvatarFallback>
      </Avatar>

      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between gap-2">
          <p className="truncate text-sm font-medium text-foreground">{cartao.leadNome}</p>
          {cartao.etapaNome && (
            <span
              className="shrink-0 rounded-full px-2 py-0.5 text-[0.65rem] font-medium"
              style={
                cartao.etapaCor
                  ? { backgroundColor: `${cartao.etapaCor}22`, color: cartao.etapaCor }
                  : undefined
              }
            >
              {cartao.etapaNome}
            </span>
          )}
        </div>

        <p className="truncate text-xs text-muted-foreground">
          {cartao.atendenteNome ?? textos.cartao.semAtendente}
        </p>

        {cartao.ultimaMensagemPreview && (
          <p className="mt-0.5 truncate text-xs text-muted-foreground">
            {cartao.ultimaMensagemPreview}
          </p>
        )}
      </div>
    </button>
  );
}
