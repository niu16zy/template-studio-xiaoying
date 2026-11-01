export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export async function getJson<T>(path: string, signal?: AbortSignal): Promise<T> {
  return requestJson<T>(path, { signal });
}

export async function requestJson<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  if (options.method && options.method !== "GET") headers.set("X-Studio-Request", "1");
  const response = await fetch(path, {
    ...options,
    credentials: "same-origin",
    cache: "no-store",
    headers,
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => null);
    throw new ApiError(
      typeof payload?.message === "string" ? payload.message : "请求暂时不可用，请稍后重试",
      response.status,
      typeof payload?.code === "string" ? payload.code : "REQUEST_FAILED",
    );
  }
  return (await response.json()) as T;
}
