import { daysLabel, requiredLink, urgency } from './labels';

describe('daysLabel', () => {
  it.each([
    [0, 'ma'],
    [1, 'holnap'],
    [7, '7 nap múlva'],
    [-1, 'lejárt 1 napja'],
    [-30, 'lejárt 30 napja'],
  ])('%i -> %s', (days, expected) => {
    expect(daysLabel(days)).toBe(expected);
  });
});

describe('urgency', () => {
  it.each([
    [-1, 'overdue'],
    [0, 'soon'],
    [7, 'soon'],
    [8, 'upcoming'],
    [30, 'upcoming'],
    [31, 'later'],
  ] as const)('%i -> %s', (days, expected) => {
    expect(urgency(days)).toBe(expected);
  });
});

describe('requiredLink', () => {
  it('mirrors the backend rules (ADR-0002)', () => {
    expect(requiredLink.TECHNICAL_INSPECTION).toBe('vehicle');
    expect(requiredLink.DRIVING_LICENCE).toBe('member');
    expect(requiredLink.KGFB_ANNIVERSARY).toBe('policy');
    expect(requiredLink.OTHER).toBe('none');
  });
});
