import { ApiError, apiRequest, onUnauthorized, queryString, tokenStore } from './client';

function jsonResponse(status: number, body: unknown, contentType = 'application/json') {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } });
}

describe('apiRequest', () => {
  it('sends the stored token as Bearer header and parses JSON', async () => {
    tokenStore.set('abc.def.ghi');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(200, [{ id: 1, name: 'Kovács család' }]));

    const result = await apiRequest<{ id: number }[]>('GET', '/households');

    expect(result).toEqual([{ id: 1, name: 'Kovács család' }]);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/households');
    expect((init?.headers as Record<string, string>).Authorization).toBe('Bearer abc.def.ghi');
  });

  it('turns a ProblemDetail response into an ApiError with field errors', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(
        400,
        {
          title: 'Validációs hiba',
          status: 400,
          detail: 'A kérés adatai érvénytelenek.',
          errors: [{ field: 'licensePlate', message: 'érvénytelen rendszám' }],
        },
        'application/problem+json',
      ),
    );

    const error = await apiRequest('POST', '/households/1/vehicles', { licensePlate: 'X' }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(400);
    expect(apiError.message).toBe('A kérés adatai érvénytelenek.');
    expect(apiError.problem?.errors?.[0].field).toBe('licensePlate');
  });

  it('clears the token and notifies the app when the token is rejected (401)', async () => {
    tokenStore.set('expired.token');
    const handler = vi.fn();
    onUnauthorized(handler);
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(401, { title: 'Hitelesítés szükséges' }, 'application/problem+json'));

    await expect(apiRequest('GET', '/households')).rejects.toBeInstanceOf(ApiError);

    expect(handler).toHaveBeenCalledOnce();
    expect(tokenStore.get()).toBeNull();
  });

  it('returns undefined for 204 No Content', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }));

    await expect(apiRequest('DELETE', '/vehicles/1')).resolves.toBeUndefined();
  });
});

describe('queryString', () => {
  it('skips empty values', () => {
    expect(queryString({ status: 'OPEN', type: '', from: undefined, page: 0 })).toBe('?status=OPEN&page=0');
    expect(queryString({ type: '' })).toBe('');
  });
});
