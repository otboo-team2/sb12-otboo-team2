import {useEffect, useState} from 'react';
import type {FormEvent} from 'react';
import {MessageCirclePlus, Send} from 'lucide-react';
import RecommendationHeader from './RecommendationHeader';
import RecommendationGrid from './RecommendationGrid';
import EmptyRecommendation from './EmptyRecommendation';
import RecommendationReason from './RecommendationReason';
import {useRecommendationStore} from "@/lib/stores/useRecommendationStore.ts";
import {useWeatherStore} from "@/lib/stores/useWeatherStore.ts";

export default function RecommendationSection() {
  const { selectedWeather } = useWeatherStore();
  const { data: recommendations, updateParams, fetchAiRecommendation, loading, error } = useRecommendationStore();
  const [prompt, setPrompt] = useState('');
  const examples = [
    '오늘 데이트룩 추천해줘',
    '비 오는 날 편한 옷 추천해줘',
    '면접인데 단정하게 입고 싶어',
    '오늘은 캐주얼한 스타일로 추천해줘',
  ];

  useEffect(() => {
    if (selectedWeather?.id) {
      updateParams({ weatherId: selectedWeather.id });
    }
  }, [selectedWeather?.id, updateParams]);

  // selectedWeather가 없으면 추천 섹션을 렌더링하지 않음
  if (!selectedWeather) {
    return null;
  }

  const hasClothes = recommendations && recommendations.clothes.length > 0;

  const handlePromptSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const value = prompt.trim();
    if (!value || !selectedWeather?.id || loading) return;
    await fetchAiRecommendation(value);
  };

  return (
    <div className="relative w-full px-[100px] h-full">
      <div className="bg-white rounded-[20px] box-border content-stretch flex flex-col gap-6 px-[40px] items-start justify-start py-8 relative w-full h-full shadow-[0px_-2px_10px_0px_rgba(0,0,0,0.05)]">
        <RecommendationHeader/>

        <div className="w-full flex flex-col gap-3">
          <form onSubmit={handlePromptSubmit} className="relative w-full">
            <input
              type="text"
              value={prompt}
              maxLength={100}
              onChange={(event) => setPrompt(event.target.value)}
              placeholder="예) 오늘 데이트 가는데 캐주얼하게 입을 옷 추천해줘"
              className="h-[58px] w-full rounded-[12px] border-2 border-blue-400 bg-white pl-14 pr-20 text-[16px] font-semibold tracking-[-0.4px] text-[#212126] outline-none placeholder:text-[#a9a9b1] focus:border-blue-500"
              aria-label="자연어 추천 요청"
            />
            <MessageCirclePlus className="pointer-events-none absolute left-5 top-1/2 size-5 -translate-y-1/2 text-blue-400" aria-hidden="true" />
            <span className="pointer-events-none absolute bottom-3 right-[68px] text-xs font-semibold text-[#b5b5bd]">
              {prompt.length} / 100
            </span>
            <button
              type="submit"
              aria-label="추천 요청 보내기"
              className="absolute right-2 top-2 flex size-[42px] items-center justify-center rounded-[10px] bg-blue-500 text-white transition-colors hover:bg-blue-600 disabled:opacity-50"
              disabled={!prompt.trim() || loading}
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
                onClick={() => setPrompt(example)}
                className="rounded-full border border-[#e1e1e5] bg-white px-3 py-1.5 text-sm font-semibold text-[#696975] transition-colors hover:border-blue-300 hover:bg-blue-50 hover:text-blue-600"
              >
                {example}
              </button>
            ))}
          </div>
        </div>

        {hasClothes && !loading && <RecommendationReason reason={recommendations.reason}/>}
        {hasClothes ? <RecommendationGrid/> : <EmptyRecommendation/>}
      </div>
    </div>
  );

}
