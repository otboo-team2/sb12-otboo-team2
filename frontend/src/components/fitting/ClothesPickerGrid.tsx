import type { ReactNode } from 'react';
import type { ClothesDto } from '@/lib/api/types';

interface ClothesPickerGridProps {
    title: string;
    clothes: ClothesDto[];
    selectedId: string;
    onSelect: (id: string) => void;
    required?: boolean;
    headerExtra?: ReactNode;
}

export default function ClothesPickerGrid({ title, clothes, selectedId, onSelect, required, headerExtra }: ClothesPickerGridProps) {
    return (
        <div className="bg-neutral-50 rounded-[20px] border border-[#e7e7e9] shadow-[0px_2px_4px_0px_rgba(55,55,64,0.03)] flex flex-col gap-4 p-5 h-full min-h-0">
            <div className="flex items-center justify-between shrink-0 gap-2">
                <div className="flex items-center gap-2 shrink-0">
                    <h3 className="font-bold text-gray-800 text-[16px]">{title}</h3>
                    {required && (
                        <span
                            className={`rounded-full px-2 py-0.5 text-[11px] font-bold leading-none transition-colors ${
                                selectedId ? 'bg-blue-50 text-blue-500' : 'bg-rose-50 text-rose-500'
                            }`}
                        >
                            필수
                        </span>
                    )}
                </div>
                <div className="flex items-center gap-2">
                    {headerExtra}
                </div>
            </div>

            <div className="flex-1 min-h-0 overflow-y-auto overflow-x-hidden -mx-2 px-2 pt-2 pb-1">
                {clothes.length === 0 ? (
                    <p className="text-gray-400 text-[14px] py-6 text-center">옷장에 옷이 없어요</p>
                ) : (
                    <div className="grid grid-cols-2 gap-3">
                        {clothes.map((c) => {
                            const selected = c.id === selectedId;
                            return (
                                <button
                                    key={c.id}
                                    type="button"
                                    onClick={() => onSelect(selected ? '' : c.id)}
                                    aria-pressed={selected}
                                    className={`relative flex flex-col gap-1.5 rounded-xl border-2 bg-white p-1.5 text-left transition-colors ${
                                        selected ? 'border-blue-400 bg-blue-50 shadow-sm' : 'border-gray-100 hover:border-gray-200'
                                    }`}
                                >
                                    <div className="aspect-square overflow-hidden rounded-[10px] bg-white">
                                        {c.imageUrl ? (
                                            <img src={c.imageUrl} alt={c.name} className="h-full w-full object-contain" />
                                        ) : (
                                            <div className="flex h-full w-full items-center justify-center bg-gray-100 text-[12px] text-gray-400">
                                                이미지 없음
                                            </div>
                                        )}
                                    </div>
                                    <p className="truncate text-[13px] font-semibold text-gray-800">{c.name}</p>
                                    {selected && (
                                        <span className="absolute -right-2 -top-2 z-10 flex size-6 items-center justify-center rounded-full bg-blue-500 text-xs font-bold text-white shadow">
                                            ✓
                                        </span>
                                    )}
                                </button>
                            );
                        })}
                    </div>
                )}
            </div>
        </div>
    );
}
