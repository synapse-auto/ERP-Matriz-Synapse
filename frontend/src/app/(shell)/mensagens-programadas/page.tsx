import { ExigeCapacidade } from "@/components/gestao/exige-capacidade";
import { PaginaMensagensProgramadas } from "@/components/mensagens-programadas/pagina-mensagens-programadas";

export default function MensagensProgramadas() {
  return (
    <ExigeCapacidade capacidade="mensagens_programadas.ver">
      <div className="min-h-0 w-full flex-1">
        <PaginaMensagensProgramadas />
      </div>
    </ExigeCapacidade>
  );
}
