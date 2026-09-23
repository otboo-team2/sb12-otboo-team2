import {apiClient} from './client';
import type {CursorParams, CursorResponse, DirectMessageDto, DirectMessageParams, DmConversationDto} from './types';

/**
 * DM 목록 조회
 */
export const getDms = async (params: DirectMessageParams): Promise<CursorResponse<DirectMessageDto>> => {
  return apiClient.get<CursorResponse<DirectMessageDto>>('/api/direct-messages', {
    params
  });
};

/**
 * 대화 목록 조회 (상대별 최근 메시지)
 */
export const getDmConversations = async (params: CursorParams): Promise<CursorResponse<DmConversationDto>> => {
    return apiClient.get<CursorResponse<DmConversationDto>>('/api/direct-messages/conversations', { params });
};
