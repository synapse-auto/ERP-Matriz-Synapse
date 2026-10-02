"use client";

import { useState } from "react";
import { Pause, Play, XCircle } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Switch } from "@/components/ui/switch";
import { useAcaoDaCampanha, useAlterarInterruptor, useEhAdministradorDeCampanhas } from "@/lib/campanhas/hooks";
import type { Campanha } from "@/lib/campanhas/types";
import { useTextos } from "@/lib/config/textos-provider";

function podePausar(campanha: Campanha): boolean {
  return campanha.status === "EM_ANDAMENTO" || campanha.status === "AGENDADA";
}

function podeRetomar(campanha: Campanha): boolean {
  return campanha.status === "PAUSADA" || campanha.status === "PAUSADA_AUTOMATICAMENTE";
}

function podeCancelar(campanha: Campanha): boolean {
  return campanha.status !== "CONCLUIDA" && campanha.status !== "CANCELADA";
}

/** Pausar, retomar, cancelar e o interruptor. Só o administrador age; os demais veem o botão e o porquê. */
export function ControlesDaCampanha({ campanha }: { campanha: Campanha }) {
  const textos = useTextos().campanhas;
  const t = textos.acoes;
  const ehAdministrador = useEhAdministradorDeCampanhas();
  const pausar = useAcaoDaCampanha("pausar");
  const retomar = useAcaoDaCampanha("retomar");
  const cancelar = useAcaoDaCampanha("cancelar");
  const interruptor = useAlterarInterruptor(campanha.id);
  const [confirmando, setConfirmando] = useState(false);
  const ativa = !campanha.desligada;
  const ajudaDaAcao = !ehAdministrador
    ? textos.ajudaSomenteAdministrador
    : podeRetomar(campanha)
      ? t.retomarAjuda
      : podePausar(campanha)
        ? t.pausarAjuda
        : null;
  const erro = pausar.isError || retomar.isError || cancelar.isError || interruptor.isError;

  return (
    <div className="flex flex-col items-start gap-2 sm:items-end">
      <div className="flex flex-wrap gap-2">
        {podePausar(campanha) && (
          <Button type="button" variant="outline" disabled={!ehAdministrador || pausar.isPending} aria-describedby="acoes-ajuda" onClick={() => pausar.mutate(campanha.id)}>
            <Pause aria-hidden />
            {pausar.isPending ? t.pausando : t.pausar}
          </Button>
        )}
        {podeRetomar(campanha) && (
          <Button type="button" disabled={!ehAdministrador || retomar.isPending} aria-describedby="acoes-ajuda" onClick={() => retomar.mutate(campanha.id)}>
            <Play aria-hidden />
            {retomar.isPending ? t.retomando : t.retomar}
          </Button>
        )}
        {podeCancelar(campanha) && (
          <Button type="button" variant="outline" disabled={!ehAdministrador || cancelar.isPending} aria-describedby="acoes-ajuda" onClick={() => setConfirmando(true)}>
            <XCircle className="text-destructive" aria-hidden />
            {cancelar.isPending ? t.cancelando : t.cancelar}
          </Button>
        )}
      </div>
      {podePausar(campanha) && (
        <label className="flex items-center gap-2 text-sm" title={t.interruptorAjuda}>
          <Switch
            checked={ativa}
            disabled={!ehAdministrador || interruptor.isPending}
            onCheckedChange={(ligado) => interruptor.mutate(!ligado)}
          />
          {ativa ? t.interruptorLigado : t.interruptorDesligado}
        </label>
      )}
      {ajudaDaAcao && (
        <p id="acoes-ajuda" className="max-w-72 text-xs text-muted-foreground sm:text-right">
          {ajudaDaAcao}
        </p>
      )}
      {erro && (
        <p role="alert" className="text-xs text-destructive">
          {textos.erroAcao}
        </p>
      )}
      <Dialog open={confirmando} onOpenChange={setConfirmando}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t.cancelarTitulo}</DialogTitle>
            <DialogDescription>{t.cancelarDescricao}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setConfirmando(false)}>
              {t.manter}
            </Button>
            <Button
              type="button"
              variant="destructive"
              onClick={() => {
                cancelar.mutate(campanha.id);
                setConfirmando(false);
              }}
            >
              {t.cancelarConfirmar}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
