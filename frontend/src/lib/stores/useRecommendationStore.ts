import {create} from 'zustand';
import type {RecommendationDto, RecommendationParams} from '@/lib/api/types';
import {getAiRecommendation, getRecommendation} from '@/lib/api/recommendations';
import type {BaseStore} from './types';
import {createBaseStoreActions} from "@/lib/stores/actions.ts";

interface RecommendationStore extends BaseStore<RecommendationDto, RecommendationParams> {
  lastAiPrompt?: string;
  seenClothesIds: string[];
  fetchAiRecommendation: (prompt: string, excludeClothesIds?: string[]) => Promise<void>;
  fetchAlternative: () => Promise<void>;
}

const clothesIds = (recommendation: RecommendationDto) =>
  recommendation.clothes?.map(clothes => clothes.clothesId) ?? [];

const uniqueIds = (...groups: string[][]) => [...new Set(groups.flat())];

export const useRecommendationStore = create<RecommendationStore>((set, get) => {
  const base = createBaseStoreActions({set, get, fetchApi: getRecommendation});
  let requestId = 0;
  let activeWeatherId: string | undefined;

  const fetchLatest = async (
      fetchApi: () => Promise<RecommendationDto>,
      options?: {ignoreLoading?: boolean; throwError?: boolean},
      onSuccess?: (data: RecommendationDto) => void,
      onError?: () => void,
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
      if (isLatest()) {
        set({data});
        onSuccess?.(data);
      }
    } catch (error) {
      if (isLatest()) {
        console.error(error);
        set({error: (error as Error).message || '추천 요청에 실패했습니다.'});
        onError?.();
        if (options?.throwError) throw error;
      }
    } finally {
      if (isLatest()) set({loading: false});
    }
  };

  return {
    ...base,
    lastAiPrompt: undefined,
    seenClothesIds: [],
    // 날씨가 바뀐 요청은 진행 중인 이전 날씨 요청을 기다리지 않는다.
    fetch: (options) => {
      const params = {...get().params};
      set({lastAiPrompt: undefined, seenClothesIds: []});
      return fetchLatest(() => getRecommendation(params), options,
          data => set({seenClothesIds: uniqueIds(clothesIds(data))}));
    },
    fetchAiRecommendation: (prompt, excludeClothesIds = []) => {
      const weatherId = get().params.weatherId;
      const isAlternative = excludeClothesIds.length > 0;
      if (!isAlternative) set({seenClothesIds: []});
      return fetchLatest(() => getAiRecommendation(weatherId, prompt, excludeClothesIds), undefined,
          data => set({
            lastAiPrompt: prompt,
            seenClothesIds: isAlternative
              ? uniqueIds(get().seenClothesIds, excludeClothesIds, clothesIds(data))
              : uniqueIds(clothesIds(data)),
          }));
    },
    fetchAlternative: () => {
      const {data, lastAiPrompt, seenClothesIds} = get();
      const excludeClothesIds = uniqueIds(seenClothesIds, data ? clothesIds(data) : []);
      const params = {...get().params};
      let cycleReset = false;
      const request = (excludedIds: string[]) => lastAiPrompt
        ? getAiRecommendation(params.weatherId, lastAiPrompt, excludedIds)
        : getRecommendation(params, excludedIds);
      return fetchLatest(async () => {
        const alternative = await request(excludeClothesIds);
        if (alternative.clothes.length > 0 || excludeClothesIds.length === 0) return alternative;
        cycleReset = true;
        return request([]);
      }, undefined, result => set({
        seenClothesIds: cycleReset
          ? uniqueIds(clothesIds(result))
          : uniqueIds(excludeClothesIds, clothesIds(result)),
      }), () => {
        if (cycleReset) set({seenClothesIds: []});
      });
    },
    clearData: () => {
      ++requestId;
      activeWeatherId = undefined;
      set({lastAiPrompt: undefined, seenClothesIds: []});
      base.clearData();
    },
  };
});
