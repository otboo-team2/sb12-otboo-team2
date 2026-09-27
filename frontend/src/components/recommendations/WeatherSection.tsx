import {useEffect} from 'react';
import CurrentWeather from './CurrentWeather';
import WeatherForecast from './WeatherForecast';
import {useMyProfileStore} from '@/lib/stores/useMyProfileStore';
import useGeoLocation from "@/hooks/useGeoLocation.ts";
import {useWeatherStore} from "@/lib/stores/useWeatherStore.ts";

export default function WeatherSection() {
  const { data: profile } = useMyProfileStore();
  const { location, refetchLocation, setLocation } = useGeoLocation();
  const { updateParams } = useWeatherStore();


  // 프로필의 위치 정보로 초기화
  useEffect(() => {
    if (profile?.location) {
      setLocation({longitude: profile.location.longitude, latitude: profile.location.latitude});
    }
  }, [profile?.location, setLocation]);

  useEffect(() => {
    if (location) {
      updateParams({...location});
    }
  }, [location, updateParams]);

  return (
    <div className="box-border content-stretch flex flex-col gap-10 items-start justify-start px-[100px] py-0 relative w-full z-10 mb-5">
      {/* CurrentWeather에 위치 정보를 props로 전달 */}
      <CurrentWeather
          fetchLocation={refetchLocation}
      />
      {/* WeatherForecast에 위치 정보를 props로 전달 */}
      <WeatherForecast/>
    </div>
  );
}
