import RecommendationItem from './RecommendationItem';
import {useRecommendationStore} from "@/lib/stores/useRecommendationStore.ts";
import {LoaderCircle} from 'lucide-react';
import {useRef} from 'react';

interface RecommendationGridProps {
  showLoadingMessage?: boolean;
}

export default function RecommendationGrid({showLoadingMessage = false}: RecommendationGridProps) {
  const {data: recommendations, loading} = useRecommendationStore();
  const lastRecommendations = useRef(recommendations);
  if (recommendations?.clothes?.length) lastRecommendations.current = recommendations;
  const displayedRecommendations = recommendations ?? lastRecommendations.current;

  if (loading && (showLoadingMessage || !displayedRecommendations?.clothes?.length)) {
    return (
      <div className="flex min-h-[220px] w-full items-center justify-center rounded-[20px] border border-[#e7e7e9] bg-white">
        <div className="text-center">
          <p className="flex items-center justify-center gap-2 text-base font-bold text-slate-700">
            AI가 어울리는 코디를 조합하고 있어요
            <LoaderCircle className="size-5 animate-spin text-slate-500" aria-label="로딩 중" />
          </p>
          <p className="mt-2 text-sm font-medium text-slate-400">잠시만 기다려주세요!</p>
        </div>
      </div>
    );
  }

  if (!displayedRecommendations || !displayedRecommendations.clothes || displayedRecommendations.clothes.length === 0) {
    return (
      <div className="flex items-center justify-center w-full py-16">
        <p className="text-gray-500 text-lg">추천할 옷을 찾을 수 없습니다.</p>
      </div>
    );
  }

  // 자연스러운 그리드로 모든 옷 표시
  const clothes = displayedRecommendations.clothes;

  return (
    <div className="w-full flex-none overflow-visible">
      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4 p-1">
        {clothes.map((item, index) => (
          <RecommendationItem
            key={`${item.clothesId}-${index}`}
            item={item}
          />
        ))}
      </div>
    </div>
  );
}
