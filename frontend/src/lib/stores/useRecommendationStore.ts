import {create} from 'zustand';
import type {RecommendationDto, RecommendationParams} from '@/lib/api/types';
import {getAiRecommendation, getRecommendation} from '@/lib/api/recommendations';
import type {BaseStore} from './types';
import {createBaseStoreActions} from "@/lib/stores/actions.ts";

interface RecommendationStore extends BaseStore<RecommendationDto, RecommendationParams> {
  fetchAiRecommendation: (prompt: string) => Promise<void>;
}

export const useRecommendationStore = create<RecommendationStore>((set, get) => {
  const base = createBaseStoreActions({set, get, fetchApi: getRecommendation});
  let requestId = 0;
  let activeWeatherId: string | undefined;

  const fetchLatest = async (
      fetchApi: () => Promise<RecommendationDto>,
      options?: {ignoreLoading?: boolean; throwError?: boolean}
  ) => {
    const weatherId = get().params.weatherId;
    if (!weatherId || (get().loading && activeWeatherId === weatherId && !options?.ignoreLoading)) return;

    const id = ++requestId;
    activeWeatherId = weatherId;
    const isLatest = () => id === requestId && get().params.weatherId === weatherId;
    set({loading: true, error: undefined,
      data: get().data?.weatherId === weatherId ? get().data : null});
    try {
      const data = await fetchApi();
      if (isLatest()) set({data});
    } catch (error) {
      if (isLatest()) {
        console.error(error);
        set({error: (error as Error).message || '추천 요청에 실패했습니다.'});
        if (options?.throwError) throw error;
      }
    } finally {
      if (isLatest()) set({loading: false});
    }
  };

  return {
    ...base,
    // 날씨가 바뀐 요청은 진행 중인 이전 날씨 요청을 기다리지 않는다.
    fetch: (options) => {
      const params = {...get().params};
      return fetchLatest(() => getRecommendation(params), options);
    },
    fetchAiRecommendation: (prompt) => {
      const weatherId = get().params.weatherId;
      return fetchLatest(() => getAiRecommendation(weatherId, prompt));
    },
    clearData: () => {
      ++requestId;
      activeWeatherId = undefined;
      base.clearData();
    },
  };
});
