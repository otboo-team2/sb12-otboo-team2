import FeedFilters from '@/components/feeds/FeedFilters';
import FeedList from '@/components/feeds/FeedList';
import AddFeedModal from '@/components/feeds/AddFeedModal';
import FeedDetailModal from '@/components/feeds/FeedDetailModal';
import {useSearchParams} from "react-router-dom";
import {useEffect, useState} from "react";
import {useFeedStore} from "@/lib/stores/useFeedStore.ts";
import type {FeedDto} from "@/lib/api";

export default function FeedsPage() {
  const [searchParams] = useSearchParams();
  const authorIdEqual = searchParams.get('authorIdEqual');
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [createdFeed, setCreatedFeed] = useState<FeedDto | undefined>();

  const {updateParams} = useFeedStore();

  useEffect(() => {
    if (authorIdEqual) {
      updateParams({authorIdEqual})
    } else {
      updateParams({authorIdEqual: undefined})
    }
  }, [authorIdEqual, updateParams]);

  return (
    <div className="flex flex-col h-full px-8 py-6">
      {/* 필터 영역 */}
      <div className="flex-shrink-0 mb-6 flex items-center gap-3.5">
        <div className="flex-1 min-w-0">
          <FeedFilters />
        </div>

        {/* 피드 등록 버튼 */}
        <button
          className="bg-[#1e89f4] box-border content-stretch flex gap-1.5 h-[46px] items-center justify-center px-[18px] py-2.5 relative rounded-[12px] shrink-0 hover:bg-[#1e89f4]/90 transition-colors"
          onClick={() => setIsAddModalOpen(true)}
        >
          <div className="font-bold leading-none not-italic relative shrink-0 text-white text-[18px] text-nowrap tracking-[-0.45px]">
            <p className="leading-normal whitespace-pre">피드 등록</p>
          </div>
        </button>
      </div>

      {/* 피드 목록 */}
      <div className="flex-1 min-h-0">
        <FeedList />
      </div>

      {/* 피드 등록 모달 */}
      <AddFeedModal
        open={isAddModalOpen}
        onClose={() => setIsAddModalOpen(false)}
        onCreated={setCreatedFeed}
      />
      {/* 등록한 피드 상세 모달 */}
      {
        createdFeed &&
          <FeedDetailModal
              feed={createdFeed}
              open={true}
              onOpenChange={() => setCreatedFeed(undefined)}
          />
      }
    </div>
  );
}
