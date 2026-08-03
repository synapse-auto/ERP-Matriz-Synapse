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
    return (
      <span className="inline-flex items-center gap-1 text-xs text-destructive">
        <AlertTriangle className="size-3.5" aria-hidden />
        {textos.status.falhou}
        {onReenviar && (
          <Button
            type="button"
            variant="link"
            size="xs"
            className="h-auto p-0 text-xs text-destructive"
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
    LIDO: {
      icone: <CheckCheck className="size-3.5 text-primary" aria-hidden />,
      rotulo: textos.status.lido,
    },
  };

  const { icone, rotulo } = mapa[status];
  return (
    <span className="inline-flex items-center text-muted-foreground" title={rotulo}>
      {icone}
    </span>
  );
}
