import type { ChangeEvent } from 'react';
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
        <div className="grid min-h-[480px] flex-1 grid-cols-2 gap-6">
            {/* 왼쪽: 예시 모델 */}
            <section className={cardClass(exampleActive)}>
                {exampleActive && <SelectedBadge />}
                <h3 className="text-[18px] font-extrabold text-gray-900">기본 모델 사용</h3>
                <p className="mt-1 text-[14px] text-gray-500">예시 모델에 옷을 입혀볼 수 있어요.</p>

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
                <h3 className="text-[18px] font-extrabold text-gray-900">내 사진 등록</h3>
                <p className="mt-1 text-[14px] text-gray-500">전신이 잘 보이는 정면 사진을 올려주세요.</p>

                <label className="relative mt-4 flex min-h-0 flex-1 cursor-pointer items-center justify-center overflow-hidden rounded-2xl border-2 border-dashed border-gray-200 bg-white transition-colors hover:border-blue-300">
                    {imagePreview ? (
                        <img
                            src={imagePreview}
                            alt="내 모델 사진"
                            className="absolute inset-0 h-full w-full object-contain"
                        />
                    ) : (
                        <div className="flex flex-col items-center gap-2 text-gray-400">
                            <span className="text-4xl leading-none">＋</span>
                            <span className="text-[14px] font-semibold">사진 선택하기</span>
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
                    <p className="mt-4 flex h-10 items-center justify-center text-[13px] text-gray-400">
                        JPG · PNG, 최대 5MB
                    </p>
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
