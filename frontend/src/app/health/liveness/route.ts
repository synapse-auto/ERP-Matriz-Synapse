import { NextResponse } from "next/server";

/** Liveness do processo Next; nao consulta backend, banco nem outro servico. */
export function GET() {
  return NextResponse.json({ status: "UP" });
}
