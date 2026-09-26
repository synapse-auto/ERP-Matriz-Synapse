"use client";

import { ArrowRight, Ban } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import type { PreviaDeCopia } from "@/lib/gestao/types";

import { rotuloDaCapacidade, rotuloDoModulo, type TextosGestao } from "./apoio";

function rotuloDaChave(textos: TextosGestao, chave: string): string {
  return chave.startsWith("modulo:") ? rotuloDoModulo(textos, chave.slice(7)).rotulo : rotuloDaCapacidade(textos, chave);
}

function rotuloDoValor(textos: TextosGestao, valor: string): string {
  if (valor === "PERMITIDO" || valor === "NEGADO") return textos.copia.valores[valor];
  return textos.permissoes.niveis[valor as keyof typeof textos.permissoes.niveis] ?? valor;
}

/**
 * Prévia de cópia calculada no servidor: mostra o que muda e o que foi impedido (teto do destino ou
 * alçada de quem copia). Aplicar só preenche o rascunho; salvar continua sendo um passo separado.
 */
export function DialogoDeCopia({
  textos,
  origem,
  previa,
  carregando,
  erro,
  onAplicar,
  onFechar,
}: {
  textos: TextosGestao;
  origem: string;
  previa: PreviaDeCopia | undefined;
  carregando: boolean;
  erro: boolean;
  onAplicar: () => void;
  onFechar: () => void;
}) {
  return (
    <Dialog open onOpenChange={(aberto) => !aberto && onFechar()}>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{textos.copia.titulo}</DialogTitle>
          <DialogDescription>{textos.copia.descricao}</DialogDescription>
        </DialogHeader>
        <p className="text-sm">
          <span className="font-semibold text-muted-foreground">{textos.copia.origem}: </span>
          <span className="font-bold">{origem}</span>
        </p>
        {carregando ? (
          <p className="text-sm text-muted-foreground">{textos.copia.carregando}</p>
        ) : erro || !previa ? (
          <p role="alert" className="text-sm text-destructive">{textos.copia.erro}</p>
        ) : (
          <div className="space-y-4">
            <section>
              <h3 className="mb-1.5 text-xs font-bold tracking-wide text-muted-foreground uppercase">{textos.copia.muda}</h3>
              {previa.alteracoes.length === 0 ? (
                <p className="text-sm text-muted-foreground">{textos.copia.semMudancas}</p>
              ) : (
                <ul className="divide-y divide-border rounded-lg border border-border">
                  {previa.alteracoes.map((a) => (
                    <li key={a.chave} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                      <span className="min-w-0 truncate">{rotuloDaChave(textos, a.chave)}</span>
                      <span className="flex shrink-0 items-center gap-1.5 text-xs font-semibold">
                        <span className="text-muted-foreground">{rotuloDoValor(textos, a.antes)}</span>
                        <ArrowRight className="size-3" aria-hidden />
                        <span className="text-primary">{rotuloDoValor(textos, a.depois)}</span>
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </section>
            {previa.impedidos.length > 0 && (
              <section>
                <h3 className="mb-1.5 text-xs font-bold tracking-wide text-muted-foreground uppercase">{textos.copia.impedido}</h3>
                <ul className="divide-y divide-border rounded-lg border border-border bg-muted/40">
                  {previa.impedidos.map((i) => (
                    <li key={i.chave} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                      <span className="flex min-w-0 items-center gap-2 truncate">
                        <Ban className="size-3.5 shrink-0 text-muted-foreground" aria-hidden />
                        {rotuloDaChave(textos, i.chave)}
                      </span>
                      <span className="shrink-0 text-xs text-muted-foreground">{textos.copia.motivos[i.motivo]}</span>
                    </li>
                  ))}
                </ul>
              </section>
            )}
          </div>
        )}
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onFechar}>
            {textos.copia.cancelar}
          </Button>
          <Button type="button" onClick={onAplicar} disabled={carregando || erro || !previa}>
            {textos.copia.aplicar}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
