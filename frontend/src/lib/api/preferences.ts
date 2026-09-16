import { apiClient } from './client';

export interface UserPreferenceDto {
  selectableValueId: string;
  definitionName: string;
  value: string;
}

export const getUserPreferences = async (userId: string): Promise<UserPreferenceDto[]> => {
  return apiClient.get<UserPreferenceDto[]>(`/api/users/${userId}/preferences`);
};

export const replaceUserPreferences = async (
  userId: string,
  selectableValueIds: string[],
): Promise<UserPreferenceDto[]> => {
  return apiClient.put<UserPreferenceDto[]>(`/api/users/${userId}/preferences`, { selectableValueIds });
};
