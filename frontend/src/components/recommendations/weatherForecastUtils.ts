import type {WeatherDto} from '@/lib/api/types';

export function dailyRepresentativeWeathers(weathers: WeatherDto[]): WeatherDto[] {
  const byDate = new Map<string, WeatherDto[]>();
  for (const weather of weathers) {
    const date = new Date(weather.forecastAt);
    const key = `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`;
    byDate.set(key, [...(byDate.get(key) ?? []), weather]);
  }
  return [...byDate.values()].slice(0, 6).map((forecasts) => forecasts[0]);
}
