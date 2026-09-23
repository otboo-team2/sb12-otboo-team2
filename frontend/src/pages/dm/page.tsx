import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDmConversationStore } from '@/lib/stores/useDmConversationStore';
import { useInfiniteScroll } from '@/lib/hooks/useInfiniteScroll.ts';
import type { DmConversationDto } from '@/lib/api/types';
import profileIcon from '@/assets/icons/profile.svg';

const formatTimeAgo = (createdAt: string) => {
    const now = new Date();
    const created = new Date(createdAt);
    const diffMs = now.getTime() - created.getTime();
    const diffMinutes = Math.floor(diffMs / (1000 * 60));

    if (diffMinutes < 1) return '방금 전';
    if (diffMinutes < 60) return `${diffMinutes}분 전`;

    const diffHours = Math.floor(diffMinutes / 60);
    if (diffHours < 24) return `${diffHours}시간 전`;

    const diffDays = Math.floor(diffHours / 24);
    if (diffDays < 7) return `${diffDays}일 전`;

    return created.toLocaleDateString('ko-KR');
};

export default function DmInboxPage() {
    const navigate = useNavigate();
    const { data: conversations, loading, fetch, fetchMore, clear } = useDmConversationStore();

    const { ref } = useInfiniteScroll({ onLoadMore: () => fetchMore() });

    useEffect(() => {
        fetch();
        return () => clear();
    }, [fetch, clear]);

    const openConversation = (conversation: DmConversationDto) => {
        navigate(`/dm/${conversation.partner.userId}`, {
            state: { partner: conversation.partner },
        });
    };

    return (
        <div className="flex flex-col h-full px-10 py-8 gap-6">
            <div className="flex-1 overflow-y-auto bg-white rounded-[16px] border border-gray-200">
                {loading && conversations.length === 0 ? (
                    <div className="flex items-center justify-center h-40 text-gray-400">불러오는 중...</div>
                ) : conversations.length === 0 ? (
                    <div className="flex items-center justify-center h-40 text-gray-400">아직 나눈 대화가 없어요</div>
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

                        <div ref={ref} className="h-1" />

                        {loading && conversations.length > 0 && (
                            <div className="p-4 text-center text-gray-400 text-sm">더 불러오는 중...</div>
                        )}
                    </>
                )}
            </div>
        </div>
    );
}
