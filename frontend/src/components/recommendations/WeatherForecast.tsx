import {type SkyStatus, type WeatherDto} from '@/lib/api/types';
import {useWeatherStore} from '@/lib/stores/useWeatherStore';

import sunnyIcon from '@/assets/illust_logos/il_Sunny.svg';
import overcastIcon from '@/assets/illust_logos/il_Overcast.svg';
import cloudyIcon from '@/assets/illust_logos/il_cloudy.svg';
import {useEffect, useMemo} from "react";
import {dailyRepresentativeWeathers, seoulDateKey} from './weatherForecastUtils';

function WeatherIcon({ skyStatus }: { skyStatus: SkyStatus }) {
  switch (skyStatus) {
    case 'CLEAR':
      return (
        <div className="overflow-clip relative shrink-0 size-10">
          <img alt="맑음" className="block max-w-none size-full" src={sunnyIcon} />
        </div>
      );
    case 'MOSTLY_CLOUDY':
      return (
        <div className="overflow-clip relative shrink-0 size-10">
          <img alt="구름많음" className="block max-w-none size-full" src={cloudyIcon} />
        </div>
      );
    case 'CLOUDY':
      return (
        <div className="overflow-clip relative shrink-0 size-10">
          <img alt="흐림" className="block max-w-none size-full" src={overcastIcon} />
        </div>
      );
    default:
      return (
        <div className="overflow-clip relative shrink-0 size-10">
          <img alt="맑음" className="block max-w-none size-full" src={sunnyIcon} />
        </div>
      );
  }
}

export default function WeatherForecast() {
  const { data: weathers, loading, selectedWeather, selectWeather } = useWeatherStore();
  const dailyWeathers = useMemo(
    () => dailyRepresentativeWeathers(weathers ?? []),
    [weathers],
  );

  const nearestWeather = useMemo(() => {
    const forecasts = weathers ?? [];
    if (forecasts.length === 0) return undefined;

    const now = Date.now();
    const futureForecasts = forecasts.filter(
      (weather) => new Date(weather.forecastAt).getTime() >= now,
    );
    const candidates = futureForecasts.length > 0 ? futureForecasts : forecasts;

    return candidates.reduce((nearest, weather) =>
      Math.abs(new Date(weather.forecastAt).getTime() - now)
        < Math.abs(new Date(nearest.forecastAt).getTime() - now)
        ? weather
        : nearest,
    );
  }, [weathers]);

  useEffect(() => {
    const selectedStillAvailable = selectedWeather
      && weathers?.some((weather) => weather.id === selectedWeather.id);
    if (nearestWeather && !selectedStillAvailable) {
      selectWeather(nearestWeather);
    }
  }, [nearestWeather, selectedWeather, selectWeather, weathers])

  if (loading || !weathers || weathers.length === 0) {
    return (
      <div className="backdrop-blur-[15px] backdrop-filter bg-white/70 box-border flex items-start overflow-hidden px-2 py-4 relative rounded-[24px] shrink-0 w-full md:px-4 lg:px-6">
        <div className="absolute border border-gray-200 border-solid inset-0 pointer-events-none rounded-[30px]" />
        
        {/* Skeleton for 5 weather items */}
        {Array.from({ length: 6 }).map((_, index) => (
          <div key={index} className={`content-stretch flex flex-1 min-w-[118px] flex-col gap-1.5 items-center justify-center relative shrink-0 ${index > 0 ? 'border-l border-gray-200' : ''}`}>
            {/* Date skeleton */}
            <div className="h-4 w-12 bg-gray-200 rounded animate-pulse" />
            {/* Icon skeleton */}
            <div className="size-10 bg-gray-200 rounded animate-pulse" />
            {/* Temperature skeleton */}
            <div className="h-4 w-8 bg-gray-200 rounded animate-pulse" />
          </div>
        ))}
      </div>
    );
  }
  const getForecastDate = (forecastAt: string) => {
    const targetKey = seoulDateKey(forecastAt);
    const todayKey = seoulDateKey(new Date());
    const toDayNumber = (key: string) => Date.parse(`${key}T00:00:00Z`);
    const dayOffset = Math.round((toDayNumber(targetKey) - toDayNumber(todayKey)) / 86_400_000);
    if (dayOffset === 0) return "오늘";
    if (dayOffset === 1) return "내일";
    if (dayOffset === 2) return "모레";
    return '';
  };

  const getDateText = (forecastAt: string) => {
    const parts = new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    month: 'numeric',
    day: 'numeric',
    weekday: 'short',
    }).formatToParts(new Date(forecastAt));
    const month = parts.find((part) => part.type === 'month')?.value ?? '';
    const day = parts.find((part) => part.type === 'day')?.value ?? '';
    const weekday = parts.find((part) => part.type === 'weekday')?.value ?? '';
    return `${month}.${day} (${weekday})`;
  };

  const getSkyStatus = (weather?: WeatherDto) => {
    return weather?.skyStatus || 'CLEAR';
  };

  const displayTemp = (temp?: number) => temp != null ? `${Math.round(temp)}°` : '-';

  return (
    <div className="backdrop-blur-[15px] backdrop-filter bg-white/70 box-border flex overflow-hidden px-1 py-1 relative rounded-[30px] shrink-0 w-full md:px-3 md:py-2 lg:px-4">
        <div className="absolute border border-gray-200 border-solid inset-0 pointer-events-none rounded-[30px]" />

        {
          dailyWeathers.map((weather, index) => {
            const date = index === 0 ? '오늘' : index === 1 ? '내일' : index === 2 ? '모레' : getForecastDate(weather.forecastAt);
            const isToday = date === '오늘';
            const selectedForDate = isToday && nearestWeather ? nearestWeather : weather;
            const { temperature } = selectedForDate;
            const skyStatus = getSkyStatus(selectedForDate)
            const isSelected = selectedWeather?.id === selectedForDate.id;

            return (
              <div key={weather.id} className="flex flex-1 min-w-0">
                <div
                    className={`content-stretch flex min-h-[148px] min-w-0 flex-1 flex-col gap-1 items-center justify-center relative shrink-0 cursor-pointer px-2 py-2 ${index > 0 ? 'border-l border-gray-200' : ''} ${isSelected ? 'bg-blue-50/70' : 'hover:bg-gray-50'}`}
                    onClick={() => selectWeather(selectedForDate)}
                >
                  <div className={`font-${isSelected ? 'extrabold' : 'bold'} leading-tight not-italic relative flex h-[52px] flex-col items-center justify-start shrink-0 text-sm text-center tracking-[-0.35px] ${isSelected ? 'text-blue-500' : 'text-gray-800'}`}>
                    <p className="h-6 text-lg font-bold">{date}</p>
                    <p className={`mt-0 h-5 text-sm font-semibold ${isSelected ? 'text-blue-500' : 'text-gray-500'}`}>{getDateText(weather.forecastAt)}</p>
                  </div>

                  <div>
                    <div><WeatherIcon skyStatus={skyStatus} /></div>

                      <div className="font-extrabold leading-none not-italic relative shrink-0 text-gray-800 text-2xl text-center text-nowrap tracking-[-0.6px]">
                        <p className="leading-normal whitespace-pre">{displayTemp(temperature.current)}</p>
                      </div>
                      <div className="mt-0.5 text-sm font-semibold text-gray-500">
                        {displayTemp(temperature.min)} / {displayTemp(temperature.max)}
                      </div>
                  </div>
                </div>
              </div>
            )
          })
        }
    </div>
  );
}
