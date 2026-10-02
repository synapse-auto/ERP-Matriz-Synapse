"use client";

import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { interpolarCatalogo } from "@/lib/atendimento/variaveis-do-template";
import { useAtualizarConfiguracao, useEhAdministradorDeCampanhas } from "@/lib/campanhas/hooks";
import type { ConfiguracaoDeCampanhas } from "@/lib/campanhas/types";
import {
  errosDaConfiguracao,
  type CampoDeConfiguracao,
  type FormularioDeConfiguracao,
} from "@/lib/campanhas/validacao";
import { useTextos } from "@/lib/config/textos-provider";

import { CampoNumerico } from "./campo-numerico";

function formularioDe(c: ConfiguracaoDeCampanhas): FormularioDeConfiguracao {
  return {
    tetoDiarioDaInstancia: c.tetoDiarioDaInstancia,
    limiteDiarioPadrao: c.limiteDiarioPadrao,
    limiteMetaInformado: c.limiteMetaInformado,
    limiarDeFalhaPorCento: c.limiarDeFalhaPorCento,
    janelaDeEnvios: c.janelaDeEnvios,
    minimoDeAmostra: c.minimoDeAmostra,
  };
}

/** Configurações da instância: o administrador edita; os demais consultam. Vale no ciclo seguinte, sem deploy. */
export function FormularioDeConfiguracaoDeCampanhas({ configuracao }: { configuracao: ConfiguracaoDeCampanhas }) {
  const t = useTextos().campanhas.configuracaoDaInstancia;
  const ehAdministrador = useEhAdministradorDeCampanhas();
  const salvar = useAtualizarConfiguracao();
  const [habilitado, setHabilitado] = useState(configuracao.envioHabilitado);
  const [form, setForm] = useState<FormularioDeConfiguracao>(formularioDe(configuracao));
  const erros = errosDaConfiguracao(form);

  const campo = (chave: CampoDeConfiguracao, rotulo: string, ajuda?: string) => (
    <CampoNumerico
      id={`config-${chave}`}
      rotulo={rotulo}
      ajuda={ajuda}
      valor={form[chave]}
      invalido={erros.includes(chave)}
      desabilitado={!ehAdministrador}
      aoMudar={(valor) => setForm({ ...form, [chave]: valor })}
    />
  );

  return (
    <form
      className="space-y-6"
      onSubmit={(evento) => {
        evento.preventDefault();
        if (ehAdministrador && erros.length === 0) {
          salvar.mutate({ envioHabilitado: habilitado, ...(form as Record<CampoDeConfiguracao, number>) });
        }
      }}
    >
      <section className="space-y-4 rounded-xl border border-border bg-card p-4 shadow-sm">
        <label className="flex items-start gap-3">
          <Switch checked={habilitado} disabled={!ehAdministrador} onCheckedChange={setHabilitado} />
          <span>
            <span className="block text-sm font-bold">{t.envioHabilitado}</span>
            <span className="block text-xs text-muted-foreground">{t.envioHabilitadoAjuda}</span>
          </span>
        </label>
        <div className="grid gap-4 sm:grid-cols-2">
          {campo("tetoDiarioDaInstancia", t.teto, t.tetoAjuda)}
          {campo("limiteDiarioPadrao", t.limitePadrao)}
          {campo("limiteMetaInformado", t.limiteMeta, t.limiteMetaAjuda)}
        </div>
      </section>
      <section className="space-y-4 rounded-xl border border-border bg-card p-4 shadow-sm">
        <div>
          <h2 className="text-sm font-bold">{t.pausaTitulo}</h2>
          <p className="text-xs text-muted-foreground">{t.pausaDescricao}</p>
        </div>
        <div className="grid gap-4 sm:grid-cols-3">
          {campo("limiarDeFalhaPorCento", t.limiarFalha)}
          {campo("janelaDeEnvios", t.janelaDeEnvios)}
          {campo("minimoDeAmostra", t.minimoAmostra)}
        </div>
      </section>
      <section className="space-y-1 text-xs text-muted-foreground">
        <h2 className="text-sm font-bold text-foreground">{t.prazos}</h2>
        <p>{interpolarCatalogo(t.conferenciaApos, { minutos: String(configuracao.conferenciaAposMinutos) })}</p>
        <p>{interpolarCatalogo(t.respondeuJanela, { dias: String(configuracao.respondeuJanelaDias) })}</p>
      </section>
      <div className="flex flex-wrap items-center gap-3">
        <Button type="submit" disabled={!ehAdministrador || erros.length > 0 || salvar.isPending}>
          {salvar.isPending ? t.salvando : t.salvar}
        </Button>
        {!ehAdministrador && <p className="text-sm text-muted-foreground">{t.somenteAdministrador}</p>}
        {salvar.isSuccess && (
          <p role="status" className="text-sm text-cor-sucesso">
            {t.salvo}
          </p>
        )}
        {salvar.isError && (
          <p role="alert" className="text-sm text-destructive">
            {t.erroSalvar}
          </p>
        )}
      </div>
    </form>
  );
}
