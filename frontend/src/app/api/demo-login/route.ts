import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8080";

export async function GET() {
  try {
    const res = await fetch(`${BACKEND_URL}/api/v1/dev/demo-token`, { cache: "no-store" });

    if (!res.ok) {
      return new NextResponse("Demo login unavailable", { status: 503 });
    }

    const body = await res.json();
    const token: string = body?.data?.token;

    if (!token) {
      return new NextResponse("Demo token missing", { status: 500 });
    }

    const response = NextResponse.redirect(new URL("/dashboard", process.env.NEXT_PUBLIC_APP_URL || "http://localhost:3000"));
    response.cookies.set("jwt", token, {
      httpOnly: true,
      path: "/",
      maxAge: 7 * 24 * 60 * 60,
      sameSite: "lax",
    });

    return response;
  } catch {
    return new NextResponse("Demo login failed", { status: 502 });
  }
}
