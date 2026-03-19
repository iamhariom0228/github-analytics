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
    const refreshToken: string = body?.data?.refreshToken;

    if (!token) {
      return new NextResponse("Demo token missing", { status: 500 });
    }

    const response = NextResponse.redirect(new URL("/dashboard", process.env.NEXT_PUBLIC_APP_URL || "http://localhost:3000"));
    response.cookies.set("jwt", token, {
      httpOnly: true,
      path: "/",
      maxAge: 15 * 60,
      sameSite: "lax",
    });
    if (refreshToken) {
      response.cookies.set("refresh_token", refreshToken, {
        httpOnly: true,
        path: "/api/auth/refresh",
        maxAge: 30 * 24 * 60 * 60,
        sameSite: "lax",
      });
    }

    return response;
  } catch {
    return new NextResponse("Demo login failed", { status: 502 });
  }
}
