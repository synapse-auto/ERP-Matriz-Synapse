"use client";

import { FormEvent, useId, useState } from "react";
import { useRouter } from "next/navigation";
import { Mail, LockKeyhole } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { PasswordInput } from "@/components/ui/password-input";
import { Label } from "@/components/ui/label";
import { useAuthStore } from "@/lib/auth/auth-store";
import { useTextos } from "@/lib/config/textos-provider";

export function LoginForm() {
  const textos = useTextos().login;
  const router = useRouter();
  const definirSessao = useAuthStore((estado) => estado.definirSessao);
  const iniciarAberturaDoPainel = useAuthStore((estado) => estado.iniciarAberturaDoPainel);

  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [manterSessaoAtiva, setManterSessaoAtiva] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const erroId = useId();

  async function handleSubmit(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setErro(null);
    setEnviando(true);

    try {
      const resposta = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, senha, manterSessaoAtiva }),
      });

      if (!resposta.ok) {
        setErro(resposta.status === 401 ? textos.erroCredenciais : textos.erroGenerico);
        return;
      }

      const sessao = (await resposta.json()) as { accessToken: string; expiraEmSegundos: number };
      definirSessao({ ...sessao, email });
      if (useAuthStore.getState().precisaTrocarSenha) {
        router.push("/trocar-senha");
        return;
      }
      iniciarAberturaDoPainel();
      router.push("/");
    } catch {
      setErro(textos.erroGenerico);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="synapse-login__cartao">
      <p className="synapse-login__seguro">
        <LockKeyhole aria-hidden="true" size={14} />
        {textos.ambienteSeguro}
      </p>
      <div className="synapse-login__cabecalho">
        <h2 className="synapse-login__titulo-formulario">{textos.titulo}</h2>
        <p className="synapse-login__descricao-formulario">{textos.subtitulo}</p>
      </div>
      <form onSubmit={handleSubmit} className="synapse-login__campos">
        <div className="synapse-login__campo">
          <Label className="synapse-login__rotulo" htmlFor="email">
            {textos.campoEmail}
          </Label>
          <div className="synapse-login__campo-controle">
            <Mail aria-hidden="true" className="synapse-login__icone-campo" size={17} />
            <Input
              id="email"
              className="synapse-login__input"
              type="email"
              autoComplete="email"
              placeholder={textos.placeholderEmail}
              required
              aria-invalid={Boolean(erro)}
              aria-describedby={erro ? erroId : undefined}
              value={email}
              onChange={(evento) => setEmail(evento.target.value)}
            />
          </div>
        </div>
        <div className="synapse-login__campo">
          <Label className="synapse-login__rotulo" htmlFor="senha">
            {textos.campoSenha}
          </Label>
          <div className="synapse-login__campo-controle">
            <LockKeyhole aria-hidden="true" className="synapse-login__icone-campo" size={17} />
            <PasswordInput
              id="senha"
              className="synapse-login__input"
              autoComplete="current-password"
              placeholder={textos.placeholderSenha}
              required
              aria-invalid={Boolean(erro)}
              aria-describedby={erro ? erroId : undefined}
              value={senha}
              onChange={(evento) => setSenha(evento.target.value)}
            />
          </div>
        </div>
        <div className="synapse-login__opcoes">
          <label className="synapse-login__lembrar">
            <input
              className="synapse-login__checkbox"
              type="checkbox"
              checked={manterSessaoAtiva}
              onChange={(evento) => setManterSessaoAtiva(evento.target.checked)}
            />
            {textos.manterSessaoAtiva}
          </label>
        </div>
        {erro && (
          <p id={erroId} role="alert" aria-live="assertive" className="synapse-login__erro">
            {erro}
          </p>
        )}
        <Button type="submit" disabled={enviando} className="synapse-login__botao">
          {enviando ? textos.entrando : textos.botaoEntrar}
        </Button>
      </form>
      <p className="synapse-login__ajuda">{textos.semAcesso}</p>
      <p className="synapse-login__rodape">{textos.rodape}</p>
    </div>
  );
}
