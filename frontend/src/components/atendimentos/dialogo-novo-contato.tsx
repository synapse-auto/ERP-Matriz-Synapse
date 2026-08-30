"use client";

import { useState } from "react";
import { MessageSquare } from "lucide-react";

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
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import type { PedidoDeNovoContato } from "@/lib/atendimento/types";
import { useTextos } from "@/lib/config/textos-provider";

type Props = {
  aberto: boolean;
  onFechar: () => void;
  onConfirmar: (pedido: PedidoDeNovoContato) => void;
  pendente?: boolean;
  erro?: string | null;
};

export function mascararTelefoneBr(valor: string): string {
  const digitos = valor.replace(/\D/g, "").slice(0, 11);
  if (digitos.length === 0) return "";
  if (digitos.length <= 2) return `(${digitos}`;
  const ddd = digitos.slice(0, 2);
  const resto = digitos.slice(2);
  if (resto.length <= 4) return `(${ddd}) ${resto}`;
  if (digitos.length <= 10) {
    return `(${ddd}) ${resto.slice(0, 4)}-${resto.slice(4)}`;
  }
  return `(${ddd}) ${resto.slice(0, 5)}-${resto.slice(5)}`;
}

export function DialogoNovoContato({
  aberto,
  onFechar,
  onConfirmar,
  pendente = false,
  erro = null,
}: Props) {
  return (
    <Dialog open={aberto} onOpenChange={(valor) => !valor && onFechar()}>
      <DialogContent className="sm:max-w-md">
        {aberto ? (
          <FormularioNovoContato
            onFechar={onFechar}
            onConfirmar={onConfirmar}
            pendente={pendente}
            erro={erro}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function FormularioNovoContato({
  onFechar,
  onConfirmar,
  pendente,
  erro,
}: Omit<Props, "aberto">) {
  const textos = useTextos().atendimentos.novoContato;
  const [nome, setNome] = useState("");
  const [telefone, setTelefone] = useState("");
  const [primeiraMensagem, setPrimeiraMensagem] = useState("");
  const [tentouEnviar, setTentouEnviar] = useState(false);

  const nomeValido = nome.trim().length > 0;
  const telefoneValido = telefone.replace(/\D/g, "").length >= 10;
  const erroNome = tentouEnviar && !nomeValido;
  const erroTelefone = tentouEnviar && !telefoneValido;

  function confirmar() {
    setTentouEnviar(true);
    if (!nomeValido || !telefoneValido || pendente) return;
    const mensagem = primeiraMensagem.trim();
    onConfirmar({
      nome: nome.trim(),
      telefone,
      ...(mensagem ? { primeiraMensagem: mensagem } : {}),
    });
  }

  return (
    <>
      <DialogHeader>
        <div className="flex items-start gap-3 pr-6">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
            <MessageSquare className="size-[calc(var(--tamanho-icone-interface)*1.25)]" aria-hidden />
          </div>
          <div className="space-y-1">
            <DialogTitle>{textos.titulo}</DialogTitle>
            <DialogDescription>{textos.descricao}</DialogDescription>
          </div>
        </div>
      </DialogHeader>

      <div className="space-y-3">
        <div className="space-y-1.5">
          <Label htmlFor="novo-contato-nome">{textos.nome}</Label>
          <Input
            id="novo-contato-nome"
            value={nome}
            onChange={(evento) => setNome(evento.target.value)}
            placeholder={textos.nomePlaceholder}
            autoComplete="name"
            required
            aria-invalid={erroNome}
            aria-describedby={erroNome ? "novo-contato-nome-erro" : undefined}
          />
          {erroNome && (
            <p id="novo-contato-nome-erro" className="text-sm text-destructive">
              {textos.nomeObrigatorio}
            </p>
          )}
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="novo-contato-telefone">{textos.telefone}</Label>
          <Input
            id="novo-contato-telefone"
            value={telefone}
            onChange={(evento) => setTelefone(mascararTelefoneBr(evento.target.value))}
            placeholder={textos.telefonePlaceholder}
            inputMode="tel"
            autoComplete="tel"
            required
            aria-invalid={erroTelefone}
            aria-describedby={erroTelefone ? "novo-contato-telefone-erro" : undefined}
          />
          {erroTelefone && (
            <p id="novo-contato-telefone-erro" className="text-sm text-destructive">
              {textos.telefoneObrigatorio}
            </p>
          )}
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="novo-contato-mensagem">{textos.primeiraMensagem}</Label>
          <Textarea
            id="novo-contato-mensagem"
            value={primeiraMensagem}
            onChange={(evento) => setPrimeiraMensagem(evento.target.value)}
            placeholder={textos.primeiraMensagemPlaceholder}
          />
        </div>
        <div className="rounded-xl border border-primary/20 bg-primary/10 p-3">
          <p className="text-sm text-foreground">{textos.avisoTemplate}</p>
        </div>
        {erro && (
          <p role="alert" className="text-sm text-destructive">
            {erro}
          </p>
        )}
      </div>

      <DialogFooter>
        <Button type="button" variant="outline" onClick={onFechar} disabled={pendente}>
          {textos.cancelar}
        </Button>
        <Button type="button" onClick={confirmar} disabled={pendente}>
          <MessageSquare className="size-(--tamanho-icone-interface)" aria-hidden />
          {textos.confirmar}
        </Button>
      </DialogFooter>
    </>
  );
}
