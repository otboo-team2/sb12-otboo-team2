import loginCharacter from '@/assets/weather/login-character.png';

export default function WeatherIconsHeader() {
  return (
    <div className="relative z-[2] mb-[-40px] h-[150px] w-full">
      <img
        src={loginCharacter}
        alt="로그인 캐릭터"
        className="absolute bottom-[-4px] left-1/2 h-[250px] w-[400px] -translate-x-1/2 object-contain"
      />
    </div>
  );
}
