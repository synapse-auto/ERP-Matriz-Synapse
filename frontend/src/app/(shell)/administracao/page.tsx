"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/lib/auth/auth-store";
import { Placeholder } from "@/components/shell/placeholder";

export default function AdministracaoPage() {
  const papel = useAuthStore((estado) => estado.papel);
  const router = useRouter();

  useEffect(() => {
    // Proteção provisória no client-side; a proteção definitiva será no backend quando houver endpoints reais
    if (papel !== "GESTOR" && papel !== "ADMINISTRADOR") {
      router.replace("/");
    }
  }, [papel, router]);

  if (papel !== "GESTOR" && papel !== "ADMINISTRADOR") return null;

  return <Placeholder />;
}
