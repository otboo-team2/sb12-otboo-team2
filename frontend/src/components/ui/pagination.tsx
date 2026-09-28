import { ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '@/lib/utils';

interface PaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  disabled?: boolean;
  className?: string;
}

type PageItem = number | 'ellipsis';

// 현재 페이지 기준 앞뒤 1개씩 + 처음/끝 페이지만 보여주고 나머지는 줄임표로 접는다.
function getPageItems(page: number, totalPages: number): PageItem[] {
  const delta = 1;
  const left = Math.max(2, page - delta);
  const right = Math.min(totalPages - 1, page + delta);

  const items: PageItem[] = [1];
  if (left > 2) items.push('ellipsis');
  for (let i = left; i <= right; i++) items.push(i);
  if (right < totalPages - 1) items.push('ellipsis');
  if (totalPages > 1) items.push(totalPages);

  return items;
}

export default function Pagination({ page, totalPages, onPageChange, disabled, className = '' }: PaginationProps) {
  if (totalPages <= 1) return null;

  const items = getPageItems(page, totalPages);

  return (
    <nav aria-label="페이지네이션" className={cn('flex items-center justify-center gap-1.5', className)}>
      <button
        type="button"
        onClick={() => onPageChange(page - 1)}
        disabled={disabled || page <= 1}
        aria-label="이전 페이지"
        className="flex size-9 items-center justify-center rounded-[10px] border border-[var(--color-gray-300)] text-[var(--color-gray-600)] transition-colors hover:bg-[var(--color-gray-50)] disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent"
      >
        <ChevronLeft className="size-4" aria-hidden="true" />
      </button>

      {items.map((item, index) =>
        item === 'ellipsis' ? (
          <span key={`ellipsis-${index}`} className="flex size-9 items-center justify-center text-[var(--color-gray-400)]">
            …
          </span>
        ) : (
          <button
            key={item}
            type="button"
            onClick={() => onPageChange(item)}
            disabled={disabled}
            aria-current={item === page ? 'page' : undefined}
            className={cn(
              'flex size-9 items-center justify-center rounded-[10px] text-[14px] font-semibold transition-colors disabled:cursor-not-allowed',
              item === page
                ? 'bg-[var(--color-blue-500)] text-white'
                : 'text-[var(--color-gray-600)] hover:bg-[var(--color-gray-50)] disabled:opacity-40'
            )}
          >
            {item}
          </button>
        )
      )}

      <button
        type="button"
        onClick={() => onPageChange(page + 1)}
        disabled={disabled || page >= totalPages}
        aria-label="다음 페이지"
        className="flex size-9 items-center justify-center rounded-[10px] border border-[var(--color-gray-300)] text-[var(--color-gray-600)] transition-colors hover:bg-[var(--color-gray-50)] disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-transparent"
      >
        <ChevronRight className="size-4" aria-hidden="true" />
      </button>
    </nav>
  );
}
