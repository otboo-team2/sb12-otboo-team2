import { useEffect, useState } from 'react';
import { useLocation, useSearchParams } from 'react-router-dom';
import { useAuthStore } from '@/lib/stores/useAuthStore';
import ProfileSummary from '@/components/profile/ProfileSummary';
import UserFeedList from '@/components/profile/UserFeedList';
import UserCloset from '@/components/profile/UserCloset';
import ClosetToggleButton from '@/components/profile/ClosetToggleButton';

export default function ProfilePage() {
  const [searchParams] = useSearchParams();
  const location = useLocation();
  const userId = searchParams.get('userId');
  const currentUser = useAuthStore((state) => state.data);
  const [isClosetOpen, setIsClosetOpen] = useState(false);

  useEffect(() => {
    if (location.hash === '#posts') {
      requestAnimationFrame(() => document.getElementById('profile-posts')?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
    }
  }, [location.hash]);
  
  // 실제로 표시할 사용자 ID (본인 프로필이면 현재 사용자 ID 사용)
  const targetUserId = userId || currentUser?.userDto?.id;
  
  const toggleCloset = () => {
    setIsClosetOpen(!isClosetOpen);
  };
  
  if (!targetUserId) {
    return (
      <div className="flex items-center justify-center h-96">
        <p className="text-gray-500">로그인이 필요합니다.</p>
      </div>
    );
  }
  
  return (
    <div className="relative flex min-h-full bg-white">
      {/* 메인 콘텐츠 영역 - 고정 너비와 중앙 정렬 */}
      <div className="flex-1 flex justify-center">
        <div 
          className={`w-full max-w-[1600px] transition-all duration-300 ease-out ${
            isClosetOpen ? 'mr-[228px]' : 'mr-0'
          }`}
        >
          <div className="flex flex-col px-8 py-6 gap-6">
            {/* ProfileSummary 컴포넌트 - 고정 */}
            <div className="overflow-hidden rounded-lg border border-[#f0f4fa] bg-[#fbfcff] flex-shrink-0">
              <ProfileSummary userId={targetUserId} />
            </div>
            
            {/* 사용자 피드 목록 - 스크롤 가능 */}
            <div id="profile-posts">
              <div className="p-6">
                <UserFeedList userId={targetUserId} wide />
              </div>
            </div>
          </div>
        </div>
      </div>
      
      {/* 토글 버튼 - 항상 고정 위치에 표시 */}
      <div 
        className={`fixed top-1/2 transform -translate-y-1/2 z-40 transition-all duration-300 ease-out ${
          isClosetOpen ? 'right-[456px]' : 'right-0'
        }`}
      >
        <ClosetToggleButton isOpen={isClosetOpen} onToggle={toggleCloset} />
      </div>
      
      {/* 옷장 슬라이드 패널 - 토글 버튼과 분리 */}
      <div 
        className={`fixed right-0 top-0 h-full transition-transform duration-300 ease-out z-30 ${
          isClosetOpen ? 'translate-x-0' : 'translate-x-[456px]'
        }`}
      >
        <UserCloset userId={targetUserId} />
      </div>
    </div>
  );
}
