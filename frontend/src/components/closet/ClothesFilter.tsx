import { useState } from 'react';
import { useClothesStore } from '@/lib/stores/useClothesStore';
import type { ClothesType } from '@/lib/api/types';

interface ClothesCategory {
  label: string;
  value?: ClothesType;
  favorite?: boolean;
}

const CLOTHES_CATEGORIES: ClothesCategory[] = [
  { label: '전체', value: undefined },
  { label: '즐겨찾기', value: undefined, favorite: true },
  { label: '상의', value: 'TOP' },
  { label: '하의', value: 'BOTTOM' },
  { label: '원피스', value: 'DRESS' },
  { label: '아우터', value: 'OUTER' },
  { label: '신발', value: 'SHOES' },
  { label: '악세서리', value: 'ACCESSORY' },
  { label: '모자', value: 'HAT' },
  { label: '가방', value: 'BAG' },
  { label: '기타', value: 'ETC' },
];

interface ClothesFilterProps {
  onAddClick?: () => void; // 선택적으로 만들어서 다른 사용자 옷장에서는 숨김
}

export default function ClothesFilter({ onAddClick }: ClothesFilterProps) {
  const { updateParams } = useClothesStore();
  const [selectedType, setSelectedType] = useState<ClothesType | undefined>(undefined);
  const [selectedFavorite, setSelectedFavorite] = useState<boolean | undefined>(undefined);

  const handleFilterChange = (type?: ClothesType, favorite?: boolean) => {
    setSelectedType(type);
    setSelectedFavorite(favorite);
    updateParams({ typeEqual: type, favorite });
  };

  return (
    <div className="content-stretch flex items-center justify-between relative w-full">
      {/* 카테고리 필터 탭 */}
      <div className="content-stretch flex gap-[20px] items-center justify-start relative shrink-0">
        {CLOTHES_CATEGORIES.map((category) => {
          const isSelected = selectedType === category.value && selectedFavorite === category.favorite;
          return (
            <button
              key={category.label}
              onClick={() => handleFilterChange(category.value, category.favorite)}
              className={`box-border content-stretch flex gap-1.5 items-center justify-center px-[18px] py-4 relative shrink-0 ${
                isSelected
                  ? 'border-b-4 border-blue-500 border-solid'
                  : ''
              }`}
            >
              <div className={`font-bold leading-none not-italic relative shrink-0 text-[18px] text-nowrap tracking-[-0.45px] ${
                isSelected ? 'text-blue-500' : 'text-gray-700'
              }`}>
                <p className="leading-normal whitespace-pre">{category.label}</p>
              </div>
            </button>
          );
        })}
      </div>

      {/* 옷 추가하기 버튼 - 자신의 옷장일 때만 표시 */}
      {onAddClick && (
        <div className="relative shrink-0">
          <button
            onClick={onAddClick}
            className="bg-blue-500 hover:bg-blue-600 box-border content-stretch flex gap-1.5 h-[46px] items-center justify-center px-[18px] py-2.5 relative rounded-[12px] shrink-0 transition-colors"
          >
            <div className="font-bold leading-none not-italic relative shrink-0 text-white text-[18px] text-nowrap tracking-[-0.45px]">
              <p className="leading-normal whitespace-pre">옷 추가하기</p>
            </div>
          </button>
        </div>
      )}
    </div>
  );
}
