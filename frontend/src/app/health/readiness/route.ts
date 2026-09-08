import { NextResponse } from "next/server";

import { buscarTema, buscarTextos } from "@/lib/config/fetch-config";

/**
 * Readiness da aplicacao Next. O balanceador so deve encaminhar trafego quando os catalogos
 * necessarios para renderizar o layout raiz puderem ser obtidos e validados.
 */
export async function GET() {
  try {
    await Promise.all([buscarTextos(), buscarTema()]);
    return NextResponse.json({ status: "UP" });
  } catch {
    return NextResponse.json({ status: "DOWN" }, { status: 503 });
  }
}
