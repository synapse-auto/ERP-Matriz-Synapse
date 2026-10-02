"use client";

import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { CircleCheck, FlaskConical } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { enviarTeste } from "@/lib/campanhas/api";
import { useTextos } from "@/lib/config/textos-provider";

/** Teste ao próprio número (ou de alguém que autorizou): contato já na Agenda, sem contar no limite. */
export function EnvioDeTeste({ rascunhoId }: { rascunhoId: string | null }) {
  const textos = useTextos().campanhas.passoRevisao;
  const [telefone, setTelefone] = useState("");
  const [autorizou, setAutorizou] = useState(false);
  const teste = useMutation({
    mutationFn: () => enviarTeste(rascunhoId as string, telefone.trim(), autorizou),
  });
  const podeEnviar = rascunhoId !== null && telefone.replace(/\D/g, "").length >= 10 && autorizou && !teste.isPending;

  return (
    <section className="space-y-3 rounded-xl border border-border bg-card p-4 shadow-sm" aria-labelledby="envio-de-teste">
      <div>
        <h3 id="envio-de-teste" className="flex items-center gap-2 text-sm font-bold">
          <FlaskConical className="size-4" aria-hidden />
          {textos.teste}
        </h3>
        <p className="text-xs text-muted-foreground">{textos.testeDescricao}</p>
      </div>
      <div className="space-y-1.5">
        <Label htmlFor="teste-telefone">{textos.testeTelefone}</Label>
        <Input
          id="teste-telefone"
          inputMode="tel"
          className="max-w-64"
          value={telefone}
          placeholder={textos.testePlaceholder}
          onChange={(evento) => setTelefone(evento.target.value)}
        />
      </div>
      <label className="flex items-start gap-2 text-sm">
        <input
          type="checkbox"
          className="mt-0.5 size-4 accent-primary"
          checked={autorizou}
          onChange={(evento) => setAutorizou(evento.target.checked)}
        />
        {textos.testeAutorizou}
      </label>
      <div className="flex flex-wrap items-center gap-3">
        <Button type="button" variant="outline" disabled={!podeEnviar} onClick={() => teste.mutate()}>
          {teste.isPending ? textos.testeEnviando : textos.testeEnviar}
        </Button>
        {teste.isSuccess && (
          <p role="status" className="flex items-center gap-1.5 text-sm text-cor-sucesso">
            <CircleCheck className="size-4" aria-hidden />
            {textos.testeEnviado}
          </p>
        )}
        {teste.isError && (
          <p role="alert" className="text-sm text-destructive">
            {teste.error instanceof Error && teste.error.message ? teste.error.message : textos.testeErro}
          </p>
        )}
      </div>
    </section>
  );
}
