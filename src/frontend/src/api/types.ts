// Generic Spring Data Page shape — reusable for any paginated endpoint
export interface Page<T> {
  content: T[]
  number: number
  size: number
  totalElements: number
  totalPages: number
  numberOfElements: number
  first: boolean
  last: boolean
  empty: boolean
}
