interface RecommendationReasonProps {
  reason?: string | null;
}

export default function RecommendationReason({reason}: RecommendationReasonProps) {
  if (!reason?.trim()) return null;

  return (
    <div className="w-full rounded-[12px] bg-blue-50 px-5 py-4" data-testid="recommendation-reason">
      <p className="mb-1 text-sm font-bold text-blue-600">AI 추천 이유</p>
      <p className="text-[15px] font-medium leading-6 text-[#575765]">{reason}</p>
    </div>
  );
}
