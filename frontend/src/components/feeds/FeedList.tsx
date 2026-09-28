import {useState} from 'react';
import {useFeedStore} from '@/lib/stores/useFeedStore';
import FeedCard from './FeedCard';
import FeedCardSkeleton from './FeedCardSkeleton';
import FeedEmptyState from './FeedEmptyState';
import FeedDetailModal from './FeedDetailModal';
import Pagination from '@/components/ui/pagination';
import type {FeedDto} from '@/lib/api/types';


export default function FeedList() {
  const { data: feeds, loading, page, totalPages, goToPage } = useFeedStore();
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

  return (
    <div className="h-full overflow-y-auto">
      {loading && feeds.length === 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6 p-4">
          {Array.from({ length: 8 }).map((_, index) => (
            <FeedCardSkeleton key={`skeleton-${index}`} />
          ))}
        </div>
      ) : feeds.length === 0 ? (
        <FeedEmptyState />
      ) : (
        <div className="flex flex-col gap-8 p-4">
          <div className={`grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6 w-full transition-opacity ${loading ? 'opacity-50' : ''}`}>
            {feeds.map((feed) => (
              <FeedCard
                key={feed.id}
                feed={feed}
                onClick={() => handleFeedClick(feed)}
              />
            ))}
          </div>

          <Pagination
            page={page}
            totalPages={totalPages()}
            onPageChange={goToPage}
            disabled={loading}
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