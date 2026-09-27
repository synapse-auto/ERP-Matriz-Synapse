import { Suspense } from "react";

import { PaginaGestao } from "@/components/gestao/pagina-gestao";

export default function Gestao() {
  return (
    <Suspense>
      <PaginaGestao />
    </Suspense>
  );
}
