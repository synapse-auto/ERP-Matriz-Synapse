"use client";

import { AlertTriangle, Check, CheckCheck, Clock } from "lucide-react";

import { Button } from "@/components/ui/button";
import { useTextos } from "@/lib/config/textos-provider";
import type { StatusEntrega } from "@/lib/atendimento/types";

type Props = {
  status: StatusEntrega;
  onReenviar?: () => void;
};

/** relógio / ✓ / ✓✓ / ✓✓ azul / ⚠ com reenviar — o ciclo de entrega do prompt E11. */
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
    // LIDO precisa se distinguir de ENTREGUE (mesmo ícone CheckCheck). O rodapé do balão já usa
    // text-primary-foreground/70 para o horário; LIDO fica na MESMA cor sem a opacidade reduzida
    // — mais "cheio" que os demais, sem sair da paleta do balão (era text-primary, a própria cor
    // do fundo: ícone azul sobre balão azul, invisível).
    LIDO: {
      icone: <CheckCheck className="size-3.5 text-primary-foreground" aria-hidden />,
      rotulo: textos.status.lido,
    },
  };

  const { icone, rotulo } = mapa[status];
  // Sem cor própria: herda text-primary-foreground/70 do rodapé do balão (bolha-mensagem.tsx).
  // PENDENTE/ENVIADO/ENTREGUE ficam nessa opacidade reduzida; LIDO se destaca por cima (acima).
  return (
    <span className="inline-flex items-center" title={rotulo}>
      {icone}
    </span>
  );
}
