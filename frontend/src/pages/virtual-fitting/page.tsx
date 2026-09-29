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
import { BadgeCheck, Download, PawPrint } from 'lucide-react';
import fittingLoading from '@/assets/model/fitting/virtual-fitting-loading.png';
import fittingResultCharacter from '@/assets/model/fitting/virtual-fitting-result.png';

const ADDITIONAL_CATEGORIES: { label: string; value: ClothesType | 'ALL' }[] = [
    { label: '전체', value: 'ALL' },
    { label: '아우터', value: 'OUTER' },
    { label: '액세서리', value: 'ACCESSORY' },
    { label: '신발', value: 'SHOES' },
    { label: '모자', value: 'HAT' },
    { label: '가방', value: 'BAG' },
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
    const { job, start, resume, reset } = useFittingStore();

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

    const handleDownload = async () => {
        if (!job?.resultImageUrl) return;
        try {
            const response = await fetch(job.resultImageUrl);
            const blob = await response.blob();
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = 'virtual-fitting-result.png';
            link.click();
            URL.revokeObjectURL(url);
        } catch {
            window.open(job.resultImageUrl, '_blank', 'noopener,noreferrer');
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
                <div className="flex-1 min-h-0 bg-[#fcfdff] rounded-[20px] border border-[#eaf2fc] flex items-center justify-center p-6 lg:p-8 overflow-hidden">
                    {isLoading && (
                        <div className="flex w-full max-w-[680px] flex-col items-center rounded-[24px] px-8 py-10 text-center">
                            <img src={fittingLoading} alt="가상피팅 생성 중" className="w-full max-w-[520px] object-contain" />
                            <h2 className="mt-3 text-[24px] font-extrabold tracking-[-0.5px] text-slate-900">
                                선택한 옷을 입혀보고 있어요!
                            </h2>
                            <p className="mt-1 text-[16px] font-medium text-slate-500">완성되면 알림으로 알려드릴게요.</p>
                            <div className="mt-5 flex items-center gap-2" aria-hidden="true">
                                <span className="size-3 animate-pulse rounded-full bg-blue-500 [animation-delay:-0.4s]" />
                                <span className="size-3 animate-pulse rounded-full bg-blue-300 [animation-delay:-0.2s]" />
                                <span className="size-3 animate-pulse rounded-full bg-blue-200" />
                            </div>
                        </div>
                    )}

                    {isSucceeded && job?.resultImageUrl && (
                        <div className="relative flex h-full w-full max-w-none self-stretch flex-col gap-5 overflow-hidden">
                            <div className="pointer-events-none absolute inset-0 overflow-hidden" aria-hidden="true">
                                <div className="absolute bottom-[2%] left-[5%] h-[78%] w-[88%] rounded-[50%] bg-gradient-to-t from-[#c7e0ff]/78 via-[#dcecff]/52 to-transparent blur-[2px]" />
                            </div>
                            <div className="-mt-2 shrink-0 text-left">
                                <div className="flex items-center">
                                    <div>
                                        <h1 className="flex items-center gap-2 text-[28px] font-extrabold tracking-[-0.7px] text-slate-900">
                                            가상피팅 결과
                                            <BadgeCheck aria-hidden="true" className="size-6 text-blue-500" strokeWidth={2} />
                                        </h1>
                                        <p className="mt-1 text-[14px] font-medium text-slate-500">선택한 의상이 모델에게 입혀졌어요.</p>
                                    </div>
                                </div>
                            </div>
                            <div className="relative z-10 flex min-h-0 flex-1 flex-col items-stretch justify-start gap-2 lg:flex-row">
                            <div className="relative min-h-0 min-w-0 flex-[1.7] flex items-end justify-center gap-0 overflow-hidden">
                                <div className="relative z-20 h-52 w-40 shrink-0 translate-x-[25px] translate-y-3 self-end">
                                    <div className="absolute left-1/2 top-[-28px] z-20 flex -translate-x-1/2 items-end gap-3">
                                        <PawPrint fill="currentColor" className="translate-y-2 size-5 rotate-[-18deg] text-[#c4dcfa]/80" />
                                        <PawPrint fill="currentColor" className="-translate-y-2 size-6 text-[#c4dcfa]/85" />
                                        <PawPrint fill="currentColor" className="translate-y-2 size-5 rotate-[18deg] text-[#c4dcfa]/80" />
                                    </div>
                                    <img src={fittingResultCharacter} alt="가상피팅 완료 캐릭터" className="size-full object-contain" />
                                </div>
                                <img
                                    src={job.resultImageUrl}
                                    alt="가상피팅 결과"
                                    className="relative z-10 h-full min-w-0 max-w-[calc(100%-12rem)] w-auto rounded-[24px] object-contain"
                                />
                            </div>
                            <div className="flex w-full shrink-0 flex-col gap-5 rounded-[20px] border border-slate-100 bg-white p-7 lg:w-[520px] lg:-translate-x-24">
                                <div>
                                    <div className="flex items-center justify-between gap-3">
                                        <p className="font-bold text-gray-900 text-[20px]">선택한 의상</p>
                                        <span className="rounded-full bg-blue-50 px-2.5 py-1 text-[12px] font-semibold text-blue-600">
                                            {[job.topClothes, job.bottomClothes, job.additionalClothes].filter(Boolean).length}개
                                        </span>
                                    </div>
                                    <p className="text-gray-500 text-[13px] mt-1">이번 가상피팅에 사용한 아이템이에요</p>
                                </div>
                                <div className="flex flex-col gap-3">
                                    {[job.topClothes, job.bottomClothes, job.additionalClothes]
                                        .filter((clothes): clothes is NonNullable<typeof clothes> => clothes !== null)
                                        .map((clothes) => (
                                        <div key={clothes.id} className="flex items-center gap-4 rounded-[18px] bg-[#f7f9fe] border border-[#edf1fa] p-4 transition-colors hover:border-blue-200 hover:bg-blue-50/40 min-w-0">
                                            <div className="size-24 rounded-[10px] bg-slate-200 overflow-hidden shrink-0">
                                                {clothes.imageUrl && (
                                                    <img src={clothes.imageUrl} alt="" className="size-full object-cover" />
                                                )}
                                            </div>
                                            <div className="min-w-0 flex flex-col gap-1">
                                                <span className="w-fit rounded-full bg-white px-2 py-0.5 text-blue-600 text-[11px] font-semibold">{clothesTypeLabel(clothes.type)}</span>
                                                <span className="text-gray-800 text-[17px] font-medium whitespace-normal break-words leading-6">{clothes.name}</span>
                                            </div>
                                        </div>
                                        ))}
                                </div>
                                <button type="button" onClick={handleDownload} className="flex h-[54px] items-center justify-center gap-3 rounded-[14px] bg-slate-900 text-[18px] font-bold text-white transition-colors hover:bg-slate-800">
                                    <Download className="size-6" />
                                    이미지 저장하기
                                </button>
                                <SecondaryButton onClick={handleReset}>새로 만들기</SecondaryButton>
                            </div>
                            </div>
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

function clothesTypeLabel(type: string) {
    const labels: Record<string, string> = {
        TOP: '상의',
        BOTTOM: '하의',
        OUTER: '아우터',
        SHOES: '신발',
        ACCESSORY: '액세서리',
        HAT: '모자',
        BAG: '가방',
        SCARF: '목도리',
    };
    return labels[type] ?? '추가 의상';
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
