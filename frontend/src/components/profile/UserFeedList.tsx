import { useEffect, useState } from 'react';
import { useFeedStore } from '@/lib/stores/useFeedStore';
import FeedCard from '@/components/feeds/FeedCard';
import FeedCardSkeleton from '@/components/feeds/FeedCardSkeleton';
import FeedDetailModal from '@/components/feeds/FeedDetailModal';
import type { FeedDto } from '@/lib/api/types';

interface UserFeedListProps {
  userId: string;
}

export default function UserFeedList({ userId }: UserFeedListProps) {
  const { data: feeds, loading, fetch, fetchMore, updateParams, cursorState } = useFeedStore();
  const [page, setPage] = useState(0);
  const pageSize = 9;
  const [selectedFeed, setSelectedFeed] = useState<FeedDto | null>(null);
  const [modalOpen, setModalOpen] = useState(false);

  const handleFeedClick = (feed: FeedDto) => {
    setSelectedFeed(feed);
    setModalOpen(true);
  };

  const handleModalClose = () => {
    setModalOpen(false);
    setTimeout(() => setSelectedFeed(null), 300); // 애니메이션 완료 후 상태 정리
  };

  // 사용자 피드 데이터 로드
  useEffect(() => {
    if (userId) {
      setPage(0);
      updateParams({ authorIdEqual: userId });
      fetch();
    }
  }, [userId, updateParams, fetch]);

  const pageCount = Math.max(1, Math.ceil(cursorState.totalCount / pageSize));
  const visibleFeeds = feeds.slice(page * pageSize, (page + 1) * pageSize);

  const handlePageChange = async (nextPage: number) => {
    if (nextPage < 0 || nextPage >= pageCount || loading) return;
    const requiredItems = (nextPage + 1) * pageSize;
    while (useFeedStore.getState().data.length < requiredItems && useFeedStore.getState().cursorState.hasNext) {
      await fetchMore();
    }
    setPage(nextPage);
  };

  return (
    <div>
      {loading && feeds.length === 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 p-4">
          {Array.from({ length: 6 }).map((_, index) => (
            <FeedCardSkeleton key={`skeleton-${index}`} />
          ))}
        </div>
      ) : feeds.length === 0 ? (
        <div className="flex items-center justify-center py-20">
          <div className="text-gray-500 text-center">
            <p className="text-lg">아직 작성한 피드가 없습니다.</p>
          </div>
        </div>
      ) : (
        <div className="p-4">
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 w-full">
            {visibleFeeds.map((feed) => (
              <FeedCard 
                key={feed.id} 
                feed={feed} 
                onClick={() => handleFeedClick(feed)}
              />
            ))}

            {/* 무한 스크롤 로딩 중인 경우 하단에 스켈레톤 추가 */}
            {loading && feeds.length > 0 && 
              Array.from({ length: 3 }).map((_, index) => (
                <FeedCardSkeleton key={`loading-skeleton-${index}`} />
              ))
            }
          </div>

          {pageCount > 1 && (
            <div className="mt-8 flex items-center justify-center gap-2">
              <button
                type="button"
                onClick={() => handlePageChange(page - 1)}
                disabled={page === 0 || loading}
                className="rounded-lg px-3 py-2 text-sm text-gray-500 disabled:opacity-30"
              >
                이전
              </button>
              {Array.from({ length: pageCount }, (_, index) => (
                <button
                  key={index}
                  type="button"
                  onClick={() => handlePageChange(index)}
                  disabled={loading}
                  className={`size-9 rounded-lg text-sm font-semibold ${
                    page === index ? 'bg-black text-white' : 'text-gray-500 hover:bg-gray-100'
                  }`}
                >
                  {index + 1}
                </button>
              ))}
              <button
                type="button"
                onClick={() => handlePageChange(page + 1)}
                disabled={page === pageCount - 1 || loading}
                className="rounded-lg px-3 py-2 text-sm text-gray-500 disabled:opacity-30"
              >
                다음
              </button>
            </div>
          )}
        </div>
      )}

      {/* 피드 상세 모달 */}
      <FeedDetailModal 
        feed={selectedFeed}
        open={modalOpen}
        onOpenChange={handleModalClose}
      />
    </div>
  );
}
