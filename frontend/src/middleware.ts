import { NextRequest, NextResponse } from "next/server";

const PUBLIC_PATHS = ["/", "/explore", "/api/backend/auth/github"];

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Allow public paths and Next.js internals
  if (
    PUBLIC_PATHS.some((p) => pathname === p || pathname.startsWith(p + "/")) ||
    pathname.startsWith("/_next") ||
    pathname.startsWith("/api/backend/auth/github")
  ) {
    return NextResponse.next();
  }

  // Check for JWT cookie
  const jwt = request.cookies.get("jwt");
  if (!jwt) {
    return NextResponse.redirect(new URL("/", request.url));
  }

  // Forward JWT as Authorization header to backend (BFF pattern)
  const response = NextResponse.next();
  response.headers.set("Authorization", `Bearer ${jwt.value}`);
  return response;
}

export const config = {
  matcher: [
    "/((?!_next/static|_next/image|favicon.ico).*)",
  ],
};
