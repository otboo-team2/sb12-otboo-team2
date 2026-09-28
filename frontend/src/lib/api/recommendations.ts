import { apiClient } from './client';
import type {
  RecommendationParams,
  RecommendationDto,
  OutfitReferenceParams,
  OutfitReferencesDto
} from './types';

/**
 * 추천 조회
 */
export const getRecommendation = async (
  params: RecommendationParams,
  excludeClothesIds: string[] = [],
  excludedOutfits: string[][] = [],
): Promise<RecommendationDto> => {
  return apiClient.get<RecommendationDto>('/api/recommendations', {
    params: {
      ...params,
      excludeClothesIds: excludeClothesIds.length > 0 ? excludeClothesIds.join(',') : undefined,
      excludedOutfits: excludedOutfits.length > 0
        ? excludedOutfits.map(outfit => outfit.join(',')).join(';')
        : undefined,
    },
  });
};

/**
 * 자연어 요청으로 AI 추천 조회
 */
export const getAiRecommendation = async (
  weatherId: string,
  prompt: string,
  excludeClothesIds: string[] = [],
  excludedOutfits: string[][] = [],
): Promise<RecommendationDto> => {
  return apiClient.post<RecommendationDto>('/api/recommendations/ai', {
    weatherId,
    prompt,
    excludeClothesIds,
    excludedOutfits,
  }, { timeout: 70000 });
};

/**
 * 날씨에 맞는 코디 참고 사진 조회 (Pinterest 동기화 핀)
 *
 * 스타일은 쉼표로 이어 보낸다. axios 기본 직렬화는 배열을 styles[]=A&styles[]=B 로 보내는데
 * 서버(Spring)는 styles[] 를 받지 못한다.
 */
export const getOutfitReferences = async (
  { weatherId, styles, limit }: OutfitReferenceParams,
): Promise<OutfitReferencesDto> => {
  return apiClient.get<OutfitReferencesDto>('/api/recommendations/outfit-references', {
    params: {
      weatherId,
      styles: styles && styles.length > 0 ? styles.join(',') : undefined,
      limit,
    },
  });
};
