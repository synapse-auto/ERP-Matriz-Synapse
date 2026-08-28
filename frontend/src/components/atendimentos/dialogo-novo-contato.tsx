"use client";

import { useEffect, useState } from "react";

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
  const textos = useTextos().atendimentos.novoContato;
  const [nome, setNome] = useState("");
  const [telefone, setTelefone] = useState("");
  const [primeiraMensagem, setPrimeiraMensagem] = useState("");

  useEffect(() => {
    if (aberto) return;
    setNome("");
    setTelefone("");
    setPrimeiraMensagem("");
  }, [aberto]);

  const podeConfirmar = nome.trim().length > 0 && telefone.replace(/\D/g, "").length >= 10 && !pendente;

  function confirmar() {
    if (!podeConfirmar) return;
    const mensagem = primeiraMensagem.trim();
    onConfirmar({
      nome: nome.trim(),
      telefone,
      ...(mensagem ? { primeiraMensagem: mensagem } : {}),
    });
  }

  return (
    <Dialog open={aberto} onOpenChange={(valor) => !valor && onFechar()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{textos.titulo}</DialogTitle>
          <DialogDescription>{textos.descricao}</DialogDescription>
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
            />
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
            />
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
          <div className="rounded-xl border border-input bg-card p-3">
            <p className="text-sm text-muted-foreground">{textos.avisoTemplate}</p>
          </div>
          {erro && <p className="text-sm text-destructive">{erro}</p>}
        </div>

        <DialogFooter>
          <Button type="button" variant="ghost" onClick={onFechar} disabled={pendente}>
            {textos.cancelar}
          </Button>
          <Button type="button" onClick={confirmar} disabled={!podeConfirmar}>
            {textos.confirmar}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
