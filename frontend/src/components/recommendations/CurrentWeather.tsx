import imageLocationIcon from '@/assets/icons/ic_local.svg';
import sunnyIcon from '@/assets/illust_logos/il_Sunny.svg';
import overcastIcon from '@/assets/illust_logos/il_Overcast.svg';
import cloudyIcon from '@/assets/illust_logos/il_cloudy.svg';
import rainIcon from '@/assets/illust_logos/il_rain.svg';
import snowIcon from '@/assets/illust_logos/il_snow.svg';
import clearCharacter from '@/assets/weather/clear.png';
import partlyCloudyCharacter from '@/assets/weather/partly-cloudy.png';
import cloudyCharacter from '@/assets/weather/cloudy.png';
import rainCharacter from '@/assets/weather/rain.png';
import snowCharacter from '@/assets/weather/snow.png';
import {CloudRain, Droplets, ThermometerSun, Wind} from 'lucide-react';
import {useWeatherStore} from "@/lib/stores/useWeatherStore.ts";
import {dailyRepresentativeWeathers, seoulDateKey} from './weatherForecastUtils';
import {getWeatherCondition, getWeatherConditionText} from './weatherDisplayUtils';

interface CurrentWeatherProps {
  fetchLocation: () => Promise<void>;
}

export default function CurrentWeather({ fetchLocation }: CurrentWeatherProps) {
  const {data: weathers, selectedWeather: weather} = useWeatherStore();

  const temperature = weather?.temperature?.current;
  const skyStatus = weather?.skyStatus || "CLEAR";
  const dailyWeather = weather && dailyRepresentativeWeathers(weathers ?? []).find(
    (candidate) => seoulDateKey(candidate.forecastAt) === seoulDateKey(weather.forecastAt),
  );
  const tempMin = dailyWeather?.temperature?.min;
  const tempMax = dailyWeather?.temperature?.max;
  const tempDiff = weather?.temperature?.comparedToDayBefore;
  const humidity = weather?.humidity?.current;
  const precipitation = weather?.precipitation || { type: 'NONE', amount: 0, probability: 0 };
  const windSpeed = weather?.windSpeed || { speed: 0, asWord: 'WEAK' };

  const condition = getWeatherCondition(skyStatus, precipitation.type);
  const conditionText = getWeatherConditionText(condition);
  const precipitationText = {NONE: '없음', RAIN: '비', SNOW: '눈', RAIN_SNOW: '비/눈', SHOWER: '소나기'}[precipitation.type] || '없음';

  const weatherIcon = condition === 'RAIN' ? rainIcon : condition === 'SNOW' ? snowIcon : condition === 'CLEAR' ? sunnyIcon : condition === 'CLOUDY' ? overcastIcon : cloudyIcon;
  const isSnow = precipitation.type === 'SNOW';
  const isRain = !isSnow && precipitation.type !== 'NONE' && precipitation.probability > 0;
  const character = isSnow ? snowCharacter : isRain ? rainCharacter : skyStatus === 'MOSTLY_CLOUDY' ? partlyCloudyCharacter : skyStatus === 'CLOUDY' ? cloudyCharacter : clearCharacter;
  const clearSky = skyStatus === 'CLEAR';
  const cloudySky = skyStatus === 'MOSTLY_CLOUDY';
  const characterMessage = isSnow
    ? '눈이 올 수 있어요\n따뜻하게 준비해 주세요!'
    : isRain
      ? '비가 올 수 있어요\n우산을 챙겨주세요!'
      : temperature != null && temperature < 10
        ? `${clearSky ? '맑고 ' : cloudySky ? '구름이 있고 ' : '흐리고 '}쌀쌀한 날씨예요\n따뜻한 외투를 준비해 주세요!`
        : temperature != null && temperature > 28
          ? `${clearSky ? '맑고 ' : cloudySky ? '구름이 있는 ' : '흐린 '}더운 날씨예요\n통풍이 잘되는 옷을 추천해요!`
          : temperature != null && temperature >= 20
            ? `${clearSky ? '맑고 ' : cloudySky ? '구름이 있는 ' : '흐린 '}활동하기 좋은 날씨예요\n가벼운 옷차림이 좋아요!`
            : `${clearSky ? '맑고 ' : cloudySky ? '구름이 있는 ' : '흐린 '}선선한 날씨예요\n얇은 겉옷을 챙겨보세요!`;

  return (
    <div className="box-border content-stretch flex flex-col gap-2 items-start justify-start px-5 py-0 relative shrink-0 w-full">
      {/* 위치 정보 */}
      <div className="content-stretch flex translate-y-3 gap-2 items-center justify-center relative rounded-full shrink-0">
        <div className="font-bold leading-none not-italic relative shrink-0 text-gray-600 text-2xl text-center text-nowrap tracking-[-0.4px]">
          <p className="leading-normal whitespace-pre">
            {
              weather?.location?.locationNames?.reduce((prev, current) => prev.concat(' ').concat(current))
                || '위치 정보가 없습니다.'
            }
          </p>
        </div>
        <div className="bg-white relative rounded-full shrink-0 size-6 cursor-pointer" onClick={fetchLocation}>
          <div className="box-border content-stretch flex gap-2 items-center justify-center overflow-clip p-[2px] relative size-6">
            <div className="overflow-clip relative shrink-0 size-[18px]">
              <div className="absolute inset-[11.806%]">
                <img alt="위치" className="block max-w-none size-full" src={imageLocationIcon} />
              </div>
            </div>
          </div>
          <div className="absolute border border-gray-200 border-solid inset-0 pointer-events-none rounded-full" />
        </div>
      </div>

      {/* 메인 날씨 정보 */}
      {weather ? (
        <div className="content-stretch flex flex-col gap-8 relative shrink-0 w-full xl:grid xl:grid-cols-[minmax(260px,1fr)_380px_minmax(300px,1fr)] xl:items-center xl:gap-8">
          {/* 온도 및 상태 */}
          <div className="box-border content-stretch flex translate-y-3 gap-6 items-center justify-start min-h-px pl-0 pr-5 py-0 relative shrink-0">
            <div className="font-extrabold leading-none not-italic relative shrink-0 text-gray-900 text-[60px] text-center text-nowrap tracking-[-1.25px]">
              <p className="leading-normal whitespace-pre">{temperature != null ? `${Math.round(temperature)}°` : '-'}</p>
            </div>
            <img alt={conditionText} className="size-[72px] shrink-0" src={weatherIcon} />
            <div className="content-stretch flex flex-col items-start justify-center gap-1 leading-none not-italic relative shrink-0">
              <div className="font-bold relative shrink-0 text-gray-900 text-4xl text-nowrap tracking-[-0.5px]">
                <p className="leading-normal whitespace-pre">{conditionText}</p>
              </div>
              <div className="font-bold relative shrink-0 text-gray-500 text-xl tracking-[-0.35px]">
                <p className="leading-normal">
                  {tempDiff != null ? <><span>어제보다 </span><span className="text-orange-500">{(tempDiff > 0 ? '+' : '') + Math.round(tempDiff)}°</span></> : '-'}
                </p>
              </div>
            </div>
          </div>

          {/* 상세 날씨 정보 */}
          <div className="box-border grid w-[380px] grid-cols-2 gap-x-8 gap-y-5 xl:-translate-x-24 xl:translate-y-5 relative shrink-0">

            {/* 기온 범위 */}
            <div className="content-stretch flex gap-3 items-center justify-start relative">
              <ThermometerSun className="size-5 shrink-0 text-orange-300" strokeWidth={2.5} />
              <div className="flex items-center gap-3 whitespace-nowrap">
                <span className="font-bold leading-none text-gray-600 text-[15px]">기온</span>
                <span className="font-bold text-gray-800 text-lg">{tempMin != null && tempMax != null ? `${Math.round(tempMin)}° / ${Math.round(tempMax)}°` : '-'}</span>
              </div>
            </div>

            {/* 습도 */}
            <div className="content-stretch flex gap-3 items-center justify-start relative shrink-0">
              <Droplets className="size-5 shrink-0 text-sky-300" strokeWidth={2.5} />
              <div className="flex items-center gap-3 whitespace-nowrap"><span className="font-bold text-gray-600 text-[15px]">습도</span><span className="font-bold text-gray-800 text-lg">{humidity != null ? Math.round(humidity) + '%' : '-'}</span><span className="text-sm font-semibold text-gray-500">{weather?.humidity?.comparedToDayBefore != null ? `어제보다 ${weather.humidity.comparedToDayBefore > 0 ? '+' : ''}${Math.round(weather.humidity.comparedToDayBefore)}%` : ''}</span></div>
            </div>

            {/* 강수 */}
            <div className="content-stretch flex gap-3 items-center justify-start relative shrink-0">
              <CloudRain className="size-5 shrink-0 text-blue-300" strokeWidth={2.5} />
              <div className="flex items-center gap-3 whitespace-nowrap"><span className="font-bold text-gray-600 text-[15px]">강수</span><span className="font-bold text-gray-800 text-lg">{Math.round(precipitation.probability)}%</span><span className="text-sm font-semibold text-gray-500">{precipitationText} · {Math.round(precipitation.amount)}mm</span></div>
            </div>

            {/* 바람 */}
            <div className="content-stretch flex gap-3 items-center justify-start relative shrink-0">
              <Wind className="size-5 shrink-0 text-teal-300" strokeWidth={2.5} />
              <div className="flex items-center gap-3 whitespace-nowrap"><span className="font-bold text-gray-600 text-[15px]">바람</span><span className="font-bold text-gray-800 text-lg">{windSpeed.speed != null ? Math.round(windSpeed.speed) + 'm/s' : '-'}</span><span className="text-sm font-semibold text-gray-500">{windSpeed.asWord === 'WEAK' ? '약한 바람' : windSpeed.asWord === 'MODERATE' ? '보통 바람' : '강한 바람'}</span></div>
            </div>
          </div>
          <div className="hidden h-[140px] w-full max-w-[500px] items-center justify-between justify-self-end overflow-hidden rounded-2xl bg-[#dff0ff] px-5 xl:flex">
            <p className="ml-6 max-w-[300px] text-lg font-bold leading-[2] tracking-wide text-gray-700">
              <span className="inline-flex items-center gap-1 whitespace-nowrap">{characterMessage.split('\n')[0]}<img src={weatherIcon} alt="현재 날씨" className="size-6 object-contain" /></span>
              <br />{characterMessage.split('\n')[1]}
            </p>
            <img src={character} alt="날씨 캐릭터" className="h-full w-[135px] object-contain object-bottom" />
          </div>
        </div>
      ) : (
        <div className="content-stretch flex items-center justify-between relative shrink-0 w-full">
          {/* Skeleton for temperature and status */}
          <div className="basis-0 box-border content-stretch flex gap-4 grow items-center justify-start min-h-px min-w-px pl-0 pr-5 py-0 relative shrink-0">
            <div className="h-[60px] w-[120px] bg-gray-200 rounded animate-pulse" />
            <div className="basis-0 content-stretch flex flex-col gap-[3px] grow items-start justify-start leading-none min-h-px min-w-px not-italic relative shrink-0">
              <div className="content-stretch flex items-center justify-between relative shrink-0 text-center w-[146px]">
                <div className="h-6 w-16 bg-gray-200 rounded animate-pulse" />
                <div className="h-5 w-20 bg-gray-200 rounded animate-pulse" />
              </div>
              <div className="h-4 w-24 bg-gray-200 rounded animate-pulse" />
            </div>
          </div>

          {/* Skeleton for weather details */}
          <div className="box-border content-stretch flex gap-5 items-center justify-start pl-5 pr-0 py-0 relative shrink-0">
            <div className="absolute border-gray-300 border-l-2 border-solid inset-0 pointer-events-none" />

            {/* Skeleton for humidity */}
            <div className="content-stretch flex gap-2.5 items-start justify-start relative shrink-0">
              <div className="h-4 w-8 bg-gray-200 rounded animate-pulse" />
              <div className="content-stretch flex flex-col gap-1 items-start justify-start leading-none not-italic relative shrink-0 w-[97px]">
                <div className="h-4 w-12 bg-gray-200 rounded animate-pulse" />
                <div className="h-3 w-16 bg-gray-200 rounded animate-pulse" />
              </div>
            </div>

            {/* Skeleton for precipitation */}
            <div className="content-stretch flex gap-2.5 items-start justify-start relative shrink-0">
              <div className="h-4 w-8 bg-gray-200 rounded animate-pulse" />
              <div className="content-stretch flex flex-col gap-1 items-start justify-start leading-none not-italic relative shrink-0 w-[89px]">
                <div className="h-4 w-10 bg-gray-200 rounded animate-pulse" />
                <div className="h-3 w-16 bg-gray-200 rounded animate-pulse" />
              </div>
            </div>

            {/* Skeleton for wind */}
            <div className="content-stretch flex gap-2.5 items-start justify-start relative shrink-0">
              <div className="h-4 w-8 bg-gray-200 rounded animate-pulse" />
              <div className="content-stretch flex flex-col gap-1 items-start justify-start leading-none not-italic relative shrink-0 w-[51px]">
                <div className="h-4 w-12 bg-gray-200 rounded animate-pulse" />
                <div className="h-3 w-14 bg-gray-200 rounded animate-pulse" />
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
