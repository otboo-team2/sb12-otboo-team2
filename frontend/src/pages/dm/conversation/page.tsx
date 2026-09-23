import { Fragment, useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import profileIcon from '@/assets/icons/profile.svg';
import sendIcon from '@/assets/icons/ic_send.svg';
import { useWebSocketStore } from '@/lib/stores/websocketStore.ts';
import { useAuthStore } from '@/lib/stores/useAuthStore.ts';
import { useDirectMessageStore } from '@/lib/stores/useDirectMessageStore.ts';
import { useActiveDmStore } from '@/lib/stores/useActiveDmStore';
import { useInfiniteScroll } from '@/lib/hooks/useInfiniteScroll.ts';
import { getProfile } from '@/lib/api/users.ts';
import type { UserSummary } from '@/lib/api/types';

interface TargetUser {
    id: string;
    name: string;
    profileImageUrl?: string;
}

export default function DmConversationPage() {
    const { userId } = useParams<{ userId: string }>();
    const location = useLocation();
    const navigate = useNavigate();
    const statePartner = (location.state as { partner?: UserSummary } | null)?.partner;

    const [targetUser, setTargetUser] = useState<TargetUser | null>(
        statePartner
            ? { id: statePartner.userId, name: statePartner.name, profileImageUrl: statePartner.profileImageUrl }
            : null
    );

    const { send, isConnected, subscribe, unsubscribe } = useWebSocketStore();
    const { data: auth } = useAuthStore();
    const {
        data: messages,
        add,
        updateParams,
        clearData: clearMessages,
        fetchMore,
        loading,
        hasNext,
    } = useDirectMessageStore();

    const [content, setContent] = useState('');
    const messagesEndRef = useRef<HTMLDivElement>(null);
    const messagesContainerRef = useRef<HTMLDivElement>(null);
    const hasInitiallyScrolledRef = useRef(false);
    const lastMessageIdRef = useRef<string | null>(null);
    const isLoadingMoreRef = useRef(false);
    const scrollRestoreRef = useRef<{ height: number; top: number } | null>(null);

    const isFirstMessage = messages.length === 0;

    const scrollToBottom = useCallback((smooth = true) => {
        messagesEndRef.current?.scrollIntoView({ behavior: smooth ? 'smooth' : 'instant', block: 'end' });
    }, []);

    const handleLoadMore = useCallback(() => {
        if (loading || !hasNext()) return;

        const container = messagesContainerRef.current;
        if (!container) return;

        isLoadingMoreRef.current = true;
        scrollRestoreRef.current = {
            height: container.scrollHeight,
            top: container.scrollTop,
        };

        fetchMore();
    }, [loading, hasNext, fetchMore]);

    const { ref } = useInfiniteScroll({
        onLoadMore: handleLoadMore,
        rootMargin: '10px',
        threshold: 1,
    });

    const setActivePartnerId = useActiveDmStore((s) => s.setActivePartnerId);

    useEffect(() => {
        if (statePartner) {
            setTargetUser({
                id: statePartner.userId,
                name: statePartner.name,
                profileImageUrl: statePartner.profileImageUrl,
            });
        } else if (userId) {
            getProfile({ userId }).then((profile) =>
                setTargetUser({ id: profile.userId, name: profile.name, profileImageUrl: profile.profileImageUrl })
            );
        }
    }, [userId, statePartner]);

    useEffect(() => {
        if (userId) {
            updateParams({ userId });
            hasInitiallyScrolledRef.current = false;
            lastMessageIdRef.current = null;
            isLoadingMoreRef.current = false;
            scrollRestoreRef.current = null;
        }
    }, [userId, updateParams]);

    const resolveDestination = useCallback((senderId: string, receiverId: string) => {
        let dest = '/sub/direct-messages_';
        if (senderId.localeCompare(receiverId) < 0) {
            dest = dest.concat(senderId).concat('_').concat(receiverId);
        } else {
            dest = dest.concat(receiverId).concat('_').concat(senderId);
        }
        return dest;
    }, []);

    useEffect(() => {
        if (!auth || !userId || !isConnected) return;
        const destination = resolveDestination(auth.userDto.id, userId);
        subscribe(destination, (message) => {
            add(message);
        });

        return () => {
            unsubscribe(destination);
        };
    }, [subscribe, unsubscribe, add, auth, userId, isConnected, resolveDestination]);

    useEffect(() => {
        if (userId) {
            setActivePartnerId(userId);
        }
        return () => setActivePartnerId(null);
    }, [userId, setActivePartnerId]);

    const sendMessage = useCallback(async () => {
        if (!isConnected || !auth || !userId || !content.trim()) return;
        const message = {
            senderId: auth.userDto.id,
            receiverId: userId,
            content: content.trim(),
        };
        send('/pub/direct-messages_send', message);
        setContent('');
    }, [isConnected, auth, userId, content, send]);

    const handleKeyPress = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    };

    // 메시지 목록이 바뀔 때 스크롤을 어떻게 처리할지: 초기 로드 / 과거 메시지 불러오기 / 새 메시지 도착
    // 세 가지 경우를 여기 한 곳에서만 판단한다.
    useEffect(() => {
        if (messages.length === 0) return;

        const container = messagesContainerRef.current;

        // 과거 메시지를 위로 불러온 경우 → 스크롤 위치만 보정하고 끝
        if (isLoadingMoreRef.current) {
            isLoadingMoreRef.current = false;
            const restore = scrollRestoreRef.current;
            scrollRestoreRef.current = null;
            if (container && restore) {
                container.scrollTop = restore.top + (container.scrollHeight - restore.height);
            }
            const latest = messages.reduce((a, b) =>
                new Date(a.createdAt).getTime() > new Date(b.createdAt).getTime() ? a : b
            );
            lastMessageIdRef.current = latest.id;
            return;
        }

        const latest = messages.reduce((a, b) =>
            new Date(a.createdAt).getTime() > new Date(b.createdAt).getTime() ? a : b
        );

        // 대화방 처음 열었을 때 → 맨 아래로 (딱 한 번)
        if (!hasInitiallyScrolledRef.current) {
            scrollToBottom(false);
            hasInitiallyScrolledRef.current = true;
            lastMessageIdRef.current = latest.id;
            return;
        }

        // 맨 뒤에 진짜 새 메시지가 추가된 경우 → 내가 보낸 것일 때만 맨 아래로
        if (latest.id !== lastMessageIdRef.current) {
            lastMessageIdRef.current = latest.id;
            if (latest.sender.userId === auth?.userDto.id) {
                scrollToBottom(true);
            }
        }
    }, [messages, scrollToBottom, auth]);

    useEffect(() => {
        return () => {
            clearMessages();
        };
    }, [clearMessages]);

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

    if (!targetUser) {
        return <div className="flex items-center justify-center h-full text-gray-400">불러오는 중...</div>;
    }

    return (
        <div className="flex flex-col h-full bg-white">
            {/* 헤더 */}
            <div className="flex gap-2 items-center px-5 py-3 border-b border-[#e7e7e9]">
                <button onClick={() => navigate('/dm')} className="p-2 -ml-2 rounded-full hover:bg-gray-100">
                    ←
                </button>
                <div className="bg-[#a9a9b1] relative rounded-[100px] shrink-0 size-[30px] overflow-hidden">
                    <img
                        src={targetUser.profileImageUrl || profileIcon}
                        alt={targetUser.name}
                        className="w-full h-full object-cover rounded-[100px]"
                    />
                </div>
                <div className="font-['SUIT:SemiBold',_sans-serif] text-[#34343d] text-[18px] tracking-[-0.45px] leading-[0] not-italic">
                    <p className="leading-[normal] whitespace-pre">{targetUser.name}</p>
                </div>
            </div>

            {/* 메시지 영역 */}
            <div className="flex-1 overflow-hidden px-5">
                {(loading && messages.length === 0) || isFirstMessage ? (
                    <div className="flex items-center justify-center h-full">
                        <div className="font-['SUIT:Bold',_sans-serif] text-[#a9a9b1] text-[24px] text-center tracking-[-0.6px] leading-[1.6] not-italic">
                            <p>{targetUser.name} 님과의 대화를 시작해보세요</p>
                        </div>
                    </div>
                ) : (
                    <div className="h-full overflow-y-auto" id="messages-container" ref={messagesContainerRef}>
                        <div ref={ref} className="w-full h-1" />

                        {loading && messages.length > 0 && (
                            <div className="flex justify-center py-4">
                                <div className="flex gap-3 items-start w-full max-w-md">
                                    <div className="bg-gray-200 rounded-[100px] size-[30px] animate-pulse" />
                                    <div className="flex-1">
                                        <div className="bg-gray-200 rounded-[16px] h-12 w-3/4 animate-pulse" />
                                    </div>
                                </div>
                            </div>
                        )}

                        <div className="flex flex-col gap-6 py-4">
                            <div className="flex flex-col gap-[18px]">
                                {messages
                                    .slice()
                                    .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
                                    .map((msg, index, sorted) => {
                                        const previous = sorted[index - 1];
                                        const isNewDay =
                                            !previous ||
                                            new Date(previous.createdAt).toDateString() !== new Date(msg.createdAt).toDateString();

                                        return (
                                            <Fragment key={msg.id}>
                                                {isNewDay && (
                                                    <div className="font-['SUIT:SemiBold',_sans-serif] text-[#808089] text-[14px] text-center tracking-[-0.35px] leading-[0] not-italic">
                                                        <p className="leading-[normal]">
                                                            {new Date(msg.createdAt).toLocaleDateString('ko-KR', {
                                                                year: '2-digit',
                                                                month: 'long',
                                                                day: 'numeric',
                                                            })}
                                                        </p>
                                                    </div>
                                                )}
                                                <div>
                                                    {msg.sender.userId === auth?.userDto.id ? (
                                                        <div className="flex gap-3 items-end justify-end">
                                                            <div className="flex gap-2 items-center px-0 py-1.5">
                                                                <div className="font-['SUIT:SemiBold',_sans-serif] text-[#808089] text-[14px] tracking-[-0.35px] leading-[0] not-italic">
                                                                    <p className="leading-[normal] whitespace-pre">{formatTimeAgo(msg.createdAt)}</p>
                                                                </div>
                                                            </div>
                                                            <div className="bg-[#1e89f4] px-[19px] py-3.5 rounded-[16px] max-w-[360px]">
                                                                <div className="font-['SUIT:SemiBold',_sans-serif] text-white text-[18px] tracking-[-0.45px] leading-[0] not-italic">
                                                                    <p className="leading-[normal] whitespace-pre-wrap break-words">{msg.content}</p>
                                                                </div>
                                                            </div>
                                                        </div>
                                                    ) : (
                                                        <div className="flex gap-3 items-start">
                                                            <div className="flex gap-2 items-center px-0 py-1">
                                                                <div className="bg-[#a9a9b1] relative rounded-[100px] shrink-0 size-[30px] overflow-hidden">
                                                                    <img
                                                                        src={targetUser.profileImageUrl || profileIcon}
                                                                        alt={targetUser.name}
                                                                        className="w-full h-full object-cover rounded-[100px]"
                                                                    />
                                                                </div>
                                                            </div>
                                                            <div className="flex gap-3 items-end">
                                                                <div className="bg-[#f2f2f3] px-[18px] py-3.5 rounded-[16px] inline-block w-fit max-w-[360px]">
                                                                    <div className="font-['SUIT:SemiBold',_sans-serif] text-[#212126] text-[18px] tracking-[-0.35px] leading-[0] not-italic">
                                                                        <p className="leading-[normal] whitespace-pre-wrap break-words">{msg.content}</p>
                                                                    </div>
                                                                </div>
                                                                <div className="flex gap-2 items-center px-0 py-1.5">
                                                                    <div className="font-['SUIT:SemiBold',_sans-serif] text-[#808089] text-[14px] tracking-[-0.35px] leading-[0] not-italic">
                                                                        <p className="leading-[normal] whitespace-pre">{formatTimeAgo(msg.createdAt)}</p>
                                                                    </div>
                                                                </div>
                                                            </div>
                                                        </div>
                                                    )}
                                                </div>
                                            </Fragment>
                                        );
                                    })}
                            </div>

                            <div ref={messagesEndRef} className="h-1" />
                        </div>
                    </div>
                )}
            </div>

            {/* 입력 영역 */}
            <div className="flex flex-col gap-2 items-center px-5 py-3">
                <div className="bg-white h-[54px] relative rounded-[100px] w-full border-[#e7e7e9] border-[1.5px]">
                    <div className="flex items-center justify-between h-full pl-5 pr-3 py-3.5">
                        <input
                            type="text"
                            value={content}
                            onChange={(e) => setContent(e.target.value)}
                            onKeyPress={handleKeyPress}
                            placeholder="메시지 입력..."
                            className="flex-1 font-['SUIT:SemiBold',_sans-serif] text-[18px] text-[#212126] tracking-[-0.45px] bg-transparent border-none outline-none placeholder:text-[#a9a9b1]"
                        />
                        <button
                            onClick={sendMessage}
                            disabled={!content.trim()}
                            className="flex gap-2 items-center justify-center p-[10px] rounded-[100px] hover:bg-gray-50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            <div className="overflow-clip relative size-6">
                                <img src={sendIcon} alt="메시지 보내기" className="block max-w-none size-full" />
                            </div>
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}
