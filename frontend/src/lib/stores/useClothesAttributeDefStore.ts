import {create} from 'zustand';
import type {ClothesAttributeDefDto, ClothesAttributeDefListParams} from '@/lib/api/types';
import {getClothesAttributeDef} from '@/lib/api/clothes-attributes';
import {type PaginatedStore} from './types';
import {createPaginatedStoreActions} from "@/lib/stores/actions.ts";

interface ClothesAttributeDefStore extends PaginatedStore<ClothesAttributeDefDto, ClothesAttributeDefListParams> {
  fetchAll: (limit?: number) => Promise<void>;
}

export const useClothesAttributeDefStore = create<ClothesAttributeDefStore>((set, get) => ({
  ...createPaginatedStoreActions({
    set, get,
    fetchApi: getClothesAttributeDef,
    initialData: {
      params: {
        limit: 20,
        sortBy: 'name',
        sortDirection: 'ASCENDING',
        keywordLike: undefined
      }
    }
  }),
  fetchAll: async (limit = 100) => {
    const previousLimit = get().params.limit;

    set((state) => ({
      params: {
        ...state.params,
        limit,
        keywordLike: undefined,
      },
      data: [],
      cursorState: {
        hasNext: false,
        totalCount: 0,
      },
    }));

    try {
      await get().fetch();
      while (get().hasNext()) {
        await get().fetchMore();
      }
    } finally {
      set((state) => ({
        params: {
          ...state.params,
          limit: previousLimit,
        },
      }));
    }
  },
}));
