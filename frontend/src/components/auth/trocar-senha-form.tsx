"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ErroDeApi } from "@/lib/api/errors";
import { trocarSenha } from "@/lib/auth/api";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextos } from "@/lib/config/textos-provider";

/**
 * Alcançada de duas formas (E29): redirecionamento automático quando a senha é provisória
 * (AuthProvider) e item voluntário no menu do usuário. O aviso de "por que estou aqui" só aparece
 * no primeiro caso — quem troca por vontade própria já sabe o motivo.
 */
export function TrocarSenhaForm() {
  const textos = useTextos().trocarSenha;
  const router = useRouter();
  const precisaTrocarSenha = useAuthStore((estado) => estado.precisaTrocarSenha);

  const [senhaAtual, setSenhaAtual] = useState("");
  const [novaSenha, setNovaSenha] = useState("");
  const [confirmarSenha, setConfirmarSenha] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  async function handleSubmit(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setErro(null);

    if (novaSenha !== confirmarSenha) {
      setErro(textos.erroConfirmacao);
      return;
    }

    setEnviando(true);
    try {
      await trocarSenha(senhaAtual, novaSenha);
      router.push("/");
    } catch (excecao) {
      if (excecao instanceof ErroDeApi && excecao.status === 400) {
        setErro(excecao.message);
      } else {
        setErro(textos.erroGenerico);
      }
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Card className="w-full max-w-sm">
      <CardHeader>
        <CardTitle>{textos.titulo}</CardTitle>
        <CardDescription>
          {precisaTrocarSenha ? textos.avisoPrimeiroAcesso : textos.subtitulo}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="senha-atual">{textos.campoSenhaAtual}</Label>
            <Input
              id="senha-atual"
              type="password"
              autoComplete="current-password"
              required
              value={senhaAtual}
              onChange={(evento) => setSenhaAtual(evento.target.value)}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="nova-senha">{textos.campoNovaSenha}</Label>
            <Input
              id="nova-senha"
              type="password"
              autoComplete="new-password"
              required
              value={novaSenha}
              onChange={(evento) => setNovaSenha(evento.target.value)}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="confirmar-senha">{textos.campoConfirmarSenha}</Label>
            <Input
              id="confirmar-senha"
              type="password"
              autoComplete="new-password"
              required
              value={confirmarSenha}
              onChange={(evento) => setConfirmarSenha(evento.target.value)}
            />
          </div>
          {erro && (
            <p role="alert" className="text-sm text-cor-erro">
              {erro}
            </p>
          )}
          <Button type="submit" disabled={enviando} className="w-full">
            {enviando ? textos.salvando : textos.botaoSalvar}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
