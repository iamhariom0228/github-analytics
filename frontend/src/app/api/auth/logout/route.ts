import { NextResponse } from "next/server";
import { cookies } from "next/headers";

export async function POST() {
  const cookieStore = cookies();

  // Forward logout to backend
  const backendUrl = process.env.BACKEND_URL || "http://localhost:8080";
  const jwt = cookieStore.get("jwt");

  if (jwt) {
    await fetch(`${backendUrl}/api/v1/auth/logout`, {
      method: "POST",
      headers: { Cookie: `jwt=${jwt.value}` },
    }).catch(() => {});
  }

  const response = NextResponse.json({ success: true });
  response.cookies.delete("jwt");
  return response;
}
