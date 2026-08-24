import Image from "next/image";

import { cn } from "@/lib/utils";

/** Logo vetorial da identidade fixa da Synapse; não é a logo configurável de cada instância. */
export function MarcaSynapse({ className, alt }: { className?: string; alt: string }) {
  return (
    <Image
      src="/synapse-logo.svg"
      alt={alt}
      width={96}
      height={96}
      priority
      className={cn(className)}
    />
  );
}
