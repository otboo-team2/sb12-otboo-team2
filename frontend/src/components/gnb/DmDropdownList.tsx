import React, { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDmConversationStore } from '@/lib/stores/useDmConversationStore';
import { useInfiniteScroll } from '@/lib/hooks/useInfiniteScroll.ts';
import type { DmConversationDto } from '@/lib/api/types';
import profileIcon from '@/assets/icons/profile.svg';

interface DmDropdownListProps {
    isOpen: boolean;
    onClose: () => void;
    anchorElement?: HTMLElement | null;
}

const formatTimeAgo = (createdAt: string) => {
    const now = new Date();
    const created = new Date(createdAt);
    const diffMinutes = Math.floor((now.getTime() - created.getTime()) / (1000 * 60));
    if (diffMinutes < 1) return '방금 전';
    if (diffMinutes < 60) return `${diffMinutes}분 전`;
    const diffHours = Math.floor(diffMinutes / 60);
    if (diffHours < 24) return `${diffHours}시간 전`;
    const diffDays = Math.floor(diffHours / 24);
    if (diffDays < 7) return `${diffDays}일 전`;
    return created.toLocaleDateString('ko-KR');
};

export const DmDropdownList = ({ isOpen, onClose, anchorElement }: DmDropdownListProps) => {
    const navigate = useNavigate();
    const { data: conversations, loading, fetch, fetchMore } = useDmConversationStore();
    const listRef = useRef<HTMLDivElement>(null);

    const { ref: infiniteScrollRef } = useInfiniteScroll({ onLoadMore: () => fetchMore() });

    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (
                isOpen &&
                listRef.current &&
                !listRef.current.contains(event.target as Node) &&
                anchorElement &&
                !anchorElement.contains(event.target as Node)
            ) {
                onClose();
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, [isOpen, onClose, anchorElement]);

    useEffect(() => {
        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape' && isOpen) onClose();
        };
        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, onClose]);

    useEffect(() => {
        if (isOpen) {
            fetch();
        }
    }, [isOpen, fetch]);

    const openConversation = (conv: DmConversationDto) => {
        onClose();
        navigate(`/dm/${conv.partner.userId}`, { state: { partner: conv.partner } });
    };

    if (!isOpen) return null;

    const getPopupStyle = (): React.CSSProperties => {
        if (!anchorElement) return {};
        const rect = anchorElement.getBoundingClientRect();
        return {
            position: 'fixed' as const,
            top: rect.bottom + 8,
            right: window.innerWidth - rect.right,
            width: 380,
            height: 500,
            zIndex: 1000,
        };
    };

    return (
        <div
            ref={listRef}
            className="bg-white rounded-[20px] border border-[var(--color-gray-200)] shadow-[0px_2px_10px_0px_rgba(41,52,57,0.14)] overflow-hidden"
            style={getPopupStyle()}
        >
            <div className="flex flex-col h-full overflow-y-auto">
                {conversations.length === 0 ? (
                    loading ? null : (
                        <div className="flex items-center justify-center h-full text-[var(--color-gray-400)]">
                            아직 나눈 대화가 없어요
                        </div>
                    )
                ) : (
                    <>
                        {conversations.map((conv) => (
                            <button
                                key={conv.messageId}
                                onClick={() => openConversation(conv)}
                                className="flex items-center gap-3 w-full px-5 py-4 border-b border-gray-100 last:border-0 hover:bg-gray-50 text-left transition-colors"
                            >
                                <div className="bg-[#a9a9b1] relative rounded-full shrink-0 size-[44px] overflow-hidden">
                                    <img
                                        src={conv.partner.profileImageUrl || profileIcon}
                                        alt={conv.partner.name}
                                        className="w-full h-full object-cover"
                                    />
                                </div>
                                <div className="flex-1 min-w-0">
                                    <p className="font-semibold text-gray-900 text-[15px]">{conv.partner.name}</p>
                                    <p className="text-gray-500 text-[14px] truncate">{conv.lastMessageContent}</p>
                                </div>
                                <span className="text-gray-400 text-[12px] shrink-0">{formatTimeAgo(conv.lastMessageAt)}</span>
                            </button>
                        ))}
                        <div ref={infiniteScrollRef} className="h-1" />
                        {loading && conversations.length > 0 && (
                            <div className="p-4 text-center text-gray-400 text-sm">더 불러오는 중...</div>
                        )}
                    </>
                )}
            </div>
        </div>
    );
};
