"use client";

import { useEffect, useId, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Search, LoaderCircle, FileText } from "lucide-react";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useTextos } from "@/lib/config/textos-provider";
import { useAuthStore } from "@/lib/auth/auth-store";
import { ErroDeApi } from "@/lib/api/errors";
import { disponibilidadeDarwin, consultarDarwin, aplicarDarwin, type PreviaDarwin } from "@/lib/lead/darwin";

/** Apenas a ficha de clientes. Permissão/visibilidade/configuração são revalidadas no backend. */
export function ConsultaDarwin({ leadId, permitido, cpfAtual }: { leadId: string; permitido: boolean; cpfAtual?: string | null }) {
  const t = useTextos().darwin;
  const usuarioId = useAuthStore(s => s.usuarioId);
  const cache = useQueryClient();
  const disponibilidade = useQuery({ queryKey: ["darwin", "disponibilidade", usuarioId],
    queryFn: ({ signal }) => disponibilidadeDarwin(signal), enabled: permitido, retry: false });
  const [aberto, setAberto] = useState(false);
  const entradaId = useId();
  const [cpfConhecido, setCpfConhecido] = useState("");
  const [previa, setPrevia] = useState<PreviaDarwin | null>(null);
  const [nome, setNome] = useState(false);
  const [cpf, setCpf] = useState(false);
  const [sucesso, setSucesso] = useState(false);
  const [conflito, setConflito] = useState(false);
  const emCurso = useRef(false);
  const abortar = useRef<AbortController | null>(null);
  const aplicando = useRef(false);
  const consulta = useMutation({ mutationFn: ({ documento, signal }: { documento: string; signal: AbortSignal }) => consultarDarwin(leadId, documento, signal), retry: false, gcTime: 0 });
  const aplicacao = useMutation({ mutationFn: () => aplicarDarwin(leadId, previa!.confirmacao!, nome, cpf), retry: false, gcTime: 0,
    onSuccess: () => {
      setSucesso(true);
      void cache.invalidateQueries({ queryKey: ["lead", leadId] });
      void cache.invalidateQueries({ queryKey: ["atendimentos"] });
      void cache.invalidateQueries({ queryKey: ["agenda"] });
    },
    onError: (erro) => { if (erro instanceof ErroDeApi && [400, 409].includes(erro.status)) setConflito(true); },
  });
  useEffect(() => () => abortar.current?.abort(), []);

  function fechar() {
    if (aplicando.current) return;
    abortar.current?.abort(); emCurso.current = false;
    setAberto(false); setPrevia(null); setCpfConhecido(""); consulta.reset(); aplicacao.reset();
  }
  function abrir() {
    setCpfConhecido(cpfAtual ?? ""); setPrevia(null); setSucesso(false); setConflito(false);
    consulta.reset(); aplicacao.reset(); setAberto(true);
  }
  function buscar() {
    if (!cpfConhecido.trim() || emCurso.current || aplicando.current) return;
    emCurso.current = true;
    const controle = new AbortController(); abortar.current = controle;
    setAberto(true); setPrevia(null); setSucesso(false); setConflito(false); aplicacao.reset();
    consulta.mutate({ documento: cpfConhecido.trim(), signal: controle.signal }, {
      onSuccess: (dados) => {
        if (controle.signal.aborted) return;
        setPrevia(dados);
        const candidato = dados.candidatos[0];
        setNome(Boolean(candidato?.nome && !dados.nomeAtual?.trim()));
        setCpf(Boolean(candidato?.cpf && !candidato.cpfInvalido && !dados.cpfAtual?.trim()));
      },
      onSettled: () => { if (abortar.current === controle) emCurso.current = false; },
    });
  }
  function salvar() {
    if (!previa?.confirmacao || aplicando.current) return;
    aplicando.current = true;
    aplicacao.mutate(undefined, { onSettled: () => { aplicando.current = false; } });
  }
  function mensagem(erro: unknown) {
    const codigo = erro instanceof ErroDeApi ? erro.problema?.codigo : null;
    return ({ DARWIN_CONCORRENCIA: t.conflito, DARWIN_AUTENTICACAO: t.autorizacao, DARWIN_LIMITE: t.limiteProvedor,
      DARWIN_CONFIGURACAO: t.configuracao, DARWIN_IDENTIDADE_DIVERGENTE: t.identidade, DARWIN_CONSULTA_INVALIDA: t.comprovante,
      DARWIN_CPF_INVALIDO: t.cpfConsultaInvalido } as Record<string, string>)[codigo ?? ""] ?? t.erro;
  }
  if (!permitido || disponibilidade.isError || !disponibilidade.data?.habilitada) return null;
  const candidato = previa?.candidatos[0];
  const podeAplicar = previa?.estado === "ENCONTRADO" && previa.confirmacao && (nome || cpf) && !sucesso && !conflito;
  return <>
    <div className="flex min-w-0 items-center gap-3 text-sm"><FileText className="size-4 shrink-0 text-muted-foreground" aria-hidden /><span className="shrink-0 text-xs text-muted-foreground">{t.cpf}</span><span className="min-w-0 break-words">{cpfAtual || t.ausente}</span></div>
    <Button type="button" variant="outline" size="sm" onClick={abrir} disabled={consulta.isPending || aplicacao.isPending} className="w-full gap-2">
      <Search className="size-4" aria-hidden />{t.consultar}
    </Button>
    <Dialog open={aberto} onOpenChange={valor => { if (!valor) fechar(); }}>
      <DialogContent className="max-h-[calc(100dvh-2rem)] min-w-0 overflow-y-auto sm:max-w-lg" showCloseButton={false}>
        <DialogHeader><DialogTitle>{t.titulo}</DialogTitle><DialogDescription>{t.descricao}</DialogDescription></DialogHeader>
        <form aria-label={t.buscarCpf} className="min-w-0 space-y-2" onSubmit={e => { e.preventDefault(); buscar(); }}>
          <Label htmlFor={entradaId}>{t.cpfConsulta}</Label>
          <Input id={entradaId} value={cpfConhecido} inputMode="numeric" autoComplete="off" maxLength={14}
            disabled={consulta.isPending || aplicacao.isPending} aria-describedby={`${entradaId}-ajuda`}
            onChange={e => { setCpfConhecido(e.target.value); setPrevia(null); setSucesso(false); setConflito(false); consulta.reset(); aplicacao.reset(); }} />
          <p id={`${entradaId}-ajuda`} className="text-xs text-muted-foreground">{t.cpfConsultaAjuda}</p>
          <Button type="submit" variant="outline" disabled={!cpfConhecido.trim() || consulta.isPending || aplicacao.isPending}>{t.buscarCpf}</Button>
        </form>
        {consulta.isPending && <p role="status" className="flex items-center gap-2 text-sm text-muted-foreground"><LoaderCircle className="size-4 animate-spin" aria-hidden />{t.carregando}</p>}
        {consulta.isError && <p role="alert" className="text-sm text-destructive">{mensagem(consulta.error)}</p>}
        {previa?.limiteAtingido && <p className="rounded-md bg-muted p-3 text-xs text-muted-foreground">{t.limite}</p>}
        {previa && previa.estado !== "ENCONTRADO" && <p role="status" className="text-sm">{t[previa.estado]}</p>}
        {previa?.estado === "ENCONTRADO" && candidato && <div className="min-w-0 space-y-3">
          <p className="text-xs text-muted-foreground">{t.cadastroConsultado}</p>
          {([{ campo: "nome", titulo: t.nome, atual: previa.nomeAtual, encontrado: candidato.nome, selecionado: nome, alterar: setNome, rotulo: t.selecionarNome, invalido: false },
            { campo: "cpf", titulo: t.cpf, atual: previa.cpfAtual, encontrado: candidato.cpf, selecionado: cpf, alterar: setCpf, rotulo: t.selecionarCpf, invalido: candidato.cpfInvalido }]).map(c =>
            <div key={c.campo} className="min-w-0 rounded-lg border border-border p-3">
              <label className="flex items-center gap-2 text-sm font-semibold"><input type="checkbox" className="size-4 accent-primary" checked={c.selecionado}
                disabled={!c.encontrado || c.invalido || aplicacao.isPending || sucesso || conflito} onChange={e => c.alterar(e.target.checked)} aria-label={c.rotulo} />{c.titulo}</label>
              <dl className="mt-2 grid min-w-0 grid-cols-[auto_minmax(0,1fr)] gap-x-3 gap-y-1 text-sm"><dt className="text-muted-foreground">{t.atual}</dt><dd className="break-words">{c.atual || t.ausente}</dd><dt className="text-muted-foreground">{t.encontrado}</dt><dd className="break-words">{c.encontrado || t.ausente}</dd></dl>
              {c.invalido && <p className="mt-2 text-xs text-destructive">{t.cpfInvalido}</p>}
            </div>)}
        </div>}
        {aplicacao.isError && <p role="alert" className="text-sm text-destructive">{mensagem(aplicacao.error)}</p>}
        {sucesso && <p role="status" className="text-sm text-cor-sucesso">{t.sucesso}</p>}
        <DialogFooter className="flex-wrap gap-2">
          <Button type="button" variant="ghost" disabled={aplicacao.isPending} onClick={fechar}>{t.cancelar}</Button>
          {!consulta.isPending && previa && <Button type="button" variant="outline" disabled={aplicacao.isPending || !cpfConhecido.trim()} onClick={buscar}>{t.novamente}</Button>}
          <Button type="button" disabled={!podeAplicar || consulta.isPending || aplicacao.isPending} onClick={salvar}>
            {aplicacao.isPending && <LoaderCircle className="mr-2 size-4 animate-spin" aria-hidden />}{t.aplicar}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </>;
}
