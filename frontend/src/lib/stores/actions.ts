import type {BaseStore, CursorState, ListStore, PaginatedStore} from "@/lib/stores/types.ts";
import type {CursorParams, CursorResponse} from "@/lib/api";

type SetType<S> = {
  (partial: S | Partial<S> | ((state: S) => S | Partial<S>), replace?: false | undefined): void;
}

export function createBaseStoreActions<T, P>(
    {set, get, fetchApi, initialData} : {
      set: SetType<BaseStore<T, P>>,
      get: () => BaseStore<T, P>,
      fetchApi: (p: P) => Promise<T>,
      initialData?: Partial<{
        data: T | null;
        params: P;
      }>
    }
): BaseStore<T, P> {
  const _initialData = {data: null, params: {} as P, ...initialData};

  return ({
    data: _initialData.data,
    update: newData => {
      set((state) => ({
        data: state.data ? { ...state.data, ...newData } : null,
      }))
    },

    params: _initialData.params,
    updateParams: (newParams, options) => {
      const _options = {autoFetch: true, ...options};
      set((state) => ({
        params: { ...state.params, ...newParams },
      }))
      if (_options.autoFetch) {
        get().fetch();
      }
    },

    fetch: async (options) => {
      const {loading} = get();
      if (loading && options?.ignoreLoading !== true) {
        console.warn(`로딩 중이므로 요청이 무시되었습니다.`);
        return;
      }

      try {
        set({loading: true, error: undefined});

        const {params} = get();
        const result = await fetchApi(params);

        set({data: result});

      } catch (error) {
        console.error(error);
        set({error: (error as Error).message || '알 수 없는 오류가 발생했습니다.'});

        if (options?.throwError) {
          throw error;
        }
      } finally {
        set({loading: false});
      }
    },
    clearData: () => {
      set({
        ..._initialData,
        loading: false,
      })
    },

    loading: false,

    error: undefined,
    setError: (error: string) => {set({error})},
    clearError: () => {set({error: undefined})},

    clear: () => {
      const { clearData, clearError } = get();
      clearData();
      clearError();
    }
  })
}
export function createListStoreActions<T, P>(
    {set, get, fetchApi, initialData, keyExtractor = e => (e as never)['id'] as string} : {
      set: SetType<ListStore<T, P>>,
      get: () => ListStore<T, P>,
      fetchApi: (p: P) => Promise<T[]>,
      initialData?: {
        data?: T[];
        params?: P;
      },
      keyExtractor?: (e: T) => string
    }
): ListStore<T, P> {
  const _initialData = {data: [], params: {} as P, ...initialData};
  return ({
    data: _initialData.data,
    add: newItem => {
      set(state => {
        const data = state.data;
        const newItemKey = keyExtractor(newItem);
        const isDuplicate = data.some(item => keyExtractor(item) === newItemKey);
        if (isDuplicate) {
          console.warn(`중복된 아이템이 감지되었습니다: ${newItemKey}`);
          return {data};
        }

        const params = state.params;
        const sortBy = (params as any)['sortBy'];
        const sortDirection = (params as any)['sortDirection'];

        // 정렬 기준이 없으면 맨 앞에 추가
        if (!sortBy || !sortDirection) {
          return {data: [newItem, ...data]};
        }

        // 정렬된 위치 찾기
        const newItems = [...data, newItem];
        const sortedData = newItems.sort((a, b) => {
          const aValue = (a as any)[sortBy];
          const bValue = (b as any)[sortBy];

          if (sortDirection === 'ASCENDING') {
            return aValue > bValue ? 1 : -1;
          } else {
            return aValue < bValue ? 1 : -1;
          }
        });
        return {data: sortedData};
      })
    },
    update: (id, newData) => {
      set((state) => ({
        data: state.data.map((item) =>
            keyExtractor(item) === id ? { ...item, ...newData } : item
        ),
      }))
    },
    delete: (id) => {
      set((state) => ({
        data: state.data.filter((item) => keyExtractor(item) !== id),
      }))
    },
    count: () => get().data.length,

    params: _initialData.params,
    updateParams: (newParams, options) => {
      const _options = {autoFetch: true, ...options};
      set((state) => ({
        params: { ...state.params, ...newParams },
      }))
      if (_options.autoFetch) {
        get().fetch();
      }
    },

    fetch: async (options) => {
      const {loading} = get();
      if (loading && options?.ignoreLoading !== true) {
        console.warn(`로딩 중이므로 요청이 무시되었습니다.`);
        return;
      }

      try {
        set({loading: true, error: undefined, data: []});

        const {params} = get();
        const result = await fetchApi(params);

        set({data: result});

      } catch (error) {
        console.error(error);
        set({error: (error as Error).message || '알 수 없는 오류가 발생했습니다.'});

        if (options?.throwError) {
          throw error;
        }
      } finally {
        set({loading: false});
      }
    },
    clearData: () => {
      set({
        ..._initialData,
        loading: false,
      })
    },

    loading: false,

    error: undefined,
    setError: (error: string) => {set({error})},
    clearError: () => {set({error: undefined})},

    clear: () => {
      const { clearData, clearError } = get();
      clearData();
      clearError();
    }
  })
}

export function createPaginatedStoreActions<T, P extends CursorParams>(
    {set, get, fetchApi, initialData, keyExtractor = e => (e as never)['id'] as string} : {
      set: SetType<PaginatedStore<T, P>>,
      get: () => PaginatedStore<T, P>,
      fetchApi: (p: P) => Promise<CursorResponse<T>>,
      initialData?: Partial<{
        data: T[];
        params: P;
        cursorState: CursorState;
      }>,
      keyExtractor?: (e: T) => string
    }
): PaginatedStore<T, P> {
  const _initialData = {data: [], params: {limit: 20} as P, cursorState: {hasNext: false, totalCount: 0}, ...initialData};

  // 번호형 페이지 이동용 커서 캐시. pageCursors[n] 은 "n 페이지를 조회할 때 쓸 커서" 다.
  // 서버가 keyset 방식만 지원해서, n 페이지 커서는 n-1 페이지를 실제로 조회해야만 알 수 있다.
  type PageCursor = { cursor?: string; idAfter?: string };
  let pageCursors: Record<number, PageCursor> = {1: {cursor: undefined, idAfter: undefined}};

  return ({
    data: _initialData.data,
    add: newItem => {
      set(state => {
        const data = state.data;
        const newItemKey = keyExtractor(newItem);
        const isDuplicate = data.some(item => keyExtractor(item) === newItemKey);
        if (isDuplicate) {
          console.warn(`중복된 아이템이 감지되었습니다: ${newItemKey}`);
          return {data};
        }

        const cursorState: CursorState = {...state.cursorState, totalCount: state.cursorState.totalCount+1}

        const params = state.params;
        const sortBy = (params as any)['sortBy'];
        const sortDirection = (params as any)['sortDirection'];

        // 정렬 기준이 없으면 맨 앞에 추가
        if (!sortBy || !sortDirection) {
          return {data: [newItem, ...data], cursorState};
        }

        // 정렬된 위치 찾기
        const newItems = [...data, newItem];
        const sortedData = newItems.sort((a, b) => {
          const aValue = (a as any)[sortBy];
          const bValue = (b as any)[sortBy];

          if (sortDirection === 'ASCENDING') {
            return aValue > bValue ? 1 : -1;
          } else {
            return aValue < bValue ? 1 : -1;
          }
        });
        return {data: sortedData, cursorState};
      })
    },
    update: (id, newData) => {
      set((state) => ({
        data: state.data.map((item) =>
            keyExtractor(item) === id ? { ...item, ...newData } : item
        ),
      }))
    },
    delete: (id) => {
      set((state) => ({
        data: state.data.filter((item) => keyExtractor(item) !== id),
        cursorState: {...state.cursorState, totalCount: state.cursorState.totalCount-1}
      }))
    },
    count: () => get().cursorState.totalCount,

    params: _initialData.params,
    updateParams: (newParams, options) => {
      const _options = {autoFetch: true, ...options};
      set((state) => ({
        params: { ...state.params, ...newParams },
      }))
      if (_options.autoFetch) {
        get().fetch();
      }
    },
    cursorState: _initialData.cursorState,
    hasNext: () => get().cursorState.hasNext,

    page: 1,
    totalPages: () => {
      const {cursorState, params} = get();
      const limit = (params as CursorParams).limit || 1;
      return Math.max(1, Math.ceil(cursorState.totalCount / limit));
    },

    fetch: async (options) => {
      const {loading} = get();
      if (loading && options?.ignoreLoading !== true) {
        console.warn(`로딩 중이므로 요청이 무시되었습니다.`);
        return;
      }

      try {
        set({
          loading: true,
          error: undefined,
          data: [],
          page: 1,
          cursorState: {hasNext: false, totalCount: 0},
        });
        pageCursors = {1: {cursor: undefined, idAfter: undefined}};

        const {params} = get();
        const result = await fetchApi({...params, cursor: undefined, idAfter: undefined} as P);

        pageCursors[2] = {cursor: result.nextCursor, idAfter: result.nextIdAfter};

        set({
          data: result.data,
          cursorState: {
            nextCursor: result.nextCursor,
            nextIdAfter: result.nextIdAfter,
            hasNext: result.hasNext,
            totalCount: result.totalCount,
          },
        });

      } catch (error) {
        console.error(error);
        set({error: (error as Error).message || '알 수 없는 오류가 발생했습니다.'});

        if (options?.throwError) {
          throw error;
        }
      } finally {
        set({loading: false});
      }
    },
    goToPage: async (targetPage, options) => {
      if (targetPage < 1) return;

      const {loading} = get();
      if (loading && options?.ignoreLoading !== true) {
        console.warn(`로딩 중이므로 요청이 무시되었습니다.`);
        return;
      }

      try {
        set({loading: true, error: undefined});

        const {params} = get();
        const lastKnownPage = Math.max(...Object.keys(pageCursors).map(Number));
        // 이미 커서를 아는 페이지면 그대로 이동하고, 모르는 먼 페이지면 아는 마지막 페이지부터 이어서 조회한다.
        let p = Math.min(targetPage, lastKnownPage);
        let result: CursorResponse<T>;

        while (true) {
          const cursor = pageCursors[p];
          result = await fetchApi({...params, cursor: cursor?.cursor, idAfter: cursor?.idAfter} as P);
          pageCursors[p + 1] = {cursor: result.nextCursor, idAfter: result.nextIdAfter};

          // 목표 페이지에 도달했거나, 더 이상 페이지가 없어 요청한 페이지가 존재하지 않으면 멈춘다.
          if (p >= targetPage || !result.hasNext) break;
          p += 1;
        }

        set({
          data: result.data,
          page: p,
          cursorState: {
            nextCursor: result.nextCursor,
            nextIdAfter: result.nextIdAfter,
            hasNext: result.hasNext,
            totalCount: result.totalCount,
          },
        });

      } catch (error) {
        console.error(error);
        set({error: (error as Error).message || '알 수 없는 오류가 발생했습니다.'});

        if (options?.throwError) {
          throw error;
        }
      } finally {
        set({loading: false});
      }
    },
    fetchMore: async (options) => {
      const {loading, cursorState} = get();
      if (loading && options?.ignoreLoading !== true) {
        console.warn(`로딩 중이므로 요청이 무시되었습니다.`);
        return;
      }
      if (!cursorState.hasNext) {
        console.warn(`더 이상 데이터가 없으므로 요청이 무시되었습니다.`);
        return;
      }

      try {
        set({loading: true, error: undefined});

        const {params, data} = get();
        const result = await fetchApi({...params, cursor: cursorState.nextCursor, idAfter: cursorState.nextIdAfter} as P);

        set({
          data: [...data, ...result.data],
          cursorState: {
            nextCursor: result.nextCursor,
            nextIdAfter: result.nextIdAfter,
            hasNext: result.hasNext,
            totalCount: result.totalCount,
          },
        });

      } catch (error) {
        console.error(error);
        set({error: (error as Error).message || '알 수 없는 오류가 발생했습니다.'});

        if (options?.throwError) {
          throw error;
        }
      } finally {
        set({loading: false});
      }
    },
    clearData: () => {
      pageCursors = {1: {cursor: undefined, idAfter: undefined}};
      set({
        ..._initialData,
        page: 1,
        loading: false,
      })
    },

    loading: false,

    error: undefined,
    setError: (error: string) => {set({error})},
    clearError: () => {set({error: undefined})},

    clear: () => {
      const { clearData, clearError } = get();
      clearData();
      clearError();
    }
  })
}
