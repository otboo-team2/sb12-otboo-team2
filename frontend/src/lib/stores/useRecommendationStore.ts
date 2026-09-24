import {create} from 'zustand';
import type {RecommendationDto, RecommendationParams} from '@/lib/api/types';
import {getAiRecommendation, getRecommendation} from '@/lib/api/recommendations';
import type {BaseStore} from './types';
import {createBaseStoreActions} from "@/lib/stores/actions.ts";

interface RecommendationStore extends BaseStore<RecommendationDto, RecommendationParams> {
  inputPrompt: string;
  recommendationMode: 'BASE' | 'AI';
  seenOutfits: string[][];
  setInputPrompt: (prompt: string) => void;
  fetchAiRecommendation: (prompt: string, excludeClothesIds?: string[]) => Promise<void>;
  fetchAlternative: () => Promise<void>;
}

const clothesIds = (recommendation: RecommendationDto) =>
  recommendation.clothes?.map(clothes => clothes.clothesId) ?? [];

const canonicalOutfit = (ids: string[]) => [...new Set(ids)].sort();

const addOutfit = (history: string[][], ids: string[]) => {
  const outfit = canonicalOutfit(ids);
  if (outfit.length === 0 || history.some(seen => seen.join(',') === outfit.join(','))) return history;
  return [...history, outfit];
};

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
    inputPrompt: '',
    recommendationMode: 'BASE',
    seenOutfits: [],
    setInputPrompt: inputPrompt => set({inputPrompt}),
    // 날씨가 바뀐 요청은 진행 중인 이전 날씨 요청을 기다리지 않는다.
    fetch: (options) => {
      const params = {...get().params};
      set({recommendationMode: 'BASE', seenOutfits: []});
      return fetchLatest(() => getRecommendation(params), options,
          data => set({seenOutfits: addOutfit([], clothesIds(data))}));
    },
    fetchAiRecommendation: (prompt, excludeClothesIds = []) => {
      const weatherId = get().params.weatherId;
      const isAlternative = excludeClothesIds.length > 0;
      return fetchLatest(() => getAiRecommendation(weatherId, prompt, excludeClothesIds), undefined,
          data => set({
            inputPrompt: '',
            recommendationMode: 'AI',
            seenOutfits: addOutfit(isAlternative ? get().seenOutfits : [], clothesIds(data)),
          }));
    },
    fetchAlternative: () => {
      const {data, inputPrompt, recommendationMode, seenOutfits} = get();
      const currentPrompt = inputPrompt.trim();
      const targetMode = currentPrompt ? 'AI' : 'BASE';
      const startsNewCycle = targetMode === 'AI' || recommendationMode !== targetMode;
      const history = startsNewCycle
        ? []
        : data ? addOutfit(seenOutfits, clothesIds(data)) : seenOutfits;
      const params = {...get().params};
      let cycleReset = false;
      const request = (excludedOutfits: string[][]) => currentPrompt
        ? getAiRecommendation(params.weatherId, currentPrompt, [], excludedOutfits)
        : getRecommendation(params, [], excludedOutfits);
      return fetchLatest(async () => {
        const alternative = await request(history);
        if (alternative.clothes.length > 0 || history.length === 0) return alternative;
        cycleReset = true;
        return request([]);
      }, undefined, result => set({
        inputPrompt: '',
        recommendationMode: targetMode,
        seenOutfits: addOutfit(cycleReset ? [] : history, clothesIds(result)),
      }), () => {
        if (cycleReset) set({seenOutfits: []});
      });
    },
    clearData: () => {
      ++requestId;
      activeWeatherId = undefined;
      set({inputPrompt: '', recommendationMode: 'BASE', seenOutfits: []});
      base.clearData();
    },
  };
});
