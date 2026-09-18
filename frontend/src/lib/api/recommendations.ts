import { apiClient } from './client';
import type {
  RecommendationParams,
  RecommendationDto
} from './types';

/**
 * 추천 조회
 */
export const getRecommendation = async (params: RecommendationParams): Promise<RecommendationDto> => {
  return apiClient.get<RecommendationDto>('/api/recommendations', { params });
};

/**
 * 자연어 요청으로 AI 추천 조회
 */
export const getAiRecommendation = async (
  weatherId: string,
  prompt: string,
): Promise<RecommendationDto> => {
  return apiClient.post<RecommendationDto>('/api/recommendations/ai', {
    weatherId,
    prompt,
  }, { timeout: 70000 });
};
