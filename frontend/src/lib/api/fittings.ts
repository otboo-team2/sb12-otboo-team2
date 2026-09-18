import { apiClient } from './client';
import type { VirtualTryOnRequest, VirtualTryOnJobDto } from './types';

/**
 * 가상피팅 생성 요청. 202 Accepted + PENDING 상태 job을 즉시 반환한다.
 * 실제 결과는 getFittingJob으로 polling해서 받아야 한다.
 */
export const submitFitting = async (
    request: VirtualTryOnRequest,
    modelImage?: File
): Promise<VirtualTryOnJobDto> => {
    const formData = new FormData();
    formData.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }));

    if (modelImage) {
        formData.append('modelImage', modelImage);
    }

    return apiClient.postFormData<VirtualTryOnJobDto>('/api/fittings', formData);
};

/**
 * 가상피팅 job 상태 조회 (polling용)
 */
export const getFittingJob = async (jobId: string): Promise<VirtualTryOnJobDto> => {
    return apiClient.get<VirtualTryOnJobDto>(`/api/fittings/${jobId}`);
};
