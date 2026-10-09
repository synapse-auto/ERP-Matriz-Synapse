"use client";

import { useState } from "react";

import { Bot } from "lucide-react";

import { Button } from "@/components/ui/button";
import type { CartaoInformacoesChatbot } from "@/lib/atendimento/types";
import { useTextos } from "@/lib/config/textos-provider";
import { cn } from "@/lib/utils";

/** Acima disso o card nasce recolhido; abaixo, mostrar tudo é mais curto que o botão de expandir. */
export const LINHAS_VISIVEIS_RECOLHIDO = 6;
export const CARACTERES_VISIVEIS_RECOLHIDO = 360;

export function conteudoPrecisaRecolher(conteudo: string): boolean {
  return (
    conteudo.length > CARACTERES_VISIVEIS_RECOLHIDO
    || conteudo.split("\n").length > LINHAS_VISIVEIS_RECOLHIDO
  );
}

/** Moldura do card (cabeçalho, hora, margens) e altura de uma linha de texto, em px. */
const ALTURA_FIXA_ESTIMADA = 130;
const ALTURA_DA_LINHA_ESTIMADA = 22;
const CARACTERES_POR_LINHA_ESTIMADOS = 95;

/**
 * Altura inicial do card para o virtualizador. Sem ela cada card valeria o chute de uma bolha curta
 * e o "rolar até o fim" da abertura da conversa aterrissaria antes do último item. É só o ponto de
 * partida: a altura real é medida depois.
 */
export function alturaEstimadaDoCartao(conteudo: string): number {
  const linhas = conteudo
    .split("\n")
    .reduce((total, linha) => total + Math.max(1, Math.ceil(linha.length / CARACTERES_POR_LINHA_ESTIMADOS)), 0);
  const visiveis = conteudoPrecisaRecolher(conteudo) ? Math.min(linhas, LINHAS_VISIVEIS_RECOLHIDO) : linhas;
  return ALTURA_FIXA_ESTIMADA + visiveis * ALTURA_DA_LINHA_ESTIMADA + (conteudoPrecisaRecolher(conteudo) ? 32 : 0);
}

/** Só a hora, como no modelo: o separador de dia ("Hoje") da lista já diz a data. */
function horaDoCartao(valor: string): string {
  return new Date(valor).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" });
}

function dataEHora(valor: string): string {
  const data = new Date(valor);
  const dia = data.toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric" });
  const hora = data.toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" });
  return `${dia} ${hora}`;
}

/**
 * Card interno do histórico com o que o chatbot coletou antes da transferência.
 *
 * Não é uma bolha de mensagem: não tem remetente humano, status de entrega, reação, resposta nem
 * reenvio, e o texto é sempre renderizado como texto (nunca HTML). A origem é a automação — o card
 * não é atribuído a uma pessoa nem ao cliente.
 */
export function CartaoInformacoesChatbot({ cartao }: { cartao: CartaoInformacoesChatbot }) {
  const textos = useTextos().atendimentos.informacoesChatbot;
  const [expandido, setExpandido] = useState(false);
  const recolhivel = conteudoPrecisaRecolher(cartao.conteudo);
  const recolhido = recolhivel && !expandido;
  const idDoConteudo = `informacoes-chatbot-${cartao.id}`;

  return (
    <section
      aria-label={textos.titulo}
      data-slot="cartao-informacoes-chatbot"
      data-origem={cartao.origem}
      className="mx-auto w-full max-w-3xl rounded-2xl border border-primary/30 bg-primary/10 px-5 py-4 text-sm text-foreground shadow-sm"
    >
      <header className="flex items-center gap-3">
        <span
          className="flex size-9 shrink-0 items-center justify-center rounded-lg border border-primary/30 bg-background/70 text-primary"
          aria-hidden
        >
          <Bot className="size-[var(--tamanho-icone-interface)]" />
        </span>
        <div className="min-w-0 flex-1">
          <p className="font-bold leading-snug text-primary">{textos.titulo}</p>
          <p className="text-xs font-medium leading-snug text-muted-foreground">{textos.origem}</p>
        </div>
      </header>
      <p
        id={idDoConteudo}
        className={cn("mt-4 whitespace-pre-wrap break-words leading-relaxed", recolhido && "line-clamp-6")}
        data-recolhido={recolhido ? "true" : undefined}
      >
        {cartao.conteudo}
      </p>
      {recolhivel && (
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="mt-1 px-1 text-primary"
          aria-expanded={expandido}
          aria-controls={idDoConteudo}
          onClick={() => setExpandido((atual) => !atual)}
        >
          {expandido ? textos.verMenos : textos.verMais}
        </Button>
      )}
      <p className="mt-3 text-right text-xs text-muted-foreground">
        <time dateTime={cartao.registradoEm} title={dataEHora(cartao.registradoEm)}>
          {horaDoCartao(cartao.registradoEm)}
        </time>
      </p>
    </section>
  );
}
