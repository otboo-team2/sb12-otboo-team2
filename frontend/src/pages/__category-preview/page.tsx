import ClothesFilter from '@/components/closet/ClothesFilter';

export default function CategoryPreviewPage() {
  return (
    <div className="p-10 flex flex-col gap-10">
      <div className="w-[900px] border border-dashed border-gray-300 p-4">
        <p className="text-sm text-gray-400 mb-2">900px 컨테이너 (옷장 페이지 폭 예시)</p>
        <ClothesFilter onAddClick={() => {}} />
      </div>
      <div className="w-[600px] border border-dashed border-gray-300 p-4">
        <p className="text-sm text-gray-400 mb-2">600px 컨테이너 (좁은 화면 예시)</p>
        <ClothesFilter onAddClick={() => {}} />
      </div>
    </div>
  );
}
