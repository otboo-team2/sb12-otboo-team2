import {useEffect, useState} from 'react';
import {useClothesStore} from '@/lib/stores/useClothesStore';
import {useAuthStore} from '@/lib/stores/useAuthStore';
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from '@/components/ui/alert-dialog';
import { deleteClothes } from '@/lib/api/clothes';
import { toast } from 'sonner';
import ClothesFilter from '@/components/closet/ClothesFilter';
import ClothesGrid from '@/components/closet/ClothesGrid';
import AddClothesModal from '@/components/closet/AddClothesModal';
import EditClothesModal from '@/components/closet/EditClothesModal';
import type { ClothesDto } from '@/lib/api/types';
import {Input} from '@/components/ui/input';
import searchIcon from '@/assets/icons/ic_search.svg';

export default function ClosetPage() {
  const { data: auth } = useAuthStore();
  const { updateParams, isEmpty, delete: remove, clear } = useClothesStore();
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [selectedClothes, setSelectedClothes] = useState<ClothesDto | null>(null);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [isDeleteAlertOpen, setIsDeleteAlertOpen] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [search, setSearch] = useState('');

  useEffect(() => {
    const userId = auth?.userDto.id;
    if (userId) {
      clear();
      updateParams({ ownerId: userId });
    }
  }, [auth?.userDto.id, updateParams, clear]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      updateParams({nameLike: search.trim() || undefined});
    }, 300);
    return () => window.clearTimeout(timer);
  }, [search, updateParams]);

  // 수정 핸들러
  const handleEditClothes = (clothes: ClothesDto) => {
    setSelectedClothes(clothes);
    setIsEditModalOpen(true);
  };

  // 삭제 핸들러
  const handleDeleteClothes = (clothes: ClothesDto) => {
    setSelectedClothes(clothes);
    setIsDeleteAlertOpen(true);
  };

  // 삭제 확인
  const confirmDeleteClothes = async () => {
    if (!selectedClothes) return;

    setDeleteLoading(true);
    try {
      await deleteClothes(selectedClothes.id);
      remove(selectedClothes.id);
      setIsDeleteAlertOpen(false);
      setSelectedClothes(null);
      toast.success('옷이 성공적으로 삭제되었습니다.');
    } catch (error) {
      console.error('옷 삭제 실패:', error);
      toast.error('옷 삭제에 실패했습니다.');
    } finally {
      setDeleteLoading(false);
    }
  };

  return (
    <div className="flex flex-col h-full px-10 py-2.5">
      {/* 카테고리 필터 */}
      {!isEmpty() && (
        <div className="flex-shrink-0 mb-6 flex items-center gap-5">
          <div className="relative w-[280px] shrink-0">
            <Input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="옷장 내 검색하기"
              icon={<img src={searchIcon} alt="검색" className="absolute left-[22px] top-1/2 size-5 -translate-y-1/2" />}
              className="h-[36px] w-full rounded-[100px] border border-[#d4d4d9] bg-white pl-[54px] pr-[22px] py-3.5 text-[16px] font-['SUIT:SemiBold',_sans-serif] text-[#575765] placeholder:text-[#a9a9b1] shadow-[0px_2px_4px_0px_rgba(55,55,64,0.03)] focus:border-[#d4d4d9] focus:ring-0"
            />
          </div>
          <div className="min-w-0 flex-1">
            <ClothesFilter onAddClick={() => setIsAddModalOpen(true)} />
          </div>
        </div>
      )}
      
      {/* 옷 그리드 - 항상 렌더링하여 fetch 호출 보장 */}
      <div className="flex-1 min-h-0">
        <ClothesGrid
          onAddClick={() => setIsAddModalOpen(true)}
          isOwner={true} // 항상 자신의 옷장을 보고 있으므로 true
          onEditClothes={handleEditClothes}
          onDeleteClothes={handleDeleteClothes}
        />
      </div>

      {/* 옷 추가 모달 */}
      <AddClothesModal
          open={isAddModalOpen}
          onClose={() => setIsAddModalOpen(false)}
      />

      {/* 옷 수정 모달 */}
      <EditClothesModal
        open={isEditModalOpen}
        onClose={() => {
          setIsEditModalOpen(false);
          setSelectedClothes(null);
        }}
        clothes={selectedClothes}
      />

      {/* 삭제 확인 다이얼로그 */}
      <AlertDialog open={isDeleteAlertOpen} onOpenChange={setIsDeleteAlertOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>옷을 삭제하시겠습니까?</AlertDialogTitle>
            <AlertDialogDescription>
              이 작업은 되돌릴 수 없습니다. "{selectedClothes?.name}" 정보가 영구적으로 삭제됩니다.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => setSelectedClothes(null)}>취소</AlertDialogCancel>
            <AlertDialogAction
              onClick={confirmDeleteClothes}
              disabled={deleteLoading}
            >
              {deleteLoading ? '삭제 중...' : '삭제'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
