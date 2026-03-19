import {
  parseKeyFacts,
  parseFlaggedTerms,
  MAX_FILE_SIZE_BYTES,
  DOCUMENT_TYPE_LABELS,
  STATUS_LABELS,
  INPUT_TYPE_LABELS,
} from './documents'

describe('parseKeyFacts', () => {
  it('parses valid JSON with all groups', () => {
    const json = JSON.stringify({
      amounts: [{ label: 'Total', value: '£100', context: 'invoice' }],
      dates: [{ label: 'Due', value: '2026-04-01', context: 'payment' }],
      parties: [{ label: 'Vendor', value: 'Acme Ltd', context: 'supplier' }],
    })

    const result = parseKeyFacts(json)
    expect(result.amounts).toHaveLength(1)
    expect(result.dates).toHaveLength(1)
    expect(result.parties).toHaveLength(1)
    expect(result.amounts[0].label).toBe('Total')
  })

  it('returns empty arrays for missing keys', () => {
    const result = parseKeyFacts(JSON.stringify({ amounts: [] }))
    expect(result.amounts).toEqual([])
    expect(result.dates).toEqual([])
    expect(result.parties).toEqual([])
  })

  it('returns empty structure for null input', () => {
    const result = parseKeyFacts(null)
    expect(result).toEqual({ amounts: [], dates: [], parties: [] })
  })

  it('returns empty structure for empty string', () => {
    const result = parseKeyFacts('')
    expect(result).toEqual({ amounts: [], dates: [], parties: [] })
  })

  it('returns empty structure for undefined input', () => {
    const result = parseKeyFacts(undefined)
    expect(result).toEqual({ amounts: [], dates: [], parties: [] })
  })

  it('returns empty structure for malformed JSON', () => {
    const result = parseKeyFacts('not valid json {{{')
    expect(result).toEqual({ amounts: [], dates: [], parties: [] })
  })

  it('returns empty arrays for non-array values', () => {
    const json = JSON.stringify({
      amounts: 'not an array',
      dates: 42,
      parties: { nested: true },
    })
    const result = parseKeyFacts(json)
    expect(result.amounts).toEqual([])
    expect(result.dates).toEqual([])
    expect(result.parties).toEqual([])
  })
})

describe('parseFlaggedTerms', () => {
  it('parses valid JSON array', () => {
    const json = JSON.stringify([
      { term: 'indemnity', definition: 'Legal protection against loss' },
    ])
    const result = parseFlaggedTerms(json)
    expect(result).toHaveLength(1)
    expect(result[0].term).toBe('indemnity')
  })

  it('returns empty array for null input', () => {
    expect(parseFlaggedTerms(null)).toEqual([])
  })

  it('returns empty array for undefined input', () => {
    expect(parseFlaggedTerms(undefined)).toEqual([])
  })

  it('returns empty array for empty string', () => {
    expect(parseFlaggedTerms('')).toEqual([])
  })

  it('returns empty array for malformed JSON', () => {
    expect(parseFlaggedTerms('{{invalid')).toEqual([])
  })

  it('returns empty array when JSON is an object, not an array', () => {
    expect(parseFlaggedTerms(JSON.stringify({ term: 'oops' }))).toEqual([])
  })
})

describe('constants', () => {
  it('MAX_FILE_SIZE_BYTES equals 10 MB', () => {
    expect(MAX_FILE_SIZE_BYTES).toBe(10 * 1024 * 1024)
  })

  it('DOCUMENT_TYPE_LABELS has expected keys', () => {
    const expectedKeys = [
      'BILL', 'INSURANCE', 'RENTAL', 'MORTGAGE', 'BANK_TERMS',
      'CONTRACT', 'GOVERNMENT', 'MEDICAL', 'TAX', 'OTHER',
    ]
    expect(Object.keys(DOCUMENT_TYPE_LABELS)).toEqual(expect.arrayContaining(expectedKeys))
  })

  it('STATUS_LABELS has expected keys', () => {
    expect(Object.keys(STATUS_LABELS)).toEqual(
      expect.arrayContaining(['UPLOADING', 'PROCESSING', 'READY', 'FAILED']),
    )
  })

  it('INPUT_TYPE_LABELS has expected keys', () => {
    expect(Object.keys(INPUT_TYPE_LABELS)).toEqual(
      expect.arrayContaining(['PDF', 'IMAGE', 'TEXT']),
    )
  })
})
