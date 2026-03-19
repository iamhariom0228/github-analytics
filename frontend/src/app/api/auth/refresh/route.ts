import { NextRequest, NextResponse } from "next/server";
import { cookies } from "next/headers";

export const dynamic = "force-dynamic";

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8080";

export async function GET(request: NextRequest) {
  const cookieStore = cookies();
  const refreshToken = cookieStore.get("refresh_token");

  if (!refreshToken) {
    return NextResponse.redirect(new URL("/", request.url));
  }

  try {
    const res = await fetch(`${BACKEND_URL}/api/v1/auth/refresh`, {
      method: "POST",
      headers: { Cookie: `refresh_token=${refreshToken.value}` },
      cache: "no-store",
    });

    if (!res.ok) {
      const response = NextResponse.redirect(new URL("/", request.url));
      response.cookies.delete("refresh_token");
      return response;
    }

    // Extract Set-Cookie from backend response to get new jwt
    const setCookie = res.headers.get("set-cookie");
    const next = request.nextUrl.searchParams.get("next") || "/dashboard";
    const response = NextResponse.redirect(new URL(next, request.url));

    if (setCookie) {
      // Parse jwt value from set-cookie header
      const match = setCookie.match(/jwt=([^;]+)/);
      if (match) {
        response.cookies.set("jwt", match[1], {
          httpOnly: true,
          path: "/",
          maxAge: 15 * 60,
          sameSite: "lax",
        });
      }
    }

    return response;
  } catch {
    return NextResponse.redirect(new URL("/", request.url));
  }
}
