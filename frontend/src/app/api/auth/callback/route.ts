import { NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url);
  const token = searchParams.get("token");
  const refresh = searchParams.get("refresh");

  if (!token) {
    return NextResponse.redirect(new URL("/?error=auth_failed", request.url));
  }

  const appUrl = process.env.NEXT_PUBLIC_APP_URL || new URL(request.url).origin;
  const response = NextResponse.redirect(new URL("/dashboard", appUrl));

  response.cookies.set("jwt", token, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: 60 * 15, // 15 minutes
  });

  if (refresh) {
    response.cookies.set("refresh_token", refresh, {
      httpOnly: true,
      secure: process.env.NODE_ENV === "production",
      sameSite: "lax",
      path: "/",
      maxAge: 60 * 60 * 24 * 30, // 30 days
    });
  }

  return response;
}
