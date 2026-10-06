"use client";

import { useId, useState } from "react";
import { Pencil } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { useTextos } from "@/lib/config/textos-provider";
import { useSalvarTelefoneLead } from "@/lib/lead/use-painel-lead";

type Props = {
  leadId: string;
  telefone: string | null;
  contexto?: "padrao" | "agenda";
  permitido: boolean;
};

/** Edição explícita do destino de mensagens, compartilhada pelas fichas do chat e da Agenda. */
export function EditorTelefoneDoLead({
  leadId,
  telefone,
  contexto = "padrao",
  permitido,
}: Props) {
  const textosPainel = useTextos().painelLead;
  const textos = textosPainel.dados;
  const salvar = useSalvarTelefoneLead(leadId, contexto);
  const idCampo = useId();
  const [aberto, setAberto] = useState(false);
  const [novoTelefone, setNovoTelefone] = useState("");
  const [erro, setErro] = useState<"invalido" | "salvar" | null>(null);

  if (!permitido) return null;

  function abrir() {
    setNovoTelefone(telefone ?? "");
    setErro(null);
    setAberto(true);
  }

  function confirmar() {
    if (novoTelefone.replace(/\D/g, "").length < 10) {
      setErro("invalido");
      return;
    }
    setErro(null);
    salvar.mutate(
      novoTelefone.trim(),
      {
        onError: () => setErro("salvar"),
        onSuccess: () => setAberto(false),
      },
    );
  }

  return (
    <>
      <Button
        type="button"
        variant="ghost"
        size="icon-sm"
        className="size-7 shrink-0 text-muted-foreground hover:text-foreground"
        aria-label={textos.editarTelefone}
        title={textos.editarTelefone}
        onClick={abrir}
      >
        <Pencil className="size-(--tamanho-icone-interface)" aria-hidden />
      </Button>
      <Dialog open={aberto} onOpenChange={(novo) => !novo && !salvar.isPending && setAberto(false)}>
        <DialogContent showCloseButton={false} className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{textos.editarTelefone}</DialogTitle>
            <DialogDescription>{textos.avisoAlteracaoTelefone}</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <label htmlFor={idCampo} className="text-sm font-medium text-foreground">
              {textos.novoTelefone}
            </label>
            <Input
              id={idCampo}
              type="tel"
              inputMode="tel"
              autoComplete="tel"
              maxLength={30}
              value={novoTelefone}
              disabled={salvar.isPending}
              aria-invalid={erro === "invalido" || undefined}
              onChange={(evento) => {
                setNovoTelefone(evento.target.value);
                setErro(null);
              }}
              onKeyDown={(evento) => {
                if (evento.key === "Enter") confirmar();
              }}
            />
            {erro && (
              <p role="alert" className="text-sm text-destructive">
                {erro === "invalido" ? textos.telefoneInvalido : textos.erroAlteracaoTelefone}
              </p>
            )}
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              disabled={salvar.isPending}
              onClick={() => setAberto(false)}
            >
              {textos.cancelarAlteracaoTelefone}
            </Button>
            <Button type="button" disabled={salvar.isPending} onClick={confirmar}>
              {salvar.isPending ? textosPainel.edicao.salvando : textos.confirmarAlteracaoTelefone}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
