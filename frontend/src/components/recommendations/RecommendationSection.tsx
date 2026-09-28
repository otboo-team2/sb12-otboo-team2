import {useEffect, useState} from 'react';
import type {FormEvent} from 'react';
import {Send, Sparkles} from 'lucide-react';
import RecommendationHeader from './RecommendationHeader';
import RecommendationGrid from './RecommendationGrid';
import EmptyRecommendation from './EmptyRecommendation';
import RecommendationReason from './RecommendationReason';
import OutfitReferenceModal from './OutfitReferenceModal';
import {useRecommendationStore} from "@/lib/stores/useRecommendationStore.ts";
import {useWeatherStore} from "@/lib/stores/useWeatherStore.ts";

export default function RecommendationSection() {
  const [isStyleModalOpen, setIsStyleModalOpen] = useState(false);
  const { selectedWeather } = useWeatherStore();
  const {
    data: recommendations, params, updateParams, fetchAiRecommendation, loading, error,
    inputPrompt, setInputPrompt,
  } = useRecommendationStore();
  const examples = [
    '오늘 데이트룩 추천해줘',
    '비 오는 날 편한 옷 추천해줘',
    '면접인데 단정하게 입고 싶어',
    '오늘은 캐주얼한 스타일로 추천해줘',
  ];

  useEffect(() => {
    if (selectedWeather?.id && params.weatherId !== selectedWeather.id) {
      updateParams({ weatherId: selectedWeather.id });
    }
  }, [selectedWeather?.id, params.weatherId, updateParams]);

  // selectedWeather가 없으면 추천 섹션을 렌더링하지 않음
  if (!selectedWeather) {
    return null;
  }

  const hasClothes = recommendations && recommendations.clothes.length > 0;

  const handlePromptSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const value = inputPrompt.trim();
    if (!value || !selectedWeather?.id || loading) return;
    await fetchAiRecommendation(value);
  };

  return (
    <div className="relative w-full px-[100px] pb-12">
      <div className="bg-white rounded-[20px] box-border content-stretch flex flex-col gap-6 px-[40px] items-start justify-start py-8 relative w-full shadow-[0px_-2px_10px_0px_rgba(0,0,0,0.05)]">
        <RecommendationHeader/>

        <div className="w-full pl-8 flex flex-col gap-3">
          <form onSubmit={handlePromptSubmit} className="relative w-full">
            <input
              type="text"
              value={inputPrompt}
              maxLength={100}
              onChange={(event) => setInputPrompt(event.target.value)}
              placeholder="예) 오늘은 편안하고 깔끔하게 입고 싶어"
              className="h-[58px] w-full rounded-[12px] border-2 border-[#cbd5e1] bg-white pl-14 pr-20 text-[16px] font-semibold tracking-[-0.4px] text-[#212126] outline-none placeholder:text-[#a9a9b1] focus:border-[#94a3b8]"
              aria-label="자연어 추천 요청"
            />
            <Sparkles className="pointer-events-none absolute left-5 top-1/2 size-5 -translate-y-1/2 text-[#64748b]" aria-hidden="true" />
            <span className="pointer-events-none absolute bottom-3 right-[68px] text-xs font-semibold text-[#b5b5bd]">
              {inputPrompt.length} / 100
            </span>
            <button
              type="submit"
              aria-label="추천 요청 보내기"
              className="absolute right-2 top-2 flex size-[42px] items-center justify-center rounded-[10px] bg-[#64748b] text-white transition-colors hover:bg-[#475569] disabled:opacity-50"
              disabled={!inputPrompt.trim() || loading}
            >
              <Send className="size-5" />
            </button>
          </form>

          {error && <p className="text-sm font-semibold text-red-500" role="alert">{error}</p>}

          <div className="flex flex-wrap items-center gap-2">
            <span className="shrink-0 text-xs font-semibold text-[#a9a9b1]">추천 예시</span>
            {examples.map((example) => (
              <button
                key={example}
                type="button"
                onClick={() => setInputPrompt(example)}
                className="rounded-full border border-[#e1e1e5] bg-white px-3 py-1.5 text-sm font-semibold text-[#696975] transition-colors hover:border-blue-200 hover:bg-blue-50 hover:text-blue-500"
              >
                {example}
              </button>
            ))}
          </div>
        </div>

        <div className="w-full pl-8">
          {hasClothes || loading ? <RecommendationGrid showLoadingMessage={loading && inputPrompt.trim().length > 0}/> : <EmptyRecommendation/>}
          {hasClothes && !loading && <div className="mt-4"><RecommendationReason reason={recommendations.reason}/></div>} 
        </div>
      </div>
      <div className="mt-6">
        <div className="flex w-full items-center justify-between rounded-[20px] border border-gray-100 bg-white px-8 py-7 shadow-[0px_2px_8px_rgba(55,55,64,0.04)]">
          <div className="flex items-start gap-5">
            <div className="flex size-12 items-center justify-center rounded-full bg-blue-500 text-white"><Sparkles className="size-7" /></div>
            <div>
              <div className="flex items-center gap-2"><p className="text-sm font-bold text-gray-800">스타일 탐색</p></div>
              <p className="mt-2 text-2xl font-extrabold text-gray-900">다른 스타일도 둘러볼까요?</p>
              <p className="mt-2 text-base font-semibold leading-relaxed text-gray-500">내 옷장과 관계없이 다양한 스타일을 살펴보고<br />새로운 코디 아이디어를 받아보세요.</p>
            </div>
          </div>
          <div className="flex flex-col items-end gap-4">
            <button type="button" onClick={() => setIsStyleModalOpen(true)} className="rounded-full bg-[#1e293b] px-8 py-4 text-base font-bold text-white hover:bg-[#0f172a]">
              AI 코디 추천 보기 <span aria-hidden="true">→</span>
            </button>
            <p className="text-sm font-semibold text-gray-500">✦ 지금은 다른 사람들의 스타일도 참고해보세요!</p>
          </div>
        </div>
        <OutfitReferenceModal open={isStyleModalOpen} onClose={() => setIsStyleModalOpen(false)} weatherId={selectedWeather.id} />
      </div>
    </div>
  );

}
