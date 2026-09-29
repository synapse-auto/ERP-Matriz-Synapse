"use client";

import { useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import type { TemplateWhatsApp } from "@/lib/atendimento/types";
import {
  analisarVariaveisDoCorpo,
  interpolarCatalogo,
} from "@/lib/atendimento/variaveis-do-template";
import type { Textos } from "@/lib/config/schema";

type TextosTemplatesWhatsApp = Textos["templatesWhatsApp"];

export function FormularioEdicaoTemplate({
  template,
  salvando,
  erro,
  textos,
  onFechar,
  onSalvar,
}: {
  template: TemplateWhatsApp | null;
  salvando: boolean;
  erro: string | null;
  textos: TextosTemplatesWhatsApp;
  onFechar: () => void;
  onSalvar: (corpo: string) => void;
}) {
  const [corpo, setCorpo] = useState(() => template?.corpo ?? "");
  const analise = analisarVariaveisDoCorpo(corpo);

  if (!template) return null;

  return (
    // Com o PUT em voo o diálogo não fecha: fechar e reabrir permitiria uma segunda edição
    // concorrente e o erro da primeira sumiria junto com o diálogo.
    <Dialog open={Boolean(template)} onOpenChange={(abertoAgora) => !abertoAgora && !salvando && onFechar()}>
      <DialogContent showCloseButton={!salvando}>
        <DialogHeader>
          <DialogTitle>{textos.formulario.editarTitulo}</DialogTitle>
        </DialogHeader>
        <form
          className="space-y-3"
          onSubmit={(evento) => {
            evento.preventDefault();
            if (!analise.erro && corpo.trim()) onSalvar(corpo);
          }}
        >
          <p className="text-sm text-muted-foreground">{template?.nome}</p>
          <label className="block text-sm">
            {textos.formulario.corpo}
            <Textarea value={corpo} onChange={(evento) => setCorpo(evento.target.value)} className="mt-1" required />
          </label>
          {(analise.erro || erro) && (
            <p role="alert" className="text-sm text-destructive">
              {erro ?? textos.formulario.variavelInvalida}
            </p>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" disabled={salvando} onClick={onFechar}>
              {textos.formulario.cancelar}
            </Button>
            <Button
              type="submit"
              disabled={salvando || Boolean(analise.erro) || !corpo.trim()}
            >
              {textos.formulario.salvarEdicao}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export function DialogoConfirmacaoExclusaoTemplate({
  template,
  excluindo,
  erro,
  textos,
  onFechar,
  onConfirmar,
}: {
  template: TemplateWhatsApp | null;
  excluindo: boolean;
  /** Falha da última tentativa; o diálogo continua aberto para o usuário tentar de novo. */
  erro: string | null;
  textos: TextosTemplatesWhatsApp;
  onFechar: () => void;
  onConfirmar: () => void;
}) {
  // `disabled={excluindo}` só vale depois do próximo render: cliques no mesmo intervalo chegavam
  // a disparar dois ou três DELETE na conta compartilhada. A trava é síncrona e só se solta
  // quando a tentativa termina, permitindo tentar de novo depois de um erro.
  const confirmacaoEmCurso = useRef(false);
  useEffect(() => {
    if (!excluindo) confirmacaoEmCurso.current = false;
  }, [excluindo]);

  if (!template) return null;

  function confirmarUmaVez() {
    if (confirmacaoEmCurso.current) return;
    confirmacaoEmCurso.current = true;
    onConfirmar();
  }

  return (
    // Com o DELETE em voo a confirmação não fecha (Cancelar, X, Esc ou clique fora): reabrir para
    // outro template permitiria um segundo DELETE concorrente e esconderia o erro do primeiro.
    <Dialog open={Boolean(template)} onOpenChange={(abertoAgora) => !abertoAgora && !excluindo && onFechar()}>
      <DialogContent showCloseButton={!excluindo}>
        <DialogHeader>
          <DialogTitle>{textos.confirmacaoExclusao.titulo}</DialogTitle>
        </DialogHeader>
        <p className="text-sm text-muted-foreground">
          {interpolarCatalogo(textos.confirmacaoExclusao.descricao, { nome: template.nome })}
        </p>
        {erro && (
          <p role="alert" className="text-sm text-destructive">
            {erro}
          </p>
        )}
        <DialogFooter>
          <Button type="button" variant="outline" disabled={excluindo} onClick={onFechar}>
            {textos.confirmacaoExclusao.cancelar}
          </Button>
          <Button
            type="button"
            variant="destructive"
            disabled={excluindo || !template}
            onClick={confirmarUmaVez}
          >
            {textos.confirmacaoExclusao.confirmar}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
