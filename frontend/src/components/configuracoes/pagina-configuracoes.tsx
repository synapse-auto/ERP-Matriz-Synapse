"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { KeyRound, Save, UserRound } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { useTextos } from "@/lib/config/textos-provider";
import { useAtualizarMeuUsuario, useMeuUsuario } from "@/lib/equipe/use-equipe";

function dataDaSenha(valor: string | null, t: ReturnType<typeof useTextos>["configuracoes"]) {
  if (!valor) return t.senhaProvisoria;
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "long" }).format(new Date(valor));
}

export function PaginaConfiguracoes() {
  const textos = useTextos();
  const t = textos.configuracoes;
  const usuario = useMeuUsuario();
  const atualizar = useAtualizarMeuUsuario();
  const [nomeEditado, setNomeEditado] = useState<string | null>(null);
  const [erroNome, setErroNome] = useState(false);
  const [salvo, setSalvo] = useState(false);

  if (usuario.isLoading) {
    return <p className="p-6 text-sm text-muted-foreground">{t.carregando}</p>;
  }
  if (usuario.isError || !usuario.data) {
    return <ErroDeCarregamento mensagem={t.erro} onTentarNovamente={() => usuario.refetch()} className="m-6" />;
  }

  const dados = usuario.data;
  const nome = nomeEditado ?? dados.nome;

  function salvar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setSalvo(false);
    const normalizado = nome.trim();
    if (!normalizado) {
      setErroNome(true);
      return;
    }
    setErroNome(false);
    atualizar.mutate({ nome: normalizado }, { onSuccess: () => { setNomeEditado(normalizado); setSalvo(true); } });
  }

  return (
    <div className="min-h-full bg-background">
      <header className="border-b bg-card px-6 py-5 md:px-8">
        <h1 className="text-xl font-extrabold tracking-tight">{t.titulo}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{t.descricao}</p>
      </header>
      <div className="p-6 md:p-8">
        <div className="mx-auto w-full max-w-3xl">
          <h2 className="mb-4 text-lg font-extrabold tracking-tight">{t.perfil}</h2>
          <Card>
            <CardHeader className="border-b">
              <div className="flex items-center gap-4">
                <div className="flex size-16 items-center justify-center rounded-xl bg-primary text-xl font-bold text-primary-foreground">
                  {dados.nome.split(/\s+/).map((parte) => parte[0]).join("").slice(0, 2).toUpperCase()}
                </div>
                <div>
                  <CardTitle>{dados.nome}</CardTitle>
                  <CardDescription>{t.perfilDescricao}</CardDescription>
                </div>
              </div>
            </CardHeader>
            <CardContent className="pt-6">
              <form className="space-y-5" onSubmit={salvar}>
                <div className="grid gap-5 md:grid-cols-2">
                  <div className="space-y-2">
                    <Label htmlFor="perfil-nome">{t.nome}</Label>
                    <Input id="perfil-nome" value={nome} aria-invalid={erroNome} onChange={(evento) => { setNomeEditado(evento.target.value); setErroNome(false); setSalvo(false); }} />
                    {erroNome && <p className="text-xs text-destructive">{t.erroNome}</p>}
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="perfil-email">{t.email}</Label>
                    <Input id="perfil-email" value={dados.email} disabled />
                    <p className="text-xs text-muted-foreground">{t.emailAjuda}</p>
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="perfil-telefone">{t.telefone}</Label>
                    <Input id="perfil-telefone" value={dados.telefone ?? t.naoInformado} disabled />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="perfil-cargo">{t.cargo}</Label>
                    <Input id="perfil-cargo" value={dados.cargo ?? t.naoInformado} disabled />
                  </div>
                </div>
                <div className="flex flex-wrap items-center gap-3">
                  <span className="text-sm font-medium">{t.papel}</span>
                  <Badge variant="secondary"><UserRound className="size-3" />{dados.papel}</Badge>
                </div>
                <div className="flex flex-wrap items-center justify-between gap-3 border-t pt-5">
                  <div className="text-sm text-muted-foreground">
                    <p className="font-medium text-foreground">{t.senha}</p>
                    <p>{t.ultimaAlteracaoSenha}: {dataDaSenha(dados.senhaAlteradaEm, t)}</p>
                  </div>
                  <Link href="/trocar-senha" className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-border px-2.5 text-sm font-medium hover:bg-muted">
                    <KeyRound className="size-4" />{t.alterarSenha}
                  </Link>
                </div>
                <div className="flex items-center justify-end gap-3">
                  {salvo && <span role="status" className="text-sm text-cor-sucesso">{t.salvo}</span>}
                  {atualizar.isError && <span role="alert" className="text-sm text-destructive">{t.erro}</span>}
                  <Button type="submit" disabled={atualizar.isPending}>
                    <Save className="size-4" />{atualizar.isPending ? t.salvando : t.salvarPerfil}
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
