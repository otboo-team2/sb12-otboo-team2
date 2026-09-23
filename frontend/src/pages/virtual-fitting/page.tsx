import { useEffect, useState } from 'react';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useAuthStore } from '@/lib/stores/useAuthStore';
import { useImageUpload } from '@/hooks/useImageUpload';
import { useFittingStore } from '@/lib/stores/useFittingStore';
import { getClothes } from '@/lib/api/clothes';
import { submitFitting } from '@/lib/api/fittings';
import ClothesPickerGrid from '@/components/fitting/ClothesPickerGrid';
import { toast } from 'sonner';
import type { ClothesDto, ClothesType } from '@/lib/api/types';

const ADDITIONAL_CATEGORIES: { label: string; value: ClothesType | 'ALL' }[] = [
    { label: '전체', value: 'ALL' },
    { label: '아우터', value: 'OUTER' },
    { label: '속옷', value: 'UNDERWEAR' },
    { label: '액세서리', value: 'ACCESSORY' },
    { label: '신발', value: 'SHOES' },
    { label: '양말', value: 'SOCKS' },
    { label: '모자', value: 'HAT' },
    { label: '가방', value: 'BAG' },
    { label: '스카프', value: 'SCARF' },
    { label: '기타', value: 'ETC' },
];

export default function VirtualFittingPage() {
    const { data: auth } = useAuthStore();
    const { selectedImage, imagePreview, handleImageChange, clearImage } = useImageUpload();
    const { job, polling, start, resume, reset } = useFittingStore();

    const [topOptions, setTopOptions] = useState<ClothesDto[]>([]);
    const [bottomOptions, setBottomOptions] = useState<ClothesDto[]>([]);
    const [additionalOptions, setAdditionalOptions] = useState<ClothesDto[]>([]);
    const [additionalCategory, setAdditionalCategory] = useState<ClothesType | 'ALL'>('ALL');

    const [topClothesId, setTopClothesId] = useState('');
    const [bottomClothesId, setBottomClothesId] = useState('');
    const [additionalClothesId, setAdditionalClothesId] = useState('');
    const [submitting, setSubmitting] = useState(false);

    // 상의 / 하의 목록 — 한 번만 불러오면 됨
    useEffect(() => {
        const ownerId = auth?.userDto.id;
        if (!ownerId) return;

        Promise.all([
            getClothes({ ownerId, typeEqual: 'TOP', limit: 100 }),
            getClothes({ ownerId, typeEqual: 'BOTTOM', limit: 100 }),
        ]).then(([tops, bottoms]) => {
            setTopOptions(tops.data);
            setBottomOptions(bottoms.data);
        });
    }, [auth?.userDto.id]);

    // 추가 의상 목록 — 카테고리 바뀔 때마다 다시 불러옴
    useEffect(() => {
        const ownerId = auth?.userDto.id;
        if (!ownerId) return;

        getClothes({
            ownerId,
            typeEqual: additionalCategory === 'ALL' ? undefined : additionalCategory,
            limit: 100,
        }).then((res) => {
            setAdditionalOptions(
                res.data.filter((c) => c.type !== 'TOP' && c.type !== 'BOTTOM' && c.type !== 'DRESS')
            );
        });
    }, [auth?.userDto.id, additionalCategory]);

    // 새로고침 등으로 페이지가 다시 열렸을 때, 진행 중이던 job이 있으면 이어서 폴링
    useEffect(() => {
        resume();
    }, []);

    const handleSubmit = async () => {
        if (!topClothesId || !bottomClothesId) return;

        setSubmitting(true);
        try {
            const created = await submitFitting(
                {
                    topClothesId,
                    bottomClothesId,
                    additionalClothesId: additionalClothesId || null,
                },
                selectedImage ?? undefined
            );
            start(created);
        } catch (error) {
            console.error('가상피팅 요청 실패:', error);
            toast.error('가상피팅 요청에 실패했습니다.');
        } finally {
            setSubmitting(false);
        }
    };

    const handleReset = () => {
        reset();
        clearImage();
        setTopClothesId('');
        setBottomClothesId('');
        setAdditionalClothesId('');
        setAdditionalCategory('ALL');
    };

    const isLoading = job !== null && (job.status === 'PENDING' || job.status === 'PROCESSING');
    const isSucceeded = job?.status === 'SUCCEEDED';
    const isFailed = job?.status === 'FAILED';

    return (
        <div className="flex flex-col h-full px-10 py-8 gap-6 overflow-y-auto">
            <div>
                <h1 className="font-bold text-gray-900 text-[24px]">가상피팅</h1>
                <p className="text-gray-500 text-[14px] mt-1">
                    내 옷장에서 상의·하의를 골라 가상으로 입혀볼 수 있어요.
                </p>
            </div>

            {job === null ? (
                <div className="flex flex-col gap-6 flex-1 min-h-0">
                    <div className="grid grid-cols-3 gap-5 flex-1 min-h-[360px]">
                        <ClothesPickerGrid
                            title="상의"
                            clothes={topOptions}
                            selectedId={topClothesId}
                            onSelect={setTopClothesId}
                        />
                        <ClothesPickerGrid
                            title="하의"
                            clothes={bottomOptions}
                            selectedId={bottomClothesId}
                            onSelect={setBottomClothesId}
                        />
                        <ClothesPickerGrid
                            title="추가 의상 (선택)"
                            clothes={additionalOptions}
                            selectedId={additionalClothesId}
                            onSelect={setAdditionalClothesId}
                            optional
                            headerExtra={
                                <Select
                                    value={additionalCategory}
                                    onValueChange={(v) => setAdditionalCategory(v as ClothesType | 'ALL')}
                                >
                                    <SelectTrigger className="bg-white h-[36px] w-[110px] rounded-[100px] border border-gray-200 px-3 text-[13px]">
                                        <SelectValue />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {ADDITIONAL_CATEGORIES.map((opt) => (
                                            <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            }
                        />
                    </div>

                    <div className="flex items-center gap-4 shrink-0">
                        <label className="cursor-pointer shrink-0 relative">
                            <div className="size-[64px] bg-gray-100 rounded-[12px] overflow-hidden flex items-center justify-center border border-gray-200">
                                {imagePreview ? (
                                    <img src={imagePreview} alt="모델 사진 미리보기" className="w-full h-full object-cover" />
                                ) : (
                                    <span className="text-gray-400 text-[11px] text-center px-1">모델 사진</span>
                                )}
                            </div>
                            {imagePreview && (
                                <button
                                    type="button"
                                    onClick={(e) => {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        clearImage();
                                    }}
                                    className="absolute -top-2 -right-2 size-[20px] rounded-full bg-gray-700 hover:bg-gray-900 text-white flex items-center justify-center text-[12px] leading-none transition-colors"
                                    aria-label="모델 사진 취소"
                                >
                                    ✕
                                </button>
                            )}
                            <input type="file" accept="image/*" onChange={handleImageChange} className="hidden" />
                        </label>
                        <p className="text-gray-500 text-[13px] flex-1">
                            모델 사진은 선택사항이에요. 비워두면 기본 모델을 사용해요.
                        </p>
                        <button
                            onClick={handleSubmit}
                            disabled={submitting || !topClothesId || !bottomClothesId}
                            className="bg-blue-500 hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed h-[46px] px-6 rounded-[12px] shrink-0 transition-colors"
                        >
              <span className="font-bold text-white text-[16px]">
                {submitting ? '요청 중...' : '가상피팅 생성'}
              </span>
                        </button>
                    </div>
                </div>
            ) : (
                <div className="flex-1 min-h-0 bg-gray-50 rounded-[16px] border border-gray-200 flex items-center justify-center p-8">
                    {isLoading && (
                        <div className="flex flex-col items-center gap-3">
                            <div className="size-10 rounded-full border-4 border-gray-200 border-t-blue-500 animate-spin" />
                            <p className="text-gray-600 text-[15px]">
                                가상피팅 생성 중이에요{polling ? '...' : ''}
                            </p>
                        </div>
                    )}

                    {isSucceeded && job?.resultImageUrl && (
                        <div className="flex flex-col items-center gap-4 h-full">
                            <img
                                src={job.resultImageUrl}
                                alt="가상피팅 결과"
                                className="max-w-full max-h-full object-contain rounded-[12px]"
                            />
                            <button
                                onClick={handleReset}
                                className="bg-gray-100 hover:bg-gray-200 h-[46px] px-6 rounded-[12px] transition-colors"
                            >
                                <span className="font-bold text-gray-700 text-[16px]">새로 만들기</span>
                            </button>
                        </div>
                    )}

                    {isFailed && (
                        <div className="flex flex-col items-center gap-4">
                            <p className="text-gray-700 text-[15px] text-center max-w-[320px]">
                                {job?.failureReason ?? '가상피팅 생성에 실패했습니다.'}
                            </p>
                            {job?.retryable && (
                                <button
                                    onClick={reset}
                                    className="bg-blue-500 hover:bg-blue-600 h-[46px] px-6 rounded-[12px] transition-colors"
                                >
                                    <span className="font-bold text-white text-[16px]">다시 시도</span>
                                </button>
                            )}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
