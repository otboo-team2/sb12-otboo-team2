import {type CursorParams} from "@/lib/api";

export interface BaseStore<T, P> {
  data: T | null;
  update: (newData: Partial<T>) => void;

  params: P;
  updateParams: (newParams: Partial<P>, options?: Partial<{ignoreFetch: boolean}>) => void;

  fetch: (options?: {
    throwError?: boolean;
    ignoreLoading?: boolean;
  }) => Promise<void>;
  clearData: () => void;

  loading: boolean;

  error?: string;
  setError: (error: string) => void;
  clearError: () => void;

  clear: () => void;
}



export interface ListStore<T, P> {
  data: T[];
  add: (item: T) => void;
  update: (id: string, newData: Partial<T>) => void;
  delete: (id: string) => void;
  count: () => number;

  params: P;
  updateParams: (newParams: Partial<P>, options?: Partial<{ignoreFetch: boolean}>) => void;

  fetch: (options?: {
    throwError?: boolean;
    ignoreLoading?: boolean;
  }) => Promise<void>;
  clearData: () => void;

  loading: boolean;

  error?: string;
  setError: (error: string) => void;
  clearError: () => void;

  clear: () => void;
}

export interface PaginatedStore<T, P extends CursorParams> {
  data: T[];
  add: (item: T) => void;
  update: (id: string, newData: Partial<T>) => void;
  delete: (id: string) => void;
  count: () => number;

  params: Omit<P, 'cursor' | 'idAfter'>;
  updateParams: (newParams: Partial<Omit<P, 'cursor' | 'idAfter'>>, options?: Partial<{ignoreFetch: boolean}>) => void;

  cursorState: CursorState;
  hasNext: () => boolean;

  /** 번호형 페이지네이션 현재 페이지 (1부터 시작) */
  page: number;
  /** cursorState.totalCount 와 params.limit 기준으로 계산한 전체 페이지 수 */
  totalPages: () => number;
  /**
   * 지정한 페이지로 이동한다. 서버가 offset 이 아닌 커서(keyset) 방식만 지원하므로,
   * 이미 방문해 커서를 아는 페이지는 바로 이동하고 아직 모르는 먼 페이지는 그 사이 페이지를
   * 순차적으로 이어서 조회해 커서를 알아낸 뒤 이동한다. 존재하지 않는 페이지를 요청하면
   * 마지막 페이지로 대체된다.
   */
  goToPage: (page: number, options?: {
    throwError?: boolean;
    ignoreLoading?: boolean;
  }) => Promise<void>;

  fetch: (options?: {
    throwError?: boolean;
    ignoreLoading?: boolean;
  }) => Promise<void>;
  fetchMore: (options?: {
    throwError?: boolean;
    ignoreLoading?: boolean;
  }) => Promise<void>;
  clearData: () => void;

  loading: boolean;

  error?: string;
  setError: (error: string) => void;
  clearError: () => void;

  clear: () => void;
}

export interface CursorState {
  nextCursor?: string;
  nextIdAfter?: string;
  hasNext: boolean;
  totalCount: number;
}