import type { ChangeEvent } from 'react';
import { AlertCircle, Check, ImagePlus, X } from 'lucide-react';
import modelMale from '@/assets/model/model-male.png';
import modelFemale from '@/assets/model/model-female.png';

export type ModelGender = 'MALE' | 'FEMALE';
export type ModelSource = 'EXAMPLE' | 'UPLOAD';

export const EXAMPLE_MODEL_IMAGES: Record<ModelGender, string> = {
    MALE: modelMale,
    FEMALE: modelFemale,
};

const GENDER_LABEL: Record<ModelGender, string> = {
    MALE: '남성',
    FEMALE: '여성',
};

interface ModelSelectStepProps {
    source: ModelSource;
    gender: ModelGender;
    imagePreview: string | null;
    onSelectExample: (gender: ModelGender) => void;
    onUpload: (e: ChangeEvent<HTMLInputElement>) => void;
    onClearUpload: () => void;
}

export default function ModelSelectStep({
                                            source, gender, imagePreview, onSelectExample, onUpload, onClearUpload,
                                        }: ModelSelectStepProps) {
    const exampleActive = source === 'EXAMPLE';
    const uploadActive = source === 'UPLOAD';

    return (
        <div className="grid min-h-[560px] flex-1 grid-cols-2 gap-7">
            {/* 왼쪽: 예시 모델 */}
            <section className={cardClass(exampleActive)}>
                {exampleActive && <SelectedBadge />}
                <h3 className="text-[22px] font-extrabold text-gray-900">기본 모델 사용</h3>
                <p className="mt-1 text-[15px] font-medium text-gray-500">예시 모델에 옷을 입혀볼 수 있어요.</p>

                <button
                    type="button"
                    onClick={() => onSelectExample(gender)}
                    className="relative mt-4 min-h-0 flex-1 overflow-hidden rounded-2xl bg-white"
                >
                    <img
                        src={EXAMPLE_MODEL_IMAGES[gender]}
                        alt={`${GENDER_LABEL[gender]} 예시 모델`}
                        className="absolute inset-0 h-full w-full object-contain"
                    />
                </button>

                <div className="mt-4 grid grid-cols-2 gap-1 rounded-full bg-gray-100 p-1">
                    {(['MALE', 'FEMALE'] as const).map((g) => {
                        const on = exampleActive && gender === g;
                        return (
                            <button
                                key={g}
                                type="button"
                                onClick={() => onSelectExample(g)}
                                aria-pressed={on}
                                className={`h-10 rounded-full text-[15px] font-bold transition-colors ${
                                    on ? 'bg-white text-blue-500 shadow-sm' : 'text-gray-500 hover:text-gray-700'
                                }`}
                            >
                                {GENDER_LABEL[g]}
                            </button>
                        );
                    })}
                </div>
            </section>

            {/* 오른쪽: 내 사진 등록 */}
            <section className={cardClass(uploadActive)}>
                {uploadActive && <SelectedBadge />}
                <h3 className="text-[22px] font-extrabold text-gray-900">내 사진 등록</h3>
                <p className="mt-1 text-[15px] font-medium text-gray-500">전신이 잘 보이는 정면 사진을 올려주세요.</p>

                <label className="relative mt-4 flex min-h-0 flex-1 cursor-pointer items-center justify-center overflow-hidden rounded-2xl border-2 border-dashed border-gray-200 bg-white transition-colors hover:border-blue-300">
                    {imagePreview ? (
                        <img
                            src={imagePreview}
                            alt="내 모델 사진"
                            className="absolute inset-0 h-full w-full object-contain"
                        />
                    ) : (
                        <div className="flex flex-col items-center gap-2 text-gray-400">
                            <div className="flex size-14 items-center justify-center rounded-xl bg-blue-50 text-blue-500"><ImagePlus className="size-8" /></div>
                            <span className="text-[18px] font-extrabold text-gray-800">사진을 선택하기</span>
                        </div>
                    )}
                    <input type="file" accept="image/*" onChange={onUpload} className="hidden" />
                </label>

                {imagePreview ? (
                    <button
                        type="button"
                        onClick={onClearUpload}
                        className="mt-4 h-10 rounded-full bg-gray-100 text-[15px] font-bold text-gray-600 transition-colors hover:bg-gray-200"
                    >
                        사진 삭제
                    </button>
                ) : (
                    <div className="mt-4 flex w-full items-center justify-between gap-5 rounded-2xl bg-blue-50/70 px-6 py-5 text-left">
                        <div className="flex flex-col items-start gap-3">
                            <p className="flex items-center gap-2 text-[18px] font-extrabold text-gray-700"><AlertCircle className="size-5 text-blue-500" />좋은 사진을 올려주세요</p>
                            <div className="flex flex-col items-start gap-1 text-left text-[13px] leading-5 text-gray-500">
                                <p className="flex items-center gap-2"><Check className="size-4 text-blue-500" />전신이 잘 보이는 정면 사진</p>
                                <p className="flex items-center gap-2"><Check className="size-4 text-blue-500" />밝은 배경에서 촬영한 사진</p>
                                <p className="flex items-center gap-2"><Check className="size-4 text-blue-500" />얼굴과 몸이 가려지지 않은 사진</p>
                            </div>
                        </div>
                        <div className="flex shrink-0 gap-2">
                            {[EXAMPLE_MODEL_IMAGES.FEMALE, EXAMPLE_MODEL_IMAGES.MALE, EXAMPLE_MODEL_IMAGES.FEMALE].map((src, index) => (
                                <div key={`${src}-${index}`} className="relative h-36 w-24 overflow-hidden rounded-xl bg-slate-300 ring-2 ring-white">
                                    <img src={src} alt="사진 예시" className={`size-full object-contain ${index === 1 ? 'brightness-50' : index === 2 ? 'brightness-35' : ''}`} />
                                    {index === 1 && <div className="absolute left-2 right-2 top-5 h-5 rounded bg-slate-900/90" />}
                                    {index === 0 ? <Check className="absolute right-1 top-1 size-5 rounded-full bg-blue-500 p-0.5 text-white" /> : <X className="absolute right-1 top-1 size-5 rounded-full bg-red-500 p-0.5 text-white" />}
                                </div>
                            ))}
                        </div>
                        <p className="text-[12px] font-semibold text-gray-400">JPG · PNG, 최대 5MB</p>
                    </div>
                )}
            </section>
        </div>
    );
}

function cardClass(active: boolean) {
    return `relative flex min-h-0 flex-col rounded-[24px] border-2 p-6 transition-colors ${
        active ? 'border-blue-400 bg-blue-50' : 'border-[#e7e7e9] bg-neutral-50'
    }`;
}

function SelectedBadge() {
    return (
        <span className="absolute -right-2 -top-2 z-10 flex size-7 items-center justify-center rounded-full bg-blue-500 text-sm font-bold text-white shadow">
            ✓
        </span>
    );
}
