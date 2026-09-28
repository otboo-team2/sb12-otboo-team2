import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useProfileStore } from '@/lib/stores/useProfileStore';
import { useFollowSummaryStore } from '@/lib/stores/useFollowSummaryStore';
import { useAuthStore } from '@/lib/stores/useAuthStore';
import profileIcon from '@/assets/icons/profile.svg';
import sendIcon from '@/assets/icons/ic_send.svg';
import FollowModal from './FollowModal';
import { createFollow, cancelFollow } from '@/lib/api/follows';
import { useFeedStore } from '@/lib/stores/useFeedStore';

interface ProfileSummaryProps {
  userId: string;
}

export default function ProfileSummary({
  userId,
}: ProfileSummaryProps) {
  const navigate = useNavigate();
  const { data: profile, loading: profileLoading, updateParams: updateProfileParams } = useProfileStore();
  const { data: followSummary, loading: followLoading, updateParams: updateFollowParams, fetch: fetchFollowSummary } = useFollowSummaryStore();
  const currentUser = useAuthStore((state) => state.data);
  const feedCount = useFeedStore((state) => state.data.length);
  const [followerModalOpen, setFollowerModalOpen] = useState(false);
  const [followingModalOpen, setFollowingModalOpen] = useState(false);

  const isOwnProfile = userId === currentUser?.userDto?.id;

  const handleMessageClick = () => {
    navigate(`/dm/${userId}`, {
      state: { partner: { userId, name: profile?.name || '사용자', profileImageUrl: profile?.profileImageUrl } },
    });
  };

  const handleFollowerClick = () => {
    setFollowerModalOpen(true);
  };

  const handleFollowingClick = () => {
    setFollowingModalOpen(true);
  };

  const handleFollowClick = async () => {
    if (!currentUser?.userDto || !profile) return;

    try {
      if (followSummary?.followedByMe && followSummary.followedByMeId) {
        // 언팔로우
        await cancelFollow(followSummary.followedByMeId);
      } else {
        // 팔로우
        await createFollow({ followeeId: profile.userId, followerId: currentUser.userDto.id });
      }

      // 팔로우 요약 정보 새로고침
      fetchFollowSummary();
    } catch (error) {
      console.error('Follow/Unfollow error:', error);
    }
  };

  useEffect(() => {
    updateProfileParams({ userId });
    updateFollowParams({ userId });
  }, [userId, isOwnProfile, updateProfileParams, updateFollowParams]);



  if (profileLoading && !isOwnProfile) {
    return (
      <div className="box-border content-stretch flex items-center justify-between pb-5 pt-0 px-5 relative w-full">
        {/* 프로필 스켈레톤 */}
        <div className="content-stretch flex gap-5 items-center justify-start relative shrink-0">
          <div className="bg-gray-200 relative rounded-[100px] shrink-0 size-[90px] animate-pulse" />
          <div className="content-stretch flex flex-col gap-2.5 items-start justify-start relative shrink-0">
            <div className="h-6 bg-gray-200 rounded w-24 animate-pulse" />
            <div className="content-stretch flex gap-5 items-center justify-start relative shrink-0">
              <div className="h-5 bg-gray-200 rounded w-20 animate-pulse" />
              <div className="h-5 bg-gray-200 rounded w-16 animate-pulse" />
            </div>
          </div>
        </div>
        {/* 버튼 스켈레톤 */}
        <div className="content-stretch flex gap-3 items-center justify-start relative shrink-0">
          <div className="h-[46px] bg-gray-200 rounded-[12px] w-20 animate-pulse" />
          <div className="h-[46px] bg-gray-200 rounded-[12px] w-32 animate-pulse" />
        </div>
      </div>
    );
  }

  return (
    <div className="box-border flex flex-col items-center pb-7 relative w-full">
      <div className="mt-5 flex flex-col items-center gap-4 text-center relative z-10">
        {/* 프로필 이미지 */}
        <div className="bg-[#a9a9b1] relative rounded-full shrink-0 size-[104px] overflow-hidden border-4 border-white shadow-md">
          {profile?.profileImageUrl ? (
            <img
              src={profile.profileImageUrl}
              alt={profile.name || '프로필'}
              className="w-full h-full object-cover rounded-[100px]"
            />
          ) : (
            <img
              src={profileIcon}
              alt={profile?.name || '프로필'}
              className="w-full h-full object-cover rounded-[100px]"
            />
          )}
          <div aria-hidden="true" className="absolute border-[#a9a9b1] border-[1.5px] border-solid inset-0 pointer-events-none rounded-[100px] shadow-[0px_2px_4px_0px_rgba(55,55,64,0.03)]" />
        </div>

        {/* 프로필 정보 */}
        <div className="flex flex-col items-center gap-2.5">
          <div className="font-['SUIT:Bold',_sans-serif] leading-[0] not-italic text-[#212126] text-[28px] tracking-[-0.7px]">
            <p className="leading-[normal]">{profile?.name || '사용자'}</p>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-3 w-[calc(100%-40px)] max-w-[600px] mt-6">
        <div className="rounded-2xl bg-[#f7f7f8] px-3 py-3 text-center"><strong className="block text-xl text-[#212126]">{feedCount}</strong><span className="text-sm text-[#808089]">게시물</span></div>
        <button onClick={handleFollowingClick} className="rounded-2xl bg-[#f7f7f8] px-3 py-3 text-center hover:bg-[#eeeeef]"><strong className="block text-xl text-[#212126]">{followLoading ? '-' : followSummary?.followingCount || 0}</strong><span className="text-sm text-[#808089]">팔로잉</span></button>
        <button onClick={handleFollowerClick} className="rounded-2xl bg-[#f7f7f8] px-3 py-3 text-center hover:bg-[#eeeeef]"><strong className="block text-xl text-[#212126]">{followLoading ? '-' : followSummary?.followerCount || 0}</strong><span className="text-sm text-[#808089]">팔로워</span></button>
      </div>

      {/* 액션 버튼들 - 본인 프로필이 아닌 경우에만 표시 */}
      {!isOwnProfile && (
        <div className="flex flex-wrap gap-3 items-center justify-center w-full mt-5 px-5">
          {/* 팔로우 버튼 */}
          <button
            onClick={handleFollowClick}
            className={`box-border flex gap-1.5 h-11 min-w-[150px] items-center justify-center px-5 py-2.5 rounded-xl shrink-0 transition-colors ${
              followSummary?.followedByMe
                ? 'bg-[#f7f7f8] hover:bg-[#eeeeef]'
                : 'bg-[#1e89f4] hover:bg-[#1a7ae6]'
            }`}
          >
            <div className={`font-['SUIT:Bold',_sans-serif] leading-[0] not-italic relative shrink-0 text-[18px] text-nowrap tracking-[-0.45px] ${
              followSummary?.followedByMe ? 'text-[#575765]' : 'text-white'
            }`}>
              <p className="leading-[normal] whitespace-pre">{followSummary?.followedByMe ? '팔로우 취소' : '팔로우'}</p>
            </div>
          </button>

          {/* 메시지 보내기 버튼 */}
          <button
            onClick={handleMessageClick}
            className="bg-[#f7f7f8] box-border flex gap-1.5 h-11 min-w-[150px] items-center justify-center px-5 py-2.5 rounded-xl shrink-0 hover:bg-[#eeeeef] transition-colors"
          >
            <div className="font-['SUIT:Bold',_sans-serif] leading-[0] not-italic relative shrink-0 text-[#575765] text-[18px] text-nowrap tracking-[-0.45px]">
              <p className="leading-[normal] whitespace-pre">메시지 보내기</p>
            </div>
            <div className="overflow-clip relative shrink-0 size-5">
              <img src={sendIcon} alt="메시지 보내기" className="block max-w-none size-full" />
            </div>
          </button>
        </div>
      )}

      {isOwnProfile && (
        <button
          type="button"
          onClick={() => navigate('/settings')}
          className="absolute left-5 top-4 h-10 rounded-xl bg-[#f7f7f8] px-5 text-[15px] font-bold text-[#575765] transition-colors hover:bg-[#eeeeef]"
        >
          프로필 수정
        </button>
      )}

      {/* 팔로워 모달 */}
      <FollowModal
        open={followerModalOpen}
        onOpenChange={setFollowerModalOpen}
        type="follower"
        targetUserId={userId}
      />

      {/* 팔로잉 모달 */}
      <FollowModal
        open={followingModalOpen}
        onOpenChange={setFollowingModalOpen}
        type="following"
        targetUserId={userId}
      />
    </div>
  );
}
