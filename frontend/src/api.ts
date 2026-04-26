import type { ApiError } from "./types";

type RequestOptions = RequestInit & {
  bodyJson?: unknown;
};

export async function apiRequest<T>(url: string, options: RequestOptions = {}): Promise<T> {
  const response = await fetch(url, {
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      ...(options.headers ?? {})
    },
    ...options,
    body: options.bodyJson !== undefined ? JSON.stringify(options.bodyJson) : options.body
  });

  if (response.status === 204) {
    return undefined as T;
  }

  const contentType = response.headers.get("content-type") || "";
  const data = contentType.includes("application/json")
    ? await response.json()
    : await response.text();

  if (!response.ok) {
    const message =
      typeof data === "string"
        ? data
        : (data as ApiError).message || "Request failed";
    throw new Error(message);
  }

  return data as T;
}
