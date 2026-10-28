import type { ProblemDetail } from './types';

const TOKEN_KEY = 'familyfleet.token';

/**
 * Hibás HTTP-válasz. A backend minden hibát RFC 9457 ProblemDetail-ként ad vissza; ezt a
 * felület közvetlenül meg tudja jeleníteni (detail + mezőnkénti errors).
 */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail | null;

  constructor(status: number, problem: ProblemDetail | null) {
    super(problem?.detail ?? problem?.title ?? `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }
}

/*
 * A token a sessionStorage-ben van: oldalfrissítés után is megmarad, a lap bezárásakor törlődik.
 * Kompromisszum: a JavaScript eléri (XSS esetén ellopható), a httpOnly cookie biztonságosabb
 * lenne, de akkor CSRF-védelem kellene (lásd ADR-0003).
 */
export const tokenStore = {
  get(): string | null {
    return sessionStorage.getItem(TOKEN_KEY);
  },
  set(token: string): void {
    sessionStorage.setItem(TOKEN_KEY, token);
  },
  clear(): void {
    sessionStorage.removeItem(TOKEN_KEY);
  },
};

let unauthorizedHandler: () => void = () => {};

/** Lejárt vagy érvénytelen token esetén (401) az alkalmazás ezzel jelentkeztet ki. */
export function onUnauthorized(handler: () => void): void {
  unauthorizedHandler = handler;
}

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';

export async function apiRequest<T>(method: Method, path: string, body?: unknown): Promise<T> {
  const token = tokenStore.get();
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`/api${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (!response.ok) {
    if (response.status === 401 && token) {
      tokenStore.clear();
      unauthorizedHandler();
    }
    throw new ApiError(response.status, await readProblem(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

async function readProblem(response: Response): Promise<ProblemDetail | null> {
  const contentType = response.headers.get('Content-Type') ?? '';
  if (!contentType.includes('json')) {
    return null;
  }
  try {
    return (await response.json()) as ProblemDetail;
  } catch {
    return null;
  }
}

/** Query string a nem üres értékekből: { status: 'OPEN', type: '' } -> "?status=OPEN". */
export function queryString(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value));
    }
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}
