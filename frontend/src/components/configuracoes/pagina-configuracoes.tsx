"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { Camera, KeyRound, Save, Trash2, UserRound } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { PasswordInput } from "@/components/ui/password-input";
import { AvatarIniciais } from "@/components/ui/avatar-iniciais";
import { ErroDeCarregamento } from "@/components/ui/erro-de-carregamento";
import { useTextos } from "@/lib/config/textos-provider";
import { useAtualizarMeuUsuario, useAtualizarMinhaFoto, useMeuUsuario, useRemoverMinhaFoto } from "@/lib/equipe/use-equipe";

function dataDaSenha(valor: string | null, t: ReturnType<typeof useTextos>["configuracoes"]) {
  if (!valor) return t.senhaProvisoria;
  return new Intl.DateTimeFormat("pt-BR", { dateStyle: "long" }).format(new Date(valor));
}

export function PaginaConfiguracoes() {
  const textos = useTextos();
  const t = textos.configuracoes;
  const usuario = useMeuUsuario();
  const atualizar = useAtualizarMeuUsuario();
  const atualizarFoto = useAtualizarMinhaFoto();
  const removerFoto = useRemoverMinhaFoto();
  const [nomeEditado, setNomeEditado] = useState<string | null>(null);
  const [emailEditado, setEmailEditado] = useState<string | null>(null);
  const [telefoneEditado, setTelefoneEditado] = useState<string | null>(null);
  const [cargoEditado, setCargoEditado] = useState<string | null>(null);
  const [senhaAtual, setSenhaAtual] = useState("");
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
  const email = emailEditado ?? dados.email;
  const telefone = telefoneEditado ?? dados.telefone ?? "";
  const cargo = cargoEditado ?? dados.cargo ?? "";

  function salvar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setSalvo(false);
    const normalizado = nome.trim();
    if (!normalizado) {
      setErroNome(true);
      return;
    }
    setErroNome(false);
    atualizar.mutate(
      { nome: normalizado, email: email.trim(), telefone: telefone || null, cargo: cargo || null, senhaAtual: senhaAtual || null },
      { onSuccess: (novo) => { setNomeEditado(novo.nome); setEmailEditado(novo.email); setTelefoneEditado(novo.telefone); setCargoEditado(novo.cargo); setSenhaAtual(""); setSalvo(true); } },
    );
  }

  return (
    <div className="min-h-full bg-background">
      <header className="border-b bg-card px-6 py-5 md:px-8">
        <h1 className="text-xl font-bold tracking-tight">{t.titulo}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{t.descricao}</p>
      </header>
      <div className="p-6 md:p-8">
        <div className="mx-auto w-full max-w-3xl">
          <h2 className="mb-4 text-lg font-bold tracking-tight">{t.perfil}</h2>
          <Card>
            <CardHeader className="border-b">
                <div className="flex items-center gap-4">
                <AvatarIniciais id={dados.id} nome={dados.nome} fotoUrl={dados.fotoUrl} className="flex size-16 shrink-0 items-center justify-center rounded-xl text-xl font-bold text-primary-foreground" />
                <div>
                  <CardTitle>{dados.nome}</CardTitle>
                  <CardDescription>{t.perfilDescricao}</CardDescription>
                </div>
                <div className="ml-auto flex flex-wrap justify-end gap-2">
                  <label className={`inline-flex h-8 items-center gap-1.5 rounded-lg border border-border px-2.5 text-sm font-medium ${atualizarFoto.isPending ? "cursor-wait opacity-60" : "cursor-pointer hover:bg-muted"}`} aria-busy={atualizarFoto.isPending}>
                    <Camera className="size-(--tamanho-icone-interface)" />{atualizarFoto.isPending ? t.salvando : t.alterarFoto}
                    <input type="file" className="sr-only" disabled={atualizarFoto.isPending} accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp" onChange={(evento) => { const arquivo = evento.target.files?.[0]; if (arquivo) atualizarFoto.mutate(arquivo); evento.currentTarget.value = ""; }} />
                  </label>
                  {dados.fotoUrl && <Button type="button" variant="outline" size="sm" onClick={() => removerFoto.mutate()} disabled={removerFoto.isPending}><Trash2 className="size-(--tamanho-icone-interface)" />{t.removerFoto}</Button>}
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
                    <Input id="perfil-email" value={email} onChange={(evento) => { setEmailEditado(evento.target.value); setSalvo(false); }} />
                    <p className="text-xs text-muted-foreground">{t.emailAjuda}</p>
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="perfil-telefone">{t.telefone}</Label>
                    <Input id="perfil-telefone" value={telefone} placeholder={t.naoInformado} onChange={(evento) => { setTelefoneEditado(evento.target.value); setSalvo(false); }} />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="perfil-cargo">{t.cargo}</Label>
                    <Input id="perfil-cargo" value={cargo} placeholder={t.naoInformado} onChange={(evento) => { setCargoEditado(evento.target.value); setSalvo(false); }} />
                  </div>
                </div>
                <div className="max-w-md space-y-2">
                  <Label htmlFor="perfil-senha-atual">{t.senhaAtual}</Label>
                  <PasswordInput id="perfil-senha-atual" value={senhaAtual} onChange={(evento) => { setSenhaAtual(evento.target.value); setSalvo(false); }} placeholder={t.senhaAtualAjuda} />
                </div>
                <div className="flex flex-wrap items-center gap-3">
                  <span className="text-sm font-medium">{t.papel}</span>
                  <Badge variant="secondary"><UserRound className="size-[calc(var(--tamanho-icone-interface)*0.75)]" />{dados.papel}</Badge>
                </div>
                <div className="flex flex-wrap items-center justify-between gap-3 border-t pt-5">
                  <div className="text-sm text-muted-foreground">
                    <p className="font-medium text-foreground">{t.senha}</p>
                    <p>{t.ultimaAlteracaoSenha}: {dataDaSenha(dados.senhaAlteradaEm, t)}</p>
                  </div>
                  <Link href="/trocar-senha" className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-border px-2.5 text-sm font-medium hover:bg-muted">
                    <KeyRound className="size-(--tamanho-icone-interface)" />{t.alterarSenha}
                  </Link>
                </div>
                <div className="flex items-center justify-end gap-3">
                  {salvo && <span role="status" className="text-sm text-cor-sucesso">{t.salvo}</span>}
                  {atualizar.isError && <span role="alert" className="text-sm text-destructive">{t.erro}</span>}
                  {(atualizarFoto.isError || removerFoto.isError) && <span role="alert" className="text-sm text-destructive">{t.fotoErro}</span>}
                  <Button type="submit" disabled={atualizar.isPending}>
                    <Save className="size-(--tamanho-icone-interface)" />{atualizar.isPending ? t.salvando : t.salvarPerfil}
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
