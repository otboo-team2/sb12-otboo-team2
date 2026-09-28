import {useEffect, useState} from 'react';
import {Check} from 'lucide-react';
import {Dialog, DialogContent, DialogOverlay} from '@/components/ui/dialog';
import {useAuthStore} from '@/lib/stores/useAuthStore';
import {useMyProfileStore} from '@/lib/stores/useMyProfileStore';
import {useWeatherStore} from '@/lib/stores/useWeatherStore';
import {useFeedStore} from '@/lib/stores/useFeedStore';
import {createFeed} from '@/lib/api/feeds';
import {getClothes} from '@/lib/api/clothes';
import {getWeather} from '@/lib/api/weather';
import {toast} from 'sonner';
import type {ClothesDto, FeedDto, SkyStatus, WeatherDto} from "@/lib/api";

// Figma assets
import closeIcon from '@/assets/icons/ic_X.svg';

interface AddFeedModalProps {
  open: boolean;
  onClose: () => void;
  onCreated: (feed: FeedDto) => void;
}

// 백엔드 목록 조회 상한(CursorRequest.MAX_LIMIT)
const CLOTHES_LIMIT = 100;

const SKY_STATUS_TEXT: Record<SkyStatus, string> = {
  CLEAR: '맑음',
  MOSTLY_CLOUDY: '구름많음',
  CLOUDY: '흐림',
};

// 지금 이후 가장 가까운 예보. 지난 예보뿐이면 그중 가장 가까운 것
function findNearestWeather(weathers: WeatherDto[]): WeatherDto | undefined {
  if (weathers.length === 0) return undefined;
  const now = Date.now();
  const future = weathers.filter(weather => new Date(weather.forecastAt).getTime() >= now);
  const candidates = future.length > 0 ? future : weathers;
  return candidates.reduce((nearest, weather) =>
    Math.abs(new Date(weather.forecastAt).getTime() - now)
      < Math.abs(new Date(nearest.forecastAt).getTime() - now)
      ? weather
      : nearest,
  );
}

export default function AddFeedModal({ open, onClose, onCreated }: AddFeedModalProps) {
  const { data: auth } = useAuthStore();
  const { data: profile } = useMyProfileStore();
  const { selectedWeather } = useWeatherStore();

  const [content, setContent] = useState('');
  const [loading, setLoading] = useState(false);
  const [clothes, setClothes] = useState<ClothesDto[]>([]);
  const [clothesLoading, setClothesLoading] = useState(false);
  const [selectedClothesIds, setSelectedClothesIds] = useState<string[]>([]);
  const [weather, setWeather] = useState<WeatherDto>();
  const [weatherLoading, setWeatherLoading] = useState(false);

  const ownerId = auth?.userDto.id;
  const latitude = profile?.location?.latitude;
  const longitude = profile?.location?.longitude;

  // 내 옷장 불러오기
  useEffect(() => {
    if (!open || !ownerId) return;
    setClothesLoading(true);
    getClothes({ ownerId, limit: CLOTHES_LIMIT })
      .then(response => setClothes(response.data))
      .catch(() => toast.error('옷장을 불러오지 못했습니다.'))
      .finally(() => setClothesLoading(false));
  }, [open, ownerId]);

  // 날씨: 추천 페이지에서 고른 날씨가 있으면 그대로 쓰고, 없으면 프로필 위치로 가장 가까운 예보를 쓴다
  useEffect(() => {
    if (!open) return;
    if (selectedWeather) {
      setWeather(selectedWeather);
      return;
    }
    if (latitude === undefined || longitude === undefined) {
      setWeather(undefined);
      return;
    }
    setWeatherLoading(true);
    getWeather({ latitude, longitude })
      .then(weathers => setWeather(findNearestWeather(weathers)))
      .catch(() => setWeather(undefined))
      .finally(() => setWeatherLoading(false));
  }, [open, selectedWeather, latitude, longitude]);

  const toggleClothes = (clothesId: string) => {
    setSelectedClothesIds(current => current.includes(clothesId)
      ? current.filter(id => id !== clothesId)
      : [...current, clothesId]);
  };

  const reset = () => {
    setContent('');
    setSelectedClothesIds([]);
  };

  const handleSubmit = async () => {
    if (!ownerId || !weather) {
      toast.error('날씨 정보가 없어 등록할 수 없습니다.');
      return;
    }

    if (selectedClothesIds.length === 0) {
      toast.error('옷을 한 벌 이상 선택해주세요.');
      return;
    }

    if (!content.trim()) {
      toast.error('OOTD에 대한 설명을 입력해주세요.');
      return;
    }

    setLoading(true);

    try {
      const created = await createFeed({
        authorId: ownerId,
        weatherId: weather.id,
        clothesIds: selectedClothesIds,
        content: content.trim()
      });

      // 성공 toast에 버튼 추가
      toast.success('피드 등록이 완료되었습니다.', {
        action: {
          label: '등록된 피드 확인',
          onClick: () => {
            onCreated(created);
          }
        }
      });

      // 새 피드가 목록에 보이도록 다시 불러온다
      useFeedStore.getState().fetch();

      // 모달 닫고 초기화
      reset();
      onClose();
    } catch (error) {
      console.error('Feed creation failed:', error);
      toast.error('피드 등록을 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = () => {
    onClose();
  };

  const renderWeather = () => {
    if (weatherLoading) {
      return <p className="text-[14px] font-semibold text-[#a9a9b1]">날씨 불러오는 중...</p>;
    }
    if (!weather) {
      return (
        <p className="text-[14px] font-semibold text-red-500">
          날씨 정보가 없습니다. 프로필에서 위치를 설정해주세요.
        </p>
      );
    }
    return (
      <p className="text-[14px] font-semibold text-[#575765]">
        {SKY_STATUS_TEXT[weather.skyStatus]} · {Math.round(weather.temperature.current)}°
      </p>
    );
  };

  const renderClothes = () => {
    if (clothesLoading) {
      return <p className="py-10 text-center text-[14px] font-semibold text-[#a9a9b1]">옷장 불러오는 중...</p>;
    }
    if (clothes.length === 0) {
      return <p className="py-10 text-center text-[14px] font-semibold text-[#a9a9b1]">옷장에 등록된 옷이 없습니다.</p>;
    }
    return (
      <div className="grid grid-cols-5 gap-3 max-h-[300px] overflow-y-auto pr-1">
        {clothes.map(item => {
          const selected = selectedClothesIds.includes(item.id);
          return (
            <button
              key={item.id}
              type="button"
              aria-pressed={selected}
              onClick={() => toggleClothes(item.id)}
              className="flex flex-col gap-1.5 items-start text-left"
            >
              <div className={`relative aspect-square w-full overflow-hidden rounded-[12px] bg-gray-200 border-2 transition-colors ${selected ? 'border-[#1e89f4]' : 'border-transparent'}`}>
                {item.imageUrl ? (
                  <img src={item.imageUrl} alt={item.name} className="size-full object-cover" />
                ) : (
                  <div className="flex size-full items-center justify-center text-xs text-gray-500">이미지 없음</div>
                )}
                {selected && (
                  <div className="absolute right-1.5 top-1.5 flex size-5 items-center justify-center rounded-full bg-[#1e89f4] text-white">
                    <Check className="size-3.5" aria-hidden="true" />
                  </div>
                )}
              </div>
              <p className="w-full truncate text-[13px] font-semibold text-[#212126]">{item.name}</p>
            </button>
          );
        })}
      </div>
    );
  };

  return (
    <Dialog open={open} onOpenChange={handleCancel}>
      <DialogOverlay className="bg-[rgba(19,19,22,0.5)]" />
      <DialogContent className="bg-white box-border content-stretch flex flex-col gap-6 items-center justify-center overflow-clip p-[30px] rounded-[30px] w-[733px] max-w-none" showCloseButton={false}>
        {/* 헤더 */}
        <div className="content-stretch flex items-center justify-between relative shrink-0 w-full">
          <div className="content-stretch flex gap-2 items-center justify-start shrink-0" />
          <div className="font-bold leading-[0] not-italic relative shrink-0 text-[#212126] text-[22px] text-nowrap tracking-[-0.55px]">
            <p className="leading-[normal] whitespace-pre">피드 등록하기</p>
          </div>
          <button
            onClick={handleCancel}
            className="overflow-clip relative shrink-0 size-[30px] flex items-center justify-center hover:bg-gray-100 rounded transition-colors"
          >
            <img src={closeIcon} alt="닫기" className="size-6" />
          </button>
        </div>

        {/* 날씨 */}
        <div className="flex w-full items-center gap-2">
          <p className="text-[14px] font-bold text-[#808089]">날씨</p>
          {renderWeather()}
        </div>

        {/* 옷 선택 */}
        <div className="flex w-full flex-col gap-3">
          <p className="text-[14px] font-bold text-[#808089]">
            옷 선택 <span className="text-[#1e89f4]">{selectedClothesIds.length}</span>
          </p>
          {renderClothes()}
        </div>

        {/* 텍스트 에리어 */}
        <div className="bg-white box-border content-stretch flex gap-2 h-[120px] items-start justify-start px-5 py-3.5 relative rounded-[12px] shrink-0 w-full border border-[#e7e7e9] shadow-[0px_2px_4px_0px_rgba(55,55,64,0.03)]">
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            placeholder="OOTD에 대해 설명해주세요"
            className="w-full h-full resize-none border-none outline-none bg-transparent font-semibold text-[16px] tracking-[-0.4px] text-[#212126] placeholder-[#a9a9b1]"
          />
        </div>

        {/* 버튼들 */}
        <div className="content-stretch flex gap-3 items-center justify-end relative shrink-0 w-full">
          <button
            onClick={handleCancel}
            className="bg-[#f7f7f8] box-border content-stretch flex gap-1.5 h-[46px] items-center justify-center px-[18px] py-2.5 relative rounded-[12px] shrink-0 hover:bg-gray-200 transition-colors"
          >
            <div className="font-bold leading-[0] not-italic relative shrink-0 text-[#575765] text-[18px] text-nowrap tracking-[-0.45px]">
              <p className="leading-[normal] whitespace-pre">취소</p>
            </div>
          </button>
          <button
            onClick={handleSubmit}
            disabled={loading || !weather || selectedClothesIds.length === 0 || !content.trim()}
            className="bg-[#1e89f4] box-border content-stretch flex gap-1.5 h-[46px] items-center justify-center px-[18px] py-2.5 relative rounded-[12px] shrink-0 hover:bg-[#1e89f4]/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <div className="font-bold leading-[0] not-italic relative shrink-0 text-[18px] text-nowrap text-white tracking-[-0.45px]">
              <p className="leading-[normal] whitespace-pre">{loading ? '등록 중...' : '등록'}</p>
            </div>
          </button>
        </div>
      </DialogContent>
    </Dialog>

  );
}
