import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Outlet, Route, Routes } from 'react-router-dom';
import type { HouseholdContext } from './HouseholdPage';
import { SharingTab } from './SharingTab';

const household = { id: 1, name: 'Kovács család', createdAt: '2026-10-28T10:00:00Z' };

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function renderTab() {
  return render(
    <MemoryRouter initialEntries={['/sharing']}>
      <Routes>
        <Route element={<Outlet context={{ household } satisfies HouseholdContext} />}>
          <Route path="/sharing" element={<SharingTab />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

const owner = {
  status: 'ACTIVE',
  userId: 10,
  invitationId: null,
  email: 'anna@example.com',
  displayName: 'Anna',
  you: true,
  since: '2026-10-28T10:00:00Z',
};

describe('SharingTab', () => {
  it('lists people with access and explains a new invitation', async () => {
    const invitation = {
      status: 'PENDING',
      userId: null,
      invitationId: 5,
      email: 'bela@example.com',
      displayName: null,
      you: false,
      since: '2026-10-29T08:00:00Z',
    };
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(json(200, [owner]))
      .mockResolvedValueOnce(json(201, invitation))
      .mockResolvedValueOnce(json(200, [owner, invitation]));
    renderTab();

    expect(await screen.findByText('Anna')).toBeInTheDocument();
    expect(screen.getByText('(te)')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('E-mail-cím'), 'bela@example.com');
    await userEvent.click(screen.getByRole('button', { name: 'Megosztás' }));

    expect(await screen.findByText(/Meghívó létrehozva: bela@example.com/)).toBeInTheDocument();
    expect(await screen.findByText('meghívva')).toBeInTheDocument();
    const [, init] = fetchMock.mock.calls[1];
    expect(init?.method).toBe('POST');
    expect(init?.body).toBe(JSON.stringify({ email: 'bela@example.com' }));
  });
});
