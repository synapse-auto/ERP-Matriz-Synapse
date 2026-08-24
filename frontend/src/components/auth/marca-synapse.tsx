import Image from "next/image";

import { cn } from "@/lib/utils";

/** Marca fixa da Synapse, derivada da referência com fundo removido; não é configurável por instância. */
export function MarcaSynapse({ className, alt }: { className?: string; alt: string }) {
  return (
    <Image
      src="/logo-synapse.png"
      alt={alt}
      width={80}
      height={80}
      priority
      className={cn(className)}
    />
  );
}
