import type { ReactNode } from 'react';
import type { ClothesDto } from '@/lib/api/types';

interface ClothesPickerGridProps {
    title: string;
    clothes: ClothesDto[];
    selectedId: string;
    onSelect: (id: string) => void;
    optional?: boolean;
    headerExtra?: ReactNode;
}

export default function ClothesPickerGrid({ title, clothes, selectedId, onSelect, optional, headerExtra }: ClothesPickerGridProps) {
    return (
        <div className="bg-neutral-50 rounded-[20px] border border-[#e7e7e9] shadow-[0px_2px_4px_0px_rgba(55,55,64,0.03)] flex flex-col gap-4 p-5 h-full min-h-0">
            <div className="flex items-center justify-between shrink-0 gap-2">
                <h3 className="font-bold text-gray-800 text-[16px] shrink-0">{title}</h3>
                <div className="flex items-center gap-2">
                    {optional && selectedId && (
                        <button
                            onClick={() => onSelect('')}
                            className="text-gray-400 hover:text-gray-600 text-[13px] underline shrink-0"
                        >
                            선택 해제
                        </button>
                    )}
                    {headerExtra}
                </div>
            </div>

            <div className="flex-1 min-h-0 overflow-y-auto">
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
                                    onClick={() => onSelect(c.id)}
                                    className={`flex flex-col gap-1.5 rounded-[12px] p-1.5 text-left transition-colors ${
                                        selected ? 'ring-2 ring-blue-500 bg-blue-50' : 'hover:bg-gray-100'
                                    }`}
                                >
                                    <div className="aspect-square bg-gray-200 rounded-[10px] overflow-hidden">
                                        {c.imageUrl ? (
                                            <img src={c.imageUrl} alt={c.name} className="w-full h-full object-cover" />
                                        ) : (
                                            <div className="w-full h-full flex items-center justify-center bg-gray-300 text-gray-500 text-[12px]">
                                                이미지 없음
                                            </div>
                                        )}
                                    </div>
                                    <p className="text-gray-800 text-[13px] font-semibold truncate">{c.name}</p>
                                </button>
                            );
                        })}
                    </div>
                )}
            </div>
        </div>
    );
}
