import { describe, it, expect } from 'vitest';
import { parseSkills, formatSkills, isTechnicianRole } from '../components/admin/adminUtils';

describe('adminUtils', () => {
  it('parseSkills parses CATEGORY:level pairs, trims and upper-cases ids', () => {
    expect(parseSkills('NETWORK:3, database : 2')).toEqual({ NETWORK: 3, DATABASE: 2 });
  });

  it('parseSkills returns an empty map for blank input', () => {
    expect(parseSkills('')).toEqual({});
    expect(parseSkills('   ')).toEqual({});
    expect(parseSkills(undefined)).toEqual({});
  });

  it('parseSkills rejects malformed or out-of-range entries', () => {
    expect(() => parseSkills('NETWORK')).toThrow(/Invalid skill/);
    expect(() => parseSkills('NETWORK:9')).toThrow(/level 1-5/);
    expect(() => parseSkills('NETWORK:0')).toThrow(/Invalid skill/);
    expect(() => parseSkills('NETWORK:2:3')).toThrow(/Invalid skill/);
    expect(() => parseSkills(':3')).toThrow(/Invalid skill/);
  });

  it('formatSkills is the inverse of parseSkills', () => {
    expect(formatSkills({ NETWORK: 3, DATABASE: 2 })).toBe('NETWORK:3, DATABASE:2');
    expect(formatSkills(null)).toBe('');
  });

  it('isTechnicianRole is true only for TECHNICIAN and TEAM_LEAD', () => {
    expect(isTechnicianRole('TECHNICIAN')).toBe(true);
    expect(isTechnicianRole('TEAM_LEAD')).toBe(true);
    expect(isTechnicianRole('EMPLOYEE')).toBe(false);
    expect(isTechnicianRole('ADMINISTRATOR')).toBe(false);
  });
});
