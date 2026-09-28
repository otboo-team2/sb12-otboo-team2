import { useEffect, useState, type ChangeEvent, type ReactNode } from 'react';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useAuthStore } from '@/lib/stores/useAuthStore';
import { useImageUpload } from '@/hooks/useImageUpload';
import { useFittingStore } from '@/lib/stores/useFittingStore';
import { getClothes } from '@/lib/api/clothes';
import { submitFitting } from '@/lib/api/fittings';
import ClothesPickerGrid from '@/components/fitting/ClothesPickerGrid';
import FittingStepper, { type FittingStep } from '@/components/fitting/FittingStepper';
import ModelSelectStep, {
    EXAMPLE_MODEL_IMAGES,
    type ModelGender,
    type ModelSource,
} from '@/components/fitting/ModelSelectStep';
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

async function assetToFile(url: string, name: string): Promise<File> {
    const res = await fetch(url);
    const blob = await res.blob();
    return new File([blob], name, { type: blob.type || 'image/png' });
}

export default function VirtualFittingPage() {
    const { data: auth } = useAuthStore();
    const { selectedImage, imagePreview, handleImageChange, clearImage } = useImageUpload();
    const { job, polling, start, resume, reset } = useFittingStore();

    const [step, setStep] = useState<FittingStep>(1);
    const [modelSource, setModelSource] = useState<ModelSource>('EXAMPLE');
    const [modelGender, setModelGender] = useState<ModelGender>('MALE');

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

    // job 이 생기거나 복구되면 결과 단계로
    useEffect(() => {
        if (job) setStep(3);
    }, [job]);

    const handleUpload = (e: ChangeEvent<HTMLInputElement>) => {
        const hasFile = !!e.target.files?.length;
        handleImageChange(e);
        if (hasFile) setModelSource('UPLOAD');
        // 같은 파일을 지웠다가 다시 고를 때도 onChange 가 뜨도록 비워둔다
        e.target.value = '';
    };

    const handleClearUpload = () => {
        clearImage();
        setModelSource('EXAMPLE');
    };

    const handleSelectExample = (gender: ModelGender) => {
        setModelGender(gender);
        setModelSource('EXAMPLE');
    };

    const canLeaveModelStep = modelSource === 'EXAMPLE' || !!selectedImage;

    const handleSubmit = async () => {
        if (!topClothesId || !bottomClothesId) return;

        setSubmitting(true);
        try {
            const modelImage = modelSource === 'UPLOAD'
                ? selectedImage ?? undefined
                : await assetToFile(EXAMPLE_MODEL_IMAGES[modelGender], `model-${modelGender.toLowerCase()}.png`);

            const created = await submitFitting(
                {
                    topClothesId,
                    bottomClothesId,
                    additionalClothesId: additionalClothesId || null,
                },
                modelImage
            );
            start(created);
        } catch (error) {
            console.error('가상피팅 요청 실패:', error);
            toast.error('가상피팅 요청에 실패했습니다.');
        } finally {
            setSubmitting(false);
        }
    };

    // 처음부터 다시
    const handleReset = () => {
        reset();
        clearImage();
        setModelSource('EXAMPLE');
        setTopClothesId('');
        setBottomClothesId('');
        setAdditionalClothesId('');
        setAdditionalCategory('ALL');
        setStep(1);
    };

    // 실패 후 재시도 — 고른 모델·의상은 그대로 두고 의상 단계로
    const handleRetry = () => {
        reset();
        setStep(2);
    };

    const isLoading = job !== null && (job.status === 'PENDING' || job.status === 'PROCESSING');
    const isSucceeded = job?.status === 'SUCCEEDED';
    const isFailed = job?.status === 'FAILED';

    return (
        <div className="flex flex-col h-full px-10 py-8 gap-6 overflow-y-auto">
            <div className="shrink-0">
                <FittingStepper current={step} />
            </div>

            {step === 1 && (
                <>
                    <ModelSelectStep
                        source={modelSource}
                        gender={modelGender}
                        imagePreview={imagePreview}
                        onSelectExample={handleSelectExample}
                        onUpload={handleUpload}
                        onClearUpload={handleClearUpload}
                    />
                    <div className="flex justify-end shrink-0">
                        <PrimaryButton disabled={!canLeaveModelStep} onClick={() => setStep(2)}>
                            다음 →
                        </PrimaryButton>
                    </div>
                </>
            )}

            {step === 2 && (
                <>
                    <div className="grid grid-cols-3 gap-5 flex-1 min-h-[360px]">
                        <ClothesPickerGrid
                            title="상의"
                            required
                            clothes={topOptions}
                            selectedId={topClothesId}
                            onSelect={setTopClothesId}
                        />
                        <ClothesPickerGrid
                            title="하의"
                            required
                            clothes={bottomOptions}
                            selectedId={bottomClothesId}
                            onSelect={setBottomClothesId}
                        />
                        <ClothesPickerGrid
                            title="추가 의상"
                            clothes={additionalOptions}
                            selectedId={additionalClothesId}
                            onSelect={setAdditionalClothesId}
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

                    <div className="flex items-center justify-between shrink-0">
                        <SecondaryButton onClick={() => setStep(1)}>← 이전</SecondaryButton>
                        <PrimaryButton
                            disabled={submitting || !topClothesId || !bottomClothesId}
                            onClick={handleSubmit}
                        >
                            {submitting ? '요청 중...' : '가상피팅 생성'}
                        </PrimaryButton>
                    </div>
                </>
            )}

            {step === 3 && (
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
                                className="max-w-full min-h-0 flex-1 object-contain rounded-[12px]"
                            />
                            <SecondaryButton onClick={handleReset}>새로 만들기</SecondaryButton>
                        </div>
                    )}

                    {isFailed && (
                        <div className="flex flex-col items-center gap-4">
                            <p className="text-gray-700 text-[15px] text-center max-w-[520px]">
                                {job?.failureReason ?? '가상피팅 생성에 실패했습니다.'}
                            </p>
                            <div className="flex gap-2">
                                {job?.retryable && <PrimaryButton onClick={handleRetry}>다시 시도</PrimaryButton>}
                                <SecondaryButton onClick={handleReset}>처음부터</SecondaryButton>
                            </div>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}

function PrimaryButton({ children, disabled, onClick }: { children: ReactNode; disabled?: boolean; onClick: () => void }) {
    return (
        <button
            type="button"
            onClick={onClick}
            disabled={disabled}
            className="bg-blue-500 hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed h-[46px] px-6 rounded-[12px] shrink-0 transition-colors"
        >
            <span className="font-bold text-white text-[16px]">{children}</span>
        </button>
    );
}

function SecondaryButton({ children, onClick }: { children: ReactNode; onClick: () => void }) {
    return (
        <button
            type="button"
            onClick={onClick}
            className="bg-gray-100 hover:bg-gray-200 h-[46px] px-6 rounded-[12px] shrink-0 transition-colors"
        >
            <span className="font-bold text-gray-700 text-[16px]">{children}</span>
        </button>
    );
}
