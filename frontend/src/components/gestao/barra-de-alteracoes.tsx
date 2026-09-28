"use client";

import { useState } from "react";
import { AlertTriangle, Check, PencilLine, RotateCcw } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { ErroDeApi } from "@/lib/api/errors";

import { preencher, rotuloDaCapacidade, type TextosGestao } from "./apoio";

type Violacao = { chave: string; codigo: string };

/** Botão secundário sobre a superfície escura da barra: contorno claro, fundo transparente. */
const BOTAO_SOBRE_ESCURO =
  "border-sidebar-foreground/25 bg-transparent text-sidebar-item-texto-hover hover:bg-sidebar-item-overlay-hover hover:text-sidebar-item-texto-hover "
  + "dark:border-sidebar-foreground/25 dark:bg-transparent dark:hover:bg-sidebar-item-overlay-hover";

/**
 * Salvar/Descartar de um rascunho de permissões, flutuando sobre o conteúdo numa barra escura e
 * compacta — a mesma superfície da navegação, para destacar do fundo claro sem cor nova.
 *
 * <p>Nunca publica sucesso antes do backend: o botão fica desabilitado enquanto a gravação está em
 * voo (sem duplo envio), o erro mantém o rascunho, e o 409 explica o conflito sem sobrescrever —
 * "recarregar" é uma escolha explícita de quem está editando.
 */
export function BarraDeAlteracoes({
  textos,
  quantidade,
  impacto,
  sensivel,
  salvando,
  erro,
  onSalvar,
  onDescartar,
  onRecarregar,
}: {
  textos: TextosGestao;
  quantidade: number;
  impacto: string;
  sensivel: boolean;
  salvando: boolean;
  erro: unknown;
  onSalvar: () => void;
  onDescartar: () => void;
  onRecarregar: () => void;
}) {
  const [confirmando, setConfirmando] = useState(false);
  if (quantidade === 0 && !erro) return null;

  const falha = mensagemDeErro(textos, erro);
  const conflito = erro instanceof ErroDeApi && erro.status === 409;

  return (
    <>
      <div
        role="region"
        aria-label={textos.barra.salvar}
        className="sticky bottom-3 z-20 mx-auto mt-4 flex w-fit max-w-full flex-wrap items-center gap-x-4 gap-y-2 rounded-xl border border-sidebar-border bg-sidebar py-2 pr-2 pl-3.5 text-sidebar-foreground shadow-lg"
      >
        <div className="flex min-w-0 items-center gap-2.5">
          <PencilLine className="size-4 shrink-0 text-cor-atencao" aria-hidden />
          <div className="min-w-0">
            {quantidade > 0 && (
              <p className="text-[13px] font-bold text-sidebar-item-texto-hover" aria-live="polite">
                {quantidade === 1 ? textos.barra.pendente : preencher(textos.barra.pendentes, { n: quantidade })}
              </p>
            )}
            <p className="text-[11px] text-sidebar-foreground/80">{impacto}</p>
            {falha && (
              <p role="alert" className="mt-0.5 flex items-start gap-1.5 text-[11px] font-medium text-sidebar-item-texto-perigo">
                <AlertTriangle className="mt-px size-3.5 shrink-0" aria-hidden />
                <span>{falha}</span>
              </p>
            )}
          </div>
        </div>
        <div className="flex shrink-0 flex-wrap items-center gap-2">
          {conflito && (
            <Button type="button" variant="outline" className={BOTAO_SOBRE_ESCURO} onClick={onRecarregar} disabled={salvando}>
              <RotateCcw className="size-(--tamanho-icone-interface)" aria-hidden />
              {textos.barra.recarregar}
            </Button>
          )}
          <Button
            type="button"
            variant="outline"
            className={BOTAO_SOBRE_ESCURO}
            onClick={onDescartar}
            disabled={salvando || quantidade === 0}
          >
            {textos.barra.descartar}
          </Button>
          <Button
            type="button"
            onClick={() => (sensivel ? setConfirmando(true) : onSalvar())}
            disabled={salvando || quantidade === 0}
          >
            <Check className="size-(--tamanho-icone-interface)" aria-hidden />
            {salvando ? textos.barra.salvando : textos.barra.salvar}
          </Button>
        </div>
      </div>
      {confirmando && (
        <Dialog open onOpenChange={(aberto) => !aberto && setConfirmando(false)}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>{textos.barra.sensivelTitulo}</DialogTitle>
              <DialogDescription>{textos.barra.sensivelDescricao}</DialogDescription>
            </DialogHeader>
            <p className="text-sm text-muted-foreground">{impacto}</p>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setConfirmando(false)}>
                {textos.barra.cancelar}
              </Button>
              <Button
                type="button"
                disabled={salvando}
                onClick={() => {
                  setConfirmando(false);
                  onSalvar();
                }}
              >
                {textos.barra.sensivelConfirmar}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}
    </>
  );
}

export function mensagemDeErro(textos: TextosGestao, erro: unknown): string | null {
  if (!erro) return null;
  if (!(erro instanceof ErroDeApi)) return textos.barra.erro;
  if (erro.status === 409) return textos.barra.conflito;
  if (erro.status === 403) return textos.barra.negada;
  if (erro.status === 422) {
    const violacoes = ((erro.problema as { violacoes?: Violacao[] } | null)?.violacoes ?? []).map((v) =>
      rotuloDaCapacidade(textos, v.chave.replace(/^modulo:/, "")),
    );
    return preencher(textos.barra.invalida, { itens: violacoes.join(", ") });
  }
  return textos.barra.erro;
}
