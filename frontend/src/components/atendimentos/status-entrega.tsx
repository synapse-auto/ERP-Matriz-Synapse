"use client";

import { AlertTriangle, Check, CheckCheck, Clock } from "lucide-react";

import { Button } from "@/components/ui/button";
import { useTextos } from "@/lib/config/textos-provider";
import type { StatusEntrega } from "@/lib/atendimento/types";

type Props = {
  status: StatusEntrega;
  onReenviar?: () => void;
};

/** relógio / ✓ / ✓✓ / ✓✓ verde / ⚠ com reenviar — o ciclo de entrega do prompt E11 e E143. */
export function StatusEntregaIcone({ status, onReenviar }: Props) {
  const textos = useTextos().atendimentos.mensagem;

  if (status === "FALHOU") {
    // O balao de saida e sempre bg-primary (azul): text-destructive (calibrado para fundo claro,
    // ver design/TOKENS.md) fica quase invisivel ali. text-sidebar-item-texto-perigo e o unico
    // token do catalogo pensado para "erro sobre superficie escura" (mesmo uso na acao Sair do
    // popup de presenca) e sobrevive bem ao azul do primary.
    return (
      <span className="inline-flex items-center gap-1 text-xs text-sidebar-item-texto-perigo">
        <AlertTriangle className="size-3.5" aria-hidden />
        {textos.status.falhou}
        {onReenviar && (
          <Button
            type="button"
            variant="link"
            size="xs"
            className="h-auto p-0 text-xs text-sidebar-item-texto-perigo"
            onClick={onReenviar}
          >
            {textos.reenviar}
          </Button>
        )}
      </span>
    );
  }

  const mapa: Record<
    Exclude<StatusEntrega, "FALHOU">,
    { icone: React.ReactNode; rotulo: string }
  > = {
    PENDENTE: { icone: <Clock className="size-3.5" aria-hidden />, rotulo: textos.status.pendente },
    ENVIADO: { icone: <Check className="size-3.5" aria-hidden />, rotulo: textos.status.enviado },
    ENTREGUE: {
      icone: <CheckCheck className="size-3.5" aria-hidden />,
      rotulo: textos.status.entregue,
    },
    // LIDO exibe os dois traços verdes sobre o balão azul (bg-primary), comportamento padrão
    // familiar aos usuários (estilo WhatsApp). Usa o token --cor-status-lido (#86EFAC, contraste
    // 3.23:1 sobre o azul), pois --cor-sucesso (#17835A) foi calibrado para fundo claro e tem
    // contraste de apenas 1.05:1 ali (invisível sobre azul).
    LIDO: {
      icone: <CheckCheck className="size-3.5 text-status-lido" aria-hidden />,
      rotulo: textos.status.lido,
    },
  };

  const { icone, rotulo } = mapa[status];
  // Sem cor própria: herda text-primary-foreground/70 do rodapé do balão (bolha-mensagem.tsx).
  // PENDENTE/ENVIADO/ENTREGUE ficam nessa opacidade reduzida; LIDO se destaca em verde (acima).
  return (
    <span className="inline-flex items-center" title={rotulo}>
      {icone}
    </span>
  );
}
