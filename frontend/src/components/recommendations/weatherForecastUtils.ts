import type {WeatherDto} from '@/lib/api/types';

const SEOUL_TIME_ZONE = 'Asia/Seoul';

function seoulDateParts(value: string | Date) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: SEOUL_TIME_ZONE,
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
  }).formatToParts(new Date(value));
  return Object.fromEntries(parts
    .filter(({type}) => type === 'year' || type === 'month' || type === 'day')
    .map(({type, value: partValue}) => [type, Number(partValue)])) as {
    year: number;
    month: number;
    day: number;
  };
}

export function seoulDateKey(value: string | Date): string {
  const {year, month, day} = seoulDateParts(value);
  return `${year}-${month}-${day}`;
}

function closestForecast(forecasts: WeatherDto[], target: number, futureOnly = false): WeatherDto {
  const candidates = futureOnly
    ? forecasts.filter(({forecastAt}) => new Date(forecastAt).getTime() >= target)
    : forecasts;
  const source = candidates.length > 0 ? candidates : forecasts;
  return source.reduce((closest, weather) =>
    Math.abs(new Date(weather.forecastAt).getTime() - target)
      < Math.abs(new Date(closest.forecastAt).getTime() - target)
      ? weather
      : closest,
  );
}

function seoulNoonTimestamp(dateKey: string): number {
  const [year, month, day] = dateKey.split('-').map(Number);
  // Seoul is UTC+09:00, so local noon is 03:00 UTC.
  return Date.UTC(year, month - 1, day, 3);
}

export function dailyRepresentativeWeathers(
  weathers: WeatherDto[],
  now: Date = new Date(),
): WeatherDto[] {
  const byDate = new Map<string, WeatherDto[]>();
  for (const weather of weathers) {
    const key = seoulDateKey(weather.forecastAt);
    byDate.set(key, [...(byDate.get(key) ?? []), weather]);
  }
  const todayKey = seoulDateKey(now);
  return [...byDate.entries()].slice(0, 5).map(([dateKey, forecasts]) => {
    const representative = dateKey === todayKey
      ? closestForecast(forecasts, now.getTime(), true)
      : closestForecast(forecasts, seoulNoonTimestamp(dateKey));
    const currentTemperatures = forecasts.map(({temperature}) => temperature.current);
    const min = Math.min(...currentTemperatures);
    const max = Math.max(...currentTemperatures);
    return {
      ...representative,
      temperature: {...representative.temperature, min, max},
    };
  });
}
