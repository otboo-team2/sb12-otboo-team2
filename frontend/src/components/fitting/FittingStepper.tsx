const STEPS = ['모델 선택', '의상 선택', '결과 확인'] as const;

export type FittingStep = 1 | 2 | 3;

export default function FittingStepper({ current }: { current: FittingStep }) {
    return (
        <ol className="flex items-center gap-3">
            {STEPS.map((label, i) => {
                const step = i + 1;
                const done = step < current;
                const active = step === current;
                return (
                    <li key={label} className="flex items-center gap-3">
                        <div className="flex items-center gap-2">
                            <span
                                className={`flex size-7 items-center justify-center rounded-full text-sm font-bold transition-colors ${
                                    active
                                        ? 'bg-blue-500 text-white'
                                        : done
                                            ? 'bg-blue-100 text-blue-500'
                                            : 'bg-gray-100 text-gray-400'
                                }`}
                            >
                                {done ? '✓' : step}
                            </span>
                            <span className={`text-[15px] font-bold ${active ? 'text-gray-900' : 'text-gray-400'}`}>
                                {label}
                            </span>
                        </div>
                        {step < STEPS.length && <span className="h-px w-10 bg-gray-200" />}
                    </li>
                );
            })}
        </ol>
    );
}
