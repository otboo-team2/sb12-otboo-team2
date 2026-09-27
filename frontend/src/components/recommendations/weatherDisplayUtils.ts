import type {PrecipitationType, SkyStatus} from '@/lib/api/types';

export type WeatherCondition = 'RAIN' | 'SNOW' | SkyStatus;

export function getWeatherCondition(
  skyStatus: SkyStatus | undefined,
  precipitationType: PrecipitationType | undefined,
): WeatherCondition {
  if (precipitationType === 'SNOW' || precipitationType === 'RAIN_SNOW') return 'SNOW';
  if (precipitationType === 'RAIN' || precipitationType === 'SHOWER') return 'RAIN';
  return skyStatus ?? 'CLEAR';
}

export function getWeatherConditionText(condition: WeatherCondition): string {
  switch (condition) {
    case 'RAIN': return '비';
    case 'SNOW': return '눈';
    case 'CLEAR': return '맑음';
    case 'MOSTLY_CLOUDY': return '구름많음';
    case 'CLOUDY': return '흐림';
  }
}
