"use client";

import { useState } from "react";
import { Star } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useAvaliacaoDoAtendimento, useRegistrarAvaliacao } from "@/lib/atendimento/use-avaliacao";
import { useTextos } from "@/lib/config/textos-provider";
import { cn } from "@/lib/utils";

type Props = {
  atendimentoId: string;
  aberto: boolean;
  onFechar: () => void;
};

const NOTAS = [1, 2, 3, 4, 5] as const;

function preencher(modelo: string, valores: Record<string, string | number>): string {
  return Object.entries(valores).reduce(
    (texto, [chave, valor]) => texto.replaceAll(`{${chave}}`, String(valor)),
    modelo,
  );
}

export function DialogoAvaliacao({ atendimentoId, aberto, onFechar }: Props) {
  const textos = useTextos().atendimentos.avaliacao;
  const consulta = useAvaliacaoDoAtendimento(atendimentoId, aberto);
  const registrar = useRegistrarAvaliacao(atendimentoId);
  const [nota, setNota] = useState<number | null>(null);
  const existente = consulta.data;
  const escolhida = existente?.nota ?? nota;

  function confirmar() {
    if (existente || nota === null) {
      onFechar();
      return;
    }
    registrar.mutate(nota, { onSuccess: onFechar });
  }

  return (
    <Dialog open={aberto} onOpenChange={(valor) => !valor && onFechar()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{textos.titulo}</DialogTitle>
          <DialogDescription>
            {existente
              ? preencher(textos.jaRegistrada, { nota: existente.nota })
              : textos.descricao}
          </DialogDescription>
        </DialogHeader>
        <div className="flex justify-center gap-1" role="group" aria-label={textos.titulo}>
          {NOTAS.map((valor) => (
            <Button
              key={valor}
              type="button"
              variant="ghost"
              size="icon"
              aria-label={preencher(textos.nota, { nota: valor })}
              aria-pressed={escolhida === valor}
              disabled={Boolean(existente) || registrar.isPending}
              onClick={() => setNota(valor)}
            >
              <Star
                className={cn(
                  "size-[calc(var(--tamanho-icone-interface)*1.5)]",
                  escolhida !== null && valor <= escolhida
                    ? "fill-primary text-primary"
                    : "text-muted-foreground",
                )}
                aria-hidden
              />
            </Button>
          ))}
        </div>
        {registrar.isError && (
          <p role="alert" className="text-sm text-destructive">
            {textos.erro}
          </p>
        )}
        <DialogFooter>
          {existente ? (
            <Button type="button" onClick={onFechar}>
              {textos.cancelar}
            </Button>
          ) : (
            <>
              <Button type="button" variant="ghost" onClick={onFechar} disabled={registrar.isPending}>
                {textos.cancelar}
              </Button>
              <Button
                type="button"
                onClick={confirmar}
                disabled={registrar.isPending || nota === null}
              >
                {textos.confirmar}
              </Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
