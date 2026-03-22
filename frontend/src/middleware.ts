import { NextRequest, NextResponse } from "next/server";

const PUBLIC_PATHS = ["/", "/explore", "/share", "/api/backend/auth/github", "/api/demo-login", "/api/auth/logout", "/api/auth/refresh", "/api/auth/callback"];

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  if (
    PUBLIC_PATHS.some((p) => pathname === p || pathname.startsWith(p + "/")) ||
    pathname.startsWith("/_next") ||
    pathname.startsWith("/api/backend/auth/github")
  ) {
    return NextResponse.next();
  }

  const jwt = request.cookies.get("jwt");
  if (!jwt) {
    // No access token — try to silently refresh if we have a refresh token
    const refreshToken = request.cookies.get("refresh_token");
    if (refreshToken) {
      const refreshUrl = new URL("/api/auth/refresh", request.url);
      refreshUrl.searchParams.set("next", pathname);
      return NextResponse.redirect(refreshUrl);
    }
    return NextResponse.redirect(new URL("/", request.url));
  }

  // Forward JWT as Authorization header to backend (BFF pattern)
  const response = NextResponse.next();
  response.headers.set("Authorization", `Bearer ${jwt.value}`);
  return response;
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
