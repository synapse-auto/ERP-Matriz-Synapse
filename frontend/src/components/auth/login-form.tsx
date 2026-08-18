"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { PasswordInput } from "@/components/ui/password-input";
import { Label } from "@/components/ui/label";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextos } from "@/lib/config/textos-provider";

export function LoginForm() {
  const textos = useTextos().login;
  const router = useRouter();
  const definirSessao = useAuthStore((estado) => estado.definirSessao);

  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  async function handleSubmit(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setErro(null);
    setEnviando(true);

    try {
      const resposta = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, senha }),
      });

      if (!resposta.ok) {
        setErro(resposta.status === 401 ? textos.erroCredenciais : textos.erroGenerico);
        return;
      }

      const sessao = (await resposta.json()) as { accessToken: string; expiraEmSegundos: number };
      definirSessao({ ...sessao, email });
      router.push("/");
    } catch {
      setErro(textos.erroGenerico);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Card className="w-full max-w-sm">
      <CardHeader>
        <CardTitle>{textos.titulo}</CardTitle>
        <CardDescription>{textos.subtitulo}</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="email">{textos.campoEmail}</Label>
            <Input
              id="email"
              type="email"
              autoComplete="username"
              required
              value={email}
              onChange={(evento) => setEmail(evento.target.value)}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="senha">{textos.campoSenha}</Label>
            <PasswordInput
              id="senha"
              autoComplete="current-password"
              required
              value={senha}
              onChange={(evento) => setSenha(evento.target.value)}
            />
          </div>
          {erro && (
            <p role="alert" className="text-sm text-cor-erro">
              {erro}
            </p>
          )}
          <Button type="submit" disabled={enviando} className="w-full">
            {enviando ? textos.entrando : textos.botaoEntrar}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
