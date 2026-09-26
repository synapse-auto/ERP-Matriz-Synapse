"use client";

import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Check, Info, Lock } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { PasswordInput } from "@/components/ui/password-input";
import { ErroDeApi } from "@/lib/api/errors";
import type { PapelGerenciavel, UsuarioEquipe } from "@/lib/equipe/types";
import { useCriarUsuario, useEditarUsuario } from "@/lib/equipe/use-equipe";
import { CHAVE_GESTAO } from "@/lib/gestao/use-gestao";
import { cn } from "@/lib/utils";

import { ICONE_DO_PAPEL, type TextosGestao } from "./apoio";

/**
 * Novo/editar usuário. Reusa o contrato existente de /api/v1/usuarios: a senha inicial continua
 * obrigatória na criação (o protótipo a omite, mas criar credencial implícita não é opção), e as
 * funções oferecidas são só as que o contrato aceita e que quem pede pode conceder — GESTOR e
 * ADMINISTRADOR nunca aparecem no formulário comum.
 */
export function DialogoUsuario({
  textos,
  existente,
  funcoesPermitidas,
  podeAlterarFuncao,
  onFechar,
}: {
  textos: TextosGestao;
  existente?: UsuarioEquipe;
  funcoesPermitidas: PapelGerenciavel[];
  podeAlterarFuncao: boolean;
  onFechar: () => void;
}) {
  const cache = useQueryClient();
  const criar = useCriarUsuario();
  const editar = useEditarUsuario();
  const [nome, setNome] = useState(existente?.nome ?? "");
  const [email, setEmail] = useState(existente?.email ?? "");
  const [senha, setSenha] = useState("");
  const [papel, setPapel] = useState<PapelGerenciavel>(
    (existente?.papel as PapelGerenciavel | undefined) ?? funcoesPermitidas[funcoesPermitidas.length - 1] ?? "ATENDENTE",
  );
  const f = textos.formulario;
  const salvando = criar.isPending || editar.isPending;
  const erro = criar.error ?? editar.error;
  const mensagemErro = erro instanceof ErroDeApi && erro.status === 409 ? f.emailEmUso : erro ? f.erro : null;
  const opcoes = existente && !funcoesPermitidas.includes(existente.papel as PapelGerenciavel)
    ? [...funcoesPermitidas, existente.papel as PapelGerenciavel]
    : funcoesPermitidas;
  const funcaoTravada = Boolean(existente) && !podeAlterarFuncao;

  function aoSalvar() {
    const aoConcluir = () => {
      void cache.invalidateQueries({ queryKey: CHAVE_GESTAO });
      onFechar();
    };
    if (existente) editar.mutate({ id: existente.id, dados: { nome, email, papel } }, { onSuccess: aoConcluir });
    else criar.mutate({ nome, email, senha, papel }, { onSuccess: aoConcluir });
  }

  return (
    <Dialog open onOpenChange={(aberto) => !aberto && !salvando && onFechar()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{existente ? f.editarTitulo : f.criarTitulo}</DialogTitle>
          <DialogDescription>{existente ? f.editarDescricao : f.criarDescricao}</DialogDescription>
        </DialogHeader>
        <form
          className="space-y-5"
          onSubmit={(e) => {
            e.preventDefault();
            aoSalvar();
          }}
        >
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block space-y-1.5">
              <span className="text-[11px] font-bold tracking-wide text-muted-foreground uppercase">{f.nome}</span>
              <Input required maxLength={150} value={nome} placeholder={f.nomePlaceholder} onChange={(e) => setNome(e.target.value)} />
            </label>
            <label className="block space-y-1.5">
              <span className="text-[11px] font-bold tracking-wide text-muted-foreground uppercase">{f.email}</span>
              <Input required type="email" value={email} placeholder={f.emailPlaceholder} onChange={(e) => setEmail(e.target.value)} />
            </label>
          </div>
          {!existente && (
            <label className="block space-y-1.5">
              <span className="text-[11px] font-bold tracking-wide text-muted-foreground uppercase">{f.senha}</span>
              <PasswordInput required minLength={8} value={senha} onChange={(e) => setSenha(e.target.value)} />
              <span className="block text-xs text-muted-foreground">{f.senhaAjuda}</span>
            </label>
          )}
          <fieldset className="space-y-1.5">
            <legend className="mb-1.5 text-[11px] font-bold tracking-wide text-muted-foreground uppercase">{f.funcao}</legend>
            <div role="radiogroup" aria-label={f.funcao} className="grid gap-3 sm:grid-cols-2">
              {opcoes.map((opcao) => {
                const Icone = ICONE_DO_PAPEL[opcao];
                const ativo = papel === opcao;
                const desabilitado = funcaoTravada && !ativo;
                return (
                  <button
                    key={opcao}
                    type="button"
                    role="radio"
                    aria-checked={ativo}
                    disabled={desabilitado}
                    onClick={() => setPapel(opcao)}
                    className={cn(
                      "flex items-start gap-2.5 rounded-xl border p-3 text-left outline-none focus-visible:ring-2 focus-visible:ring-ring/50",
                      ativo ? "border-primary bg-accent text-accent-foreground" : "border-border hover:bg-muted/50",
                      desabilitado && "cursor-not-allowed opacity-50",
                    )}
                  >
                    <Icone className="mt-0.5 size-4 shrink-0" />
                    <span className="min-w-0">
                      <span className="block text-sm font-bold">{textos.papeis[opcao]}</span>
                      <span className="block text-xs opacity-80">{textos.papeisDescricao[opcao]}</span>
                    </span>
                  </button>
                );
              })}
            </div>
            {funcaoTravada && (
              <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <Lock className="size-3" aria-hidden />
                {f.funcaoBloqueada}
              </p>
            )}
          </fieldset>
          {!existente && (
            <p className="flex items-start gap-2 rounded-lg bg-muted px-3 py-2 text-xs text-muted-foreground">
              <Info className="mt-px size-3.5 shrink-0" aria-hidden />
              {f.iaAviso}
            </p>
          )}
          {mensagemErro && <p role="alert" className="text-sm text-destructive">{mensagemErro}</p>}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onFechar} disabled={salvando}>
              {f.cancelar}
            </Button>
            <Button type="submit" disabled={salvando}>
              <Check className="size-(--tamanho-icone-interface)" aria-hidden />
              {f.salvar}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
