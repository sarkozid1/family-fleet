import { ApiError } from '../api/client';

/**
 * Egy hiba megjelenítése. ApiError-nál a ProblemDetail adatait mutatja: a cím és a részletek
 * mellett a mezőnkénti hibákat és a kérésazonosítót (ezzel lehet a backend logjában keresni).
 */
export function ProblemAlert({ error }: { error: unknown }) {
  if (!error) {
    return null;
  }
  if (!(error instanceof ApiError)) {
    return (
      <div className="alert" role="alert">
        <strong>Nem sikerült elérni a szervert.</strong> Fut a backend?
      </div>
    );
  }
  const problem = error.problem;
  return (
    <div className="alert" role="alert">
      <strong>{problem?.title ?? `Hiba (${error.status})`}</strong>
      {problem?.detail && <div>{problem.detail}</div>}
      {problem?.errors && problem.errors.length > 0 && (
        <ul>
          {problem.errors.map((fieldError) => (
            <li key={`${fieldError.field}-${fieldError.message}`}>
              <code>{fieldError.field}</code>: {fieldError.message}
            </li>
          ))}
        </ul>
      )}
      {problem?.requestId && <div className="muted small">Kérésazonosító: {problem.requestId}</div>}
    </div>
  );
}
