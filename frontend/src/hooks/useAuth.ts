import { useQuery } from "@tanstack/react-query";
import { getMe } from "@/lib/api/client";

export function useAuth() {
  return useQuery({
    queryKey: ["me"],
    queryFn: getMe,
    retry: false,
    staleTime: 5 * 60 * 1000,
  });
}
