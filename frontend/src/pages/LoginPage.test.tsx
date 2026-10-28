import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { tokenStore } from '../api/client';
import { AuthProvider } from '../auth/AuthContext';
import { LoginPage } from './LoginPage';

function renderLogin() {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<p>Főoldal</p>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
}

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
  });
}

describe('LoginPage', () => {
  it('shows the backend error message on wrong password', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      json(401, { title: 'Hitelesítés szükséges', detail: 'Hibás e-mail-cím vagy jelszó.', requestId: 'req-42' }),
    );
    renderLogin();

    await userEvent.type(screen.getByLabelText('E-mail'), 'anna@example.com');
    await userEvent.type(screen.getByLabelText('Jelszó'), 'rossz-jelszo');
    await userEvent.click(screen.getByRole('button', { name: 'Belépés' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Hibás e-mail-cím vagy jelszó.');
    expect(screen.getByRole('alert')).toHaveTextContent('req-42');
    expect(tokenStore.get()).toBeNull();
  });

  it('registers, logs in, stores the token and leaves the login page', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(json(201, { id: 1, email: 'anna@example.com', displayName: 'Anna', role: 'USER' }))
      .mockResolvedValueOnce(json(200, { accessToken: 'jwt.token', tokenType: 'Bearer', expiresIn: 3600 }))
      .mockResolvedValueOnce(json(200, { id: 1, email: 'anna@example.com', displayName: 'Anna', role: 'USER' }));
    renderLogin();

    await userEvent.click(screen.getByRole('tab', { name: 'Regisztráció' }));
    await userEvent.type(screen.getByLabelText('Név'), 'Anna');
    await userEvent.type(screen.getByLabelText('E-mail'), 'anna@example.com');
    await userEvent.type(screen.getByLabelText('Jelszó'), 'nagyon-titkos-1');
    await userEvent.click(screen.getByRole('button', { name: 'Regisztráció és belépés' }));

    expect(await screen.findByText('Főoldal')).toBeInTheDocument();
    expect(tokenStore.get()).toBe('jwt.token');
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(['/api/auth/register', '/api/auth/login', '/api/auth/me']);
  });
});
