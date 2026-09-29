import { useEffect, useState } from 'react';
import { useFeedStore } from '@/lib/stores/useFeedStore';
import FeedCard from '@/components/feeds/FeedCard';
import FeedCardSkeleton from '@/components/feeds/FeedCardSkeleton';
import FeedDetailModal from '@/components/feeds/FeedDetailModal';
import Pagination from '@/components/ui/pagination';
import type { FeedDto } from '@/lib/api/types';

interface UserFeedListProps {
  userId: string;
  wide?: boolean;
}

export default function UserFeedList({ userId, wide = false }: UserFeedListProps) {
  const { data: feeds, loading, fetch, fetchMore, updateParams, cursorState } = useFeedStore();
  const [page, setPage] = useState(0);
  const pageSize = 12;
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
      updateParams({ authorIdEqual: userId, limit: pageSize });
      fetch();
    }
  }, [userId, updateParams, fetch]);

  const pageCount = Math.max(1, Math.ceil(cursorState.totalCount / pageSize));
  const visibleFeeds = feeds.slice(page * pageSize, (page + 1) * pageSize);

  useEffect(() => {
    if (page >= pageCount) setPage(Math.max(0, pageCount - 1));
  }, [page, pageCount]);

  const handlePageChange = async (nextPage: number) => {
    if (nextPage < 0 || nextPage >= pageCount || loading) return;
    const requiredItems = (nextPage + 1) * pageSize;
    while (useFeedStore.getState().data.length < requiredItems && useFeedStore.getState().cursorState.hasNext) {
      await fetchMore();
    }
    setPage(nextPage);
    document.querySelector('main')?.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <div>
      <div className="mb-5 flex items-center gap-2 px-1">
        <h2 className="text-[24px] font-extrabold tracking-[-0.6px] text-[#212126]">게시물</h2>
        <span className="rounded-full bg-[#eaf3ff] px-3 py-1 text-[18px] font-bold text-[#2588f5]">{cursorState.totalCount}</span>
      </div>
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
          <div className={`grid grid-cols-1 md:grid-cols-2 ${wide ? 'lg:grid-cols-4' : 'lg:grid-cols-3'} gap-6 w-full`}>
            {visibleFeeds.map((feed) => (
              <FeedCard 
                key={feed.id} 
                feed={feed} 
                onClick={() => handleFeedClick(feed)}
              />
            ))}

          </div>

          <Pagination
            page={page + 1}
            totalPages={pageCount}
            onPageChange={(nextPage) => handlePageChange(nextPage - 1)}
            disabled={loading}
            className="mt-8 pb-2"
          />
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
