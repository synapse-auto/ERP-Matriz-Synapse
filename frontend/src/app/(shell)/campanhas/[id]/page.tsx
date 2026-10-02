import { ExigeAcessoACampanhas } from "@/components/campanhas/exige-acesso-a-campanhas";
import { PaginaDetalheDaCampanha } from "@/components/campanhas/pagina-detalhe-da-campanha";

export default async function DetalheDaCampanha({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <ExigeAcessoACampanhas>
      <PaginaDetalheDaCampanha id={id} />
    </ExigeAcessoACampanhas>
  );
}
